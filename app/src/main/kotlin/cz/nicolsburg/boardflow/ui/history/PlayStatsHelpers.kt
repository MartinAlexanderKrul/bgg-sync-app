package cz.nicolsburg.boardflow.ui.history

import cz.nicolsburg.boardflow.data.SecurePreferences
import cz.nicolsburg.boardflow.model.InsightRarity
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.RecordMoment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

enum class StatsTimeRange(val label: String, val subtitle: String = label) {
    ALL("All time"),
    THIS_YEAR("This year"),
    THIS_MONTH("This month"),
    LAST_30("Last 30 d", "Last 30 days")
}

fun StatsTimeRange.displayLabel(now: LocalDate = LocalDate.now()): String = when (this) {
    StatsTimeRange.ALL -> label
    StatsTimeRange.THIS_YEAR -> "${now.year} So Far"
    StatsTimeRange.THIS_MONTH -> "${now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}'s Table"
    StatsTimeRange.LAST_30 -> "Recent Run"
}

fun StatsTimeRange.displaySubtitle(now: LocalDate = LocalDate.now()): String = when (this) {
    StatsTimeRange.ALL -> subtitle
    StatsTimeRange.THIS_YEAR -> "${now.year} so far"
    StatsTimeRange.THIS_MONTH -> "${now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}'s table"
    StatsTimeRange.LAST_30 -> "Recent run"
}

fun List<LoggedPlay>.filterByTimeRange(range: StatsTimeRange): List<LoggedPlay> {
    if (range == StatsTimeRange.ALL) return this
    val now = LocalDate.now()
    val cutoff = when (range) {
        StatsTimeRange.THIS_YEAR -> LocalDate.of(now.year, 1, 1)
        StatsTimeRange.THIS_MONTH -> LocalDate.of(now.year, now.monthValue, 1)
        StatsTimeRange.LAST_30 -> now.minusDays(30)
        StatsTimeRange.ALL -> return this
    }
    return filter { play ->
        runCatching { LocalDate.parse(play.date) >= cutoff }.getOrDefault(false)
    }
}

data class GameHistoryStats(
    val plays: Int,
    val lastPlayedDate: String?,
    val avgDurationMinutes: Int?,
    val bestScore: Pair<String, Int>?,
    val mostWins: Pair<String, Int>?,
    val commonPlayers: List<Pair<String, Int>>
)

enum class InsightType(val priority: Int) {
    Milestone(1),
    RecentActivity(2),
    Rivalry(3),
    Session(4),
    Dormant(5)
}

data class ContextualInsight(
    val key: String,
    val text: String,
    val type: InsightType,
    val rarity: InsightRarity = InsightRarity.COMMON
)

private data class InsightCandidate(
    val key: String,
    val text: String,
    val type: InsightType,
    val rarity: InsightRarity = InsightRarity.COMMON
)

private fun resolveDisplayName(raw: String, roster: List<Player>): String {
    val lower = raw.lowercase().trim()
    return roster.firstOrNull { player ->
        player.displayName.lowercase().trim() == lower ||
            player.aliases.any { alias -> alias.lowercase().trim() == lower } ||
            player.bggUsername.lowercase().trim().let { u -> u.isNotBlank() && u == lower }
    }?.displayName ?: raw
}

private fun computeGameHistoryStats(gamePlays: List<LoggedPlay>, roster: List<Player>): GameHistoryStats? {
    if (gamePlays.isEmpty()) return null

    val plays = gamePlays.sumOf { it.quantity.coerceAtLeast(1) }
    val lastDate = gamePlays.maxOfOrNull { it.date }?.let {
        runCatching {
            LocalDate.parse(it).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }.getOrDefault(it)
    }

    val withDuration = gamePlays.filter { it.durationMinutes > 0 }
    val avgDuration = if (withDuration.isEmpty()) null else withDuration.sumOf { it.durationMinutes } / withDuration.size

    var bestScore: Pair<String, Int>? = null
    gamePlays.forEach { play ->
        play.players.forEach { player ->
            val score = player.score.trim().toIntOrNull()
            if (score != null && player.name.isNotBlank()) {
                val displayName = resolveDisplayName(player.name, roster)
                if (bestScore == null || score > bestScore!!.second) {
                    bestScore = displayName to score
                }
            }
        }
    }

    val winCounts = mutableMapOf<String, Int>()
    gamePlays.forEach { play ->
        play.players.filter { it.isWinner && it.name.isNotBlank() }.forEach { player ->
            val name = resolveDisplayName(player.name, roster)
            winCounts[name] = (winCounts[name] ?: 0) + 1
        }
    }
    val mostWins = winCounts.maxByOrNull { it.value }?.let { it.key to it.value }

    val playerCounts = mutableMapOf<String, Int>()
    gamePlays.forEach { play ->
        play.players.filter { it.name.isNotBlank() }.forEach { player ->
            val name = resolveDisplayName(player.name, roster)
            playerCounts[name] = (playerCounts[name] ?: 0) + 1
        }
    }
    val commonPlayers = playerCounts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(3)
        .map { it.key to it.value }

    return GameHistoryStats(plays, lastDate, avgDuration, bestScore, mostWins, commonPlayers)
}

fun List<LoggedPlay>.gameHistoryStats(gameName: String, roster: List<Player> = emptyList()): GameHistoryStats? {
    val gamePlays = filter { it.gameName.trim().equals(gameName.trim(), ignoreCase = true) }
    return computeGameHistoryStats(gamePlays, roster)
}

fun List<LoggedPlay>.gameHistoryStats(gameId: Int, roster: List<Player> = emptyList()): GameHistoryStats? {
    val gamePlays = filter { it.gameId == gameId }
    return computeGameHistoryStats(gamePlays, roster)
}

fun List<LoggedPlay>.gameHealthSignal(gameId: Int): Pair<String, Boolean>? {
    val gamePlays = filter { it.gameId == gameId }
    if (gamePlays.isEmpty()) return "No plays logged yet." to false

    val totalPlays = gamePlays.sumOf { it.quantity.coerceAtLeast(1) }
    if (totalPlays == 1) return "Just the one session so far." to false

    val now = LocalDate.now()
    val cutoff30 = now.minusDays(30)
    val recentCount = gamePlays.count {
        runCatching { LocalDate.parse(it.date) >= cutoff30 }.getOrDefault(false)
    }
    if (recentCount >= 2) return "$recentCount plays in the last 30 days." to true

    val lastDate = gamePlays
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        .maxOrNull() ?: return null

    val days = now.toEpochDay() - lastDate.toEpochDay()
    return when {
        days <= 0 -> "On the table today." to true
        days == 1L -> "Last session: yesterday." to true
        days <= 14 -> "Last played $days days ago." to true
        days <= 60 -> "Not played in $days days." to false
        else -> "Not played in ${days / 30} month${if (days / 30 > 1) "s" else ""}." to false
    }
}

fun List<LoggedPlay>.gameInsight(gameId: Int, roster: List<Player> = emptyList()): String? {
    val gamePlays = filter { it.gameId == gameId }
    if (gamePlays.size < 3) return null

    val winCounts = mutableMapOf<String, Int>()
    gamePlays.forEach { play ->
        play.players.filter { it.isWinner && it.name.isNotBlank() }.forEach { player ->
            val name = resolveDisplayName(player.name, roster)
            winCounts[name] = (winCounts[name] ?: 0) + 1
        }
    }
    val topWinner = winCounts.maxByOrNull { it.value } ?: return null
    return if (topWinner.value >= 2) "${topWinner.key} leads — ${topWinner.value} wins." else null
}

private fun buildInsightCandidates(
    gamePlays: List<LoggedPlay>,
    roster: List<Player>,
    currentPlayerName: String?
): List<InsightCandidate> {
    if (gamePlays.isEmpty()) return emptyList()

    val candidates = mutableListOf<InsightCandidate>()
    val now = LocalDate.now()
    val totalPlays = gamePlays.sumOf { it.quantity.coerceAtLeast(1) }
    val lastDate = gamePlays.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()
    val daysSinceLast = lastDate?.let { now.toEpochDay() - it.toEpochDay() }

    when (totalPlays) {
        1   -> candidates += InsightCandidate("milestone_first_play", "First session logged.",                          InsightType.Milestone, InsightRarity.COMMON)
        5   -> candidates += InsightCandidate("milestone_5",          "Five plays in.",                                InsightType.Milestone, InsightRarity.COMMON)
        10  -> candidates += InsightCandidate("milestone_10",         "Ten plays. Starting to feel familiar.",         InsightType.Milestone, InsightRarity.NOTABLE)
        25  -> candidates += InsightCandidate("milestone_25",         "25 plays. This one has history.",               InsightType.Milestone, InsightRarity.RARE)
        50  -> candidates += InsightCandidate("milestone_50",         "50 plays. That's a relationship.",              InsightType.Milestone, InsightRarity.EPIC)
        100 -> candidates += InsightCandidate("milestone_100",        "A hundred plays. Some games just stick.",       InsightType.Milestone, InsightRarity.LEGENDARY)
        200 -> candidates += InsightCandidate("milestone_200",        "200 plays. This one is part of the furniture.", InsightType.Milestone, InsightRarity.LEGENDARY)
    }

    // Approaching milestone — only fires when no exact milestone was just logged
    if (candidates.none { it.type == InsightType.Milestone }) {
        val milestoneTargets = listOf(10, 25, 50, 100, 200)
        val next = milestoneTargets.firstOrNull { it > totalPlays }
        if (next != null) {
            val gap = next - totalPlays
            if (gap <= 2) {
                val text = when (gap) {
                    1    -> "One away from $next. Something's coming."
                    else -> "Two plays from $next. Getting close."
                }
                candidates += InsightCandidate("approaching_$next", text, InsightType.Milestone, InsightRarity.NOTABLE)
            }
        }
    }

    val last7 = now.minusDays(7)
    val last30 = now.minusDays(30)
    val recent7 = gamePlays.sumOf { play ->
        if (runCatching { LocalDate.parse(play.date) >= last7 }.getOrDefault(false)) play.quantity.coerceAtLeast(1) else 0
    }
    val recent30 = gamePlays.sumOf { play ->
        if (runCatching { LocalDate.parse(play.date) >= last30 }.getOrDefault(false)) play.quantity.coerceAtLeast(1) else 0
    }
    when {
        daysSinceLast == 0L ->
            candidates += InsightCandidate("recent_today", "On the table today.", InsightType.RecentActivity)
        recent7 >= 2 ->
            candidates += InsightCandidate("recent_week_$recent7", "$recent7 plays this week. A good stretch.", InsightType.RecentActivity)
        recent30 >= 2 ->
            candidates += InsightCandidate("recent_30_$recent30", "$recent30 plays in the recent run.", InsightType.RecentActivity)
        daysSinceLast == 1L ->
            candidates += InsightCandidate("recent_yesterday", "Last session: yesterday.", InsightType.RecentActivity)
        daysSinceLast != null && daysSinceLast in 2..14 ->
            candidates += InsightCandidate("recent_${daysSinceLast}d", "Last session: $daysSinceLast days ago.", InsightType.RecentActivity)
    }

    if (totalPlays >= 4) {
        // Head-to-head: find the most-played pair in this game's history
        // pairKey = Pair(nameA, nameB) where nameA <= nameB (alphabetical), wins = Pair(aWins, bWins)
        val pairPlays = mutableMapOf<Pair<String, String>, Int>()
        val pairWins  = mutableMapOf<Pair<String, String>, Pair<Int, Int>>()

        gamePlays.forEach { play ->
            val qty = play.quantity.coerceAtLeast(1)
            val names = play.players
                .filter { it.name.isNotBlank() }
                .map { resolveDisplayName(it.name, roster) }
                .distinct()
            val winners = play.players
                .filter { it.isWinner && it.name.isNotBlank() }
                .map { resolveDisplayName(it.name, roster) }
                .toSet()
            for (i in names.indices) {
                for (j in i + 1 until names.size) {
                    val (na, nb) = if (names[i] <= names[j]) names[i] to names[j] else names[j] to names[i]
                    val key = na to nb
                    pairPlays[key] = (pairPlays[key] ?: 0) + qty
                    val prevWins = pairWins[key] ?: (0 to 0)
                    val aWon = if (na in winners) qty else 0
                    val bWon = if (nb in winners) qty else 0
                    pairWins[key] = (prevWins.first + aWon) to (prevWins.second + bWon)
                }
            }
        }

        val bestPair = pairPlays.entries.filter { it.value >= 4 }.maxByOrNull { it.value }
        if (bestPair != null) {
            val (na, nb) = bestPair.key
            val together = bestPair.value
            val (aWins, bWins) = pairWins[bestPair.key] ?: (0 to 0)
            val currentNorm = currentPlayerName?.lowercase()?.trim()
            val aIsMe = currentNorm != null && na.lowercase().trim() == currentNorm
            val bIsMe = currentNorm != null && nb.lowercase().trim() == currentNorm
            val rarity = if (together >= 10) InsightRarity.RARE else InsightRarity.NOTABLE
            val text = when {
                aWins == bWins && (aIsMe || bIsMe) -> {
                    val other = if (aIsMe) nb else na
                    "Deadlocked with $other. $aWins each across $together sessions."
                }
                aWins == bWins ->
                    "Deadlocked. $aWins each after $together sessions."
                aIsMe ->
                    if (aWins > bWins) "You lead $nb $aWins–$bWins across $together sessions."
                    else "$nb leads you $bWins–$aWins across $together sessions."
                bIsMe ->
                    if (bWins > aWins) "You lead $na $bWins–$aWins across $together sessions."
                    else "$na leads you $aWins–$bWins across $together sessions."
                aWins > bWins ->
                    "$na leads $nb $aWins–$bWins across $together sessions."
                else ->
                    "$nb leads $na $bWins–$aWins across $together sessions."
            }
            candidates += InsightCandidate("rivalry_h2h_${na}_$nb", text, InsightType.Rivalry, rarity)
        } else {
            // Fall back to simple win-count leader when no qualifying pair
            val winCounts = mutableMapOf<String, Int>()
            gamePlays.forEach { play ->
                play.players.filter { it.isWinner && it.name.isNotBlank() }.forEach { player ->
                    val name = resolveDisplayName(player.name, roster)
                    winCounts[name] = (winCounts[name] ?: 0) + 1
                }
            }
            val sorted = winCounts.entries.sortedByDescending { it.value }
            val top = sorted.firstOrNull()
            val second = sorted.getOrNull(1)
            val totalWins = sorted.sumOf { it.value }
            val hasClearLeader = top != null &&
                top.value >= 2 &&
                (second == null || top.value > second.value) &&
                (totalWins == 0 || top.value * 2 >= totalWins + 1)
            if (hasClearLeader) {
                val isCurrentPlayerLeader = currentPlayerName != null &&
                    top.key.lowercase().trim() == currentPlayerName.lowercase().trim()
                val text = if (isCurrentPlayerLeader) {
                    "You have been dominant lately. ${top.value} wins."
                } else {
                    "${top.key} has been dominant lately. ${top.value} wins."
                }
                candidates += InsightCandidate("rivalry_${top.key}_${top.value}", text, InsightType.Rivalry, InsightRarity.NOTABLE)
            }
        }
    }

    val withDuration = gamePlays.filter { it.durationMinutes > 0 }
    if (withDuration.isNotEmpty()) {
        fun formatDuration(minutes: Int) = when {
            minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes}m"
        }

        val longestDuration = withDuration.maxOf { it.durationMinutes }
        if (withDuration.size >= 3) {
            val averageDuration = withDuration.sumOf { it.durationMinutes } / withDuration.size
            candidates += InsightCandidate(
                key = "session_avg_$averageDuration",
                text = "Sessions run about ${formatDuration(averageDuration)} on average.",
                type = InsightType.Session
            )
        }
        candidates += InsightCandidate(
            key = "session_longest_$longestDuration",
            text = "Longest session: ${formatDuration(longestDuration)}.",
            type = InsightType.Session
        )
    }

    if (candidates.none { it.type == InsightType.RecentActivity }) {
        when {
            totalPlays == 1 ->
                candidates += InsightCandidate("dormant_once", "Only played once so far.", InsightType.Dormant)
            daysSinceLast != null && daysSinceLast > 14 ->
                candidates += InsightCandidate("dormant_$daysSinceLast", "Hasn't hit the table in ${formatDormantAge(daysSinceLast)}. $totalPlays plays in the books.", InsightType.Dormant)
        }
    }

    return candidates.sortedWith(
        compareBy<InsightCandidate> { it.type.priority }
            .thenBy { it.key }
    )
}

/**
 * Resolves the app user's display name for use as [currentPlayerName] in insight
 * calculations.
 *
 * Resolution order:
 * 1. Roster player whose stored [Player.bggUsername] matches [bggUsername] (case-insensitive)
 *    → returns their [Player.displayName] so it aligns with what [resolveDisplayName] produces.
 * 2. Roster player whose display name or alias matches [bggUsername] directly
 *    → handles setups where the BGG username and roster name are the same string.
 * 3. The raw [bggUsername] itself — BGG-synced plays store the logged-in user by their
 *    BGG username, so comparing against the raw value still catches those plays.
 *
 * Returns null only when [bggUsername] is blank.
 */
fun resolveCurrentPlayerName(bggUsername: String, roster: List<Player>): String? {
    val trimmed = bggUsername.trim()
    if (trimmed.isBlank()) return null
    val norm = trimmed.lowercase()
    // 1. Match roster player by their stored bggUsername field
    roster.firstOrNull { it.bggUsername.trim().lowercase() == norm }?.displayName?.let { return it }
    // 2. Match roster player whose display name or alias equals the bggUsername
    roster.firstOrNull { player ->
        player.displayName.lowercase().trim() == norm ||
            player.aliases.any { it.lowercase().trim() == norm }
    }?.displayName?.let { return it }
    // 3. Fall back to the raw bggUsername — BGG play sync stores users by their username
    return trimmed
}

fun List<LoggedPlay>.gameContextualInsight(
    gameId: Int,
    roster: List<Player> = emptyList(),
    currentPlayerName: String? = null,
    prefs: SecurePreferences? = null
): ContextualInsight? {
    val candidates = buildInsightCandidates(
        gamePlays = filter { it.gameId == gameId },
        roster = roster,
        currentPlayerName = currentPlayerName
    )
    if (candidates.isEmpty()) return null

    val topCandidate = candidates.first()
    val lastShownKey = prefs?.getLastGameInsightKey(gameId)
    val neighboringCandidate = candidates.firstOrNull { candidate ->
        candidate.key != topCandidate.key && candidate.type.priority <= topCandidate.type.priority + 1
    }
    val chosen = if (topCandidate.key == lastShownKey && neighboringCandidate != null) {
        neighboringCandidate
    } else {
        topCandidate
    }

    prefs?.setLastGameInsightKey(gameId, chosen.key)
    return ContextualInsight(
        key    = chosen.key,
        text   = chosen.text,
        type   = chosen.type,
        rarity = chosen.rarity
    )
}

data class RivalryStat(
    val opponentName: String,
    val playsTogetherCount: Int,
    val myWins: Int,
    val theirWins: Int
)

fun List<LoggedPlay>.rivalriesForPlayer(player: Player, limit: Int = 4): List<RivalryStat> {
    val playerNames = (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
    val myPlays = filter { play -> play.players.any { it.name.lowercase().trim() in playerNames } }

    val opponentMap = mutableMapOf<String, Triple<Int, Int, Int>>()
    myPlays.forEach { play ->
        val iWon = play.players.any { it.name.lowercase().trim() in playerNames && it.isWinner }
        play.players
            .filter { playerResult -> playerResult.name.isNotBlank() && playerResult.name.lowercase().trim() !in playerNames }
            .forEach { opponent ->
                val key = opponent.name.trim()
                val (together, myWins, theirWins) = opponentMap[key] ?: Triple(0, 0, 0)
                opponentMap[key] = Triple(
                    together + 1,
                    myWins + if (iWon) 1 else 0,
                    theirWins + if (opponent.isWinner) 1 else 0
                )
            }
    }

    return opponentMap.entries
        .sortedByDescending { it.value.first }
        .take(limit)
        .map { (name, stats) -> RivalryStat(name, stats.first, stats.second, stats.third) }
}

fun List<LoggedPlay>.playerCurrentWinStreak(playerNames: List<String>): Int {
    val lowerNames = playerNames.map { it.lowercase().trim() }
    val myPlays = filter { play ->
        play.players.any { it.name.lowercase().trim() in lowerNames }
    }.sortedByDescending { it.date }
    var streak = 0
    for (play in myPlays) {
        val won = play.players.any { it.name.lowercase().trim() in lowerNames && it.isWinner }
        if (won) streak++ else break
    }
    return streak
}

fun List<LoggedPlay>.hotPlayerStreak(roster: List<Player>): Pair<String, Int>? =
    roster.map { player ->
        val names = (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
        player.displayName to playerCurrentWinStreak(names)
    }.filter { it.second >= 2 }.maxByOrNull { it.second }

fun List<LoggedPlay>.mostPlayedThisMonth(): Pair<String, Int>? {
    val now = LocalDate.now()
    val thisMonth = now.year * 100 + now.monthValue
    return filter { play ->
        runCatching {
            LocalDate.parse(play.date).let { it.year * 100 + it.monthValue } == thisMonth
        }.getOrDefault(false)
    }.groupBy { it.gameName }
        .mapValues { (_, plays) -> plays.sumOf { it.quantity.coerceAtLeast(1) } }
        .maxByOrNull { it.value }
        ?.let { it.key to it.value }
}

data class PlayInsight(val text: String, val rarity: InsightRarity = InsightRarity.COMMON)

// playInsights detects the same conditions as AppViewModel.detectRecord() / RecordMoment,
// but operates on already-saved history and uses RecordMoment.stripText (no emoji) for
// the strip surface. AppViewModel works on the pre-save snapshot for the immediate
// post-log celebration; this works on full history for retrospective browsing.
fun List<LoggedPlay>.playInsights(play: LoggedPlay): List<PlayInsight> {
    val gamePlays = filter { it.gameName.trim().equals(play.gameName.trim(), ignoreCase = true) }
    val result    = mutableListOf<PlayInsight>()

    if (gamePlays.size == 1 && gamePlays[0].id == play.id) {
        result.add(PlayInsight("First time playing ${play.gameName}.", InsightRarity.NOTABLE))
        return result
    }

    val prior = gamePlays.filter { it.id != play.id }

    // FirstWin — same condition as RecordMoment.FirstWin; text from stripText
    play.players.filter { it.isWinner && it.name.isNotBlank() }.forEach { winner ->
        if (result.size >= 2) return result
        val name = winner.name.trim()
        val hadPreviousWin = prior.any { p ->
            p.players.any { it.name.trim().equals(name, ignoreCase = true) && it.isWinner }
        }
        if (!hadPreviousWin) {
            result.add(PlayInsight(RecordMoment.FirstWin(name, play.gameName).stripText, InsightRarity.NOTABLE))
        }
    }

    // NewHighScore — same condition as RecordMoment.NewHighScore; score value appended
    if (result.size < 2) {
        play.players.filter { it.name.isNotBlank() }.forEach { player ->
            if (result.size >= 2) return result
            val name  = player.name.trim()
            val score = player.score.trim().toIntOrNull() ?: return@forEach
            val previousBest = prior.flatMap { it.players }
                .filter { it.name.trim().equals(name, ignoreCase = true) }
                .mapNotNull { it.score.trim().toIntOrNull() }
                .maxOrNull()
            if (previousBest == null || score > previousBest) {
                // Strip text + score value: "New personal best for Martin — 142."
                val base = RecordMoment.NewHighScore(name, play.gameName).stripText.dropLast(1)
                result.add(PlayInsight("$base — $score.", InsightRarity.NOTABLE))
            }
        }
    }

    // Longest session — no RecordMoment equivalent; strip-only insight
    if (result.size < 2 && play.durationMinutes > 0) {
        val previousMax = prior.filter { it.durationMinutes > 0 }.maxOfOrNull { it.durationMinutes }
        if (previousMax == null || play.durationMinutes > previousMax) {
            val label = if (play.durationMinutes >= 60)
                "${play.durationMinutes / 60}h ${play.durationMinutes % 60}m"
            else "${play.durationMinutes}m"
            result.add(PlayInsight("$label at the table. This one had room to breathe.", InsightRarity.COMMON))
        }
    }

    return result
}

// ── Shared duration formatter ─────────────────────────────────────────────────

fun formatDuration(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}

private fun formatDormantAge(days: Long): String = when {
    days < 30 -> "$days days"
    days < 365 -> {
        val months = (days / 30).coerceAtLeast(1)
        "$months month${if (months == 1L) "" else "s"}"
    }
    else -> {
        val years = (days / 365).coerceAtLeast(1)
        "$years year${if (years == 1L) "" else "s"}"
    }
}

// ── Range-specific observations ───────────────────────────────────────────────

fun List<LoggedPlay>.buildRangeObservations(
    range: StatsTimeRange,
    allPlays: List<LoggedPlay>
): List<SmartObservation> {
    if (isEmpty()) return emptyList()
    val now = LocalDate.now()
    val totalPlays = sumOf { it.quantity.coerceAtLeast(1) }
    val uniqueGames = map { it.gameName }.toSet().size
    val withDuration = filter { it.durationMinutes > 0 }
    val avgSession = if (withDuration.isNotEmpty()) withDuration.sumOf { it.durationMinutes } / withDuration.size else 0
    val result = mutableListOf<SmartObservation>()

    when (range) {
        StatsTimeRange.THIS_YEAR -> {
            val monthsElapsed = now.monthValue.coerceAtLeast(1)
            val projected = totalPlays * 12 / monthsElapsed

            // Pace projection
            if (monthsElapsed >= 2) {
                result += SmartObservation(
                    text = "$totalPlays plays in ${now.year} so far. On pace for $projected.",
                    rarity = if (projected >= 100) InsightRarity.EPIC else InsightRarity.COMMON,
                    subtext = "${now.year} so far"
                )
            }

            // vs last year
            val lastYearCount = allPlays.filter {
                runCatching { LocalDate.parse(it.date).year == now.year - 1 }.getOrDefault(false)
            }.sumOf { it.quantity.coerceAtLeast(1) }
            if (lastYearCount > 0 && monthsElapsed >= 3) {
                val paceVsLastYear = projected - lastYearCount
                when {
                    paceVsLastYear > 5 -> result += SmartObservation(
                        text = "$paceVsLastYear plays ahead of last year's pace. A stronger season.",
                        rarity = InsightRarity.NOTABLE,
                        subtext = "Compared with ${now.year - 1}"
                    )
                    paceVsLastYear < -5 -> result += SmartObservation(
                        text = "${-paceVsLastYear} plays behind last year's pace. A quieter season.",
                        rarity = InsightRarity.COMMON,
                        subtext = "Compared with ${now.year - 1}"
                    )
                    else -> result += SmartObservation(
                        "Almost exactly on last year's pace. The table has a rhythm."
                    )
                }
            }

            // Most active month this year
            val byMonth = groupBy { runCatching { LocalDate.parse(it.date).monthValue }.getOrNull() }
                .filterKeys { it != null }
                .mapValues { (_, g) -> g.sumOf { it.quantity.coerceAtLeast(1) } }
            val peakMonth = byMonth.maxByOrNull { it.value }
            if (peakMonth != null && byMonth.size >= 2) {
                val monthName = java.time.Month.of(peakMonth.key!!).getDisplayName(TextStyle.FULL, Locale.getDefault())
                    .replaceFirstChar { it.uppercase() }
                result += SmartObservation(
                    text = "$monthName has been the busiest chapter: ${peakMonth.value} ${if (peakMonth.value == 1) "play" else "plays"}.",
                    rarity = if (peakMonth.value >= 12) InsightRarity.RARE else InsightRarity.NOTABLE,
                    subtext = "Busiest month this year"
                )
            }

            // Unique games
            if (uniqueGames >= 5) {
                result += SmartObservation(
                    text = "$uniqueGames different games in ${now.year}. " +
                        if (uniqueGames > totalPlays / 2) "That's variety." else "Clear favorites are emerging.",
                    rarity = if (uniqueGames >= 20) InsightRarity.RARE else InsightRarity.NOTABLE,
                    subtext = "Range of play"
                )
            }

            // Average session this year
            if (avgSession > 0) {
                result += SmartObservation(
                    "Sessions run about ${formatDuration(avgSession)} this year."
                )
            }
        }

        StatsTimeRange.THIS_MONTH -> {
            val monthName = now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase() }

            // vs last month
            val lastMonthStart = now.withDayOfMonth(1).minusMonths(1)
            val lastMonthEnd = now.withDayOfMonth(1).minusDays(1)
            val lastMonthCount = allPlays.filter {
                runCatching {
                    val d = LocalDate.parse(it.date)
                    !d.isBefore(lastMonthStart) && !d.isAfter(lastMonthEnd)
                }.getOrDefault(false)
            }.sumOf { it.quantity.coerceAtLeast(1) }

            when {
                lastMonthCount == 0 -> result += SmartObservation(
                    text = "$monthName's table has $totalPlays ${if (totalPlays == 1) "play" else "plays"} so far.",
                    subtext = "$monthName's table"
                )
                totalPlays > lastMonthCount -> result += SmartObservation(
                    text = "$monthName has already passed last month: $totalPlays plays vs. $lastMonthCount.",
                    rarity = if (totalPlays >= lastMonthCount + 6) InsightRarity.NOTABLE else InsightRarity.COMMON,
                    subtext = "Month over month"
                )
                totalPlays == lastMonthCount -> result += SmartObservation(
                    "$totalPlays plays so far. Exactly matching last month."
                )
                else -> result += SmartObservation(
                    "$monthName is quieter than last month: $totalPlays plays vs. $lastMonthCount."
                )
            }

            // Most played game this month
            val topGame = groupBy { it.gameName }
                .mapValues { (_, g) -> g.sumOf { it.quantity.coerceAtLeast(1) } }
                .maxByOrNull { it.value }
            if (topGame != null && topGame.value >= 2) {
                result += SmartObservation(
                    text = "${topGame.key} owns $monthName so far: ${topGame.value} plays.",
                    rarity = if (topGame.value >= 10) InsightRarity.RARE else InsightRarity.NOTABLE,
                    subtext = "Most played this month"
                )
            }

            // Active days this month
            val activeDays = mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet().size
            val daysInMonth = now.dayOfMonth
            if (activeDays >= 3) {
                result += SmartObservation(
                    text = "Games on $activeDays of $daysInMonth days this month. A steady table.",
                    rarity = if (activeDays >= 10) InsightRarity.NOTABLE else InsightRarity.COMMON,
                    subtext = "$monthName's rhythm"
                )
            }

            // Average session
            if (avgSession > 0 && withDuration.size >= 2) {
                result += SmartObservation(
                    "Sessions in $monthName run about ${formatDuration(avgSession)}."
                )
            }
        }

        StatsTimeRange.LAST_30 -> {
            // vs previous 30 days
            val cutoff30 = now.minusDays(30)
            val cutoff60 = now.minusDays(60)
            val prev30Count = allPlays.filter {
                runCatching {
                    val d = LocalDate.parse(it.date)
                    !d.isBefore(cutoff60) && d.isBefore(cutoff30)
                }.getOrDefault(false)
            }.sumOf { it.quantity.coerceAtLeast(1) }

            when {
                prev30Count == 0 -> result += SmartObservation(
                    text = "$totalPlays plays in the last 30 days.",
                    subtext = "Recent run"
                )
                totalPlays > prev30Count * 1.3 -> result += SmartObservation(
                    text = "$totalPlays plays in 30 days, up from $prev30Count. Momentum building.",
                    rarity = InsightRarity.NOTABLE,
                    subtext = "Recent run"
                )
                totalPlays < prev30Count * 0.7 -> result += SmartObservation(
                    "$totalPlays plays in the last 30 days, down from $prev30Count. A quieter stretch."
                )
                else -> result += SmartObservation(
                    "$totalPlays plays in the last 30 days. Roughly the same as before."
                )
            }

            // Weekly pace
            val playsPerWeek = totalPlays.toFloat() / 4.3f
            if (playsPerWeek >= 1f) {
                result += SmartObservation(
                    "${String.format("%.1f", playsPerWeek)} plays per week over the recent run."
                )
            }

            // Unique games
            if (uniqueGames >= 3) {
                result += SmartObservation(
                    text = "$uniqueGames different games across $totalPlays plays. A restless table.",
                    rarity = if (uniqueGames >= 10) InsightRarity.RARE else InsightRarity.NOTABLE,
                    subtext = "Recent variety"
                )
            }

            // Average session
            if (avgSession > 0 && withDuration.size >= 3) {
                result += SmartObservation(
                    "Sessions run about ${formatDuration(avgSession)} over the recent run."
                )
            }
        }

        StatsTimeRange.ALL -> Unit
    }

    return result.distinctBy { it.text }
}

// ── Smart observations ────────────────────────────────────────────────────────

data class SmartObservation(
    val text: String,
    val rarity: InsightRarity = InsightRarity.COMMON,
    val subtext: String? = null,
    val dateAchieved: LocalDate = LocalDate.now()
)

data class StatsBriefItem(
    val title: String,
    val body: String,
    val metric: String? = null,
    val rarity: InsightRarity = InsightRarity.COMMON,
    val gameFilter: Pair<Int, String>? = null,
    val playerFilter: String? = null
)

fun List<LoggedPlay>.buildStatsBrief(
    range: StatsTimeRange,
    allPlays: List<LoggedPlay>,
    roster: List<Player> = emptyList(),
    currentPlayerName: String? = null
): List<StatsBriefItem> {
    if (isEmpty()) return emptyList()

    val now = LocalDate.now()
    val totalPlays = sumOf { it.quantity.coerceAtLeast(1) }
    val items = mutableListOf<StatsBriefItem>()

    fun playDate(play: LoggedPlay): LocalDate? =
        runCatching { LocalDate.parse(play.date) }.getOrNull()

    fun countBetween(start: LocalDate, end: LocalDate): Int =
        allPlays.sumOf { play ->
            val date = playDate(play)
            if (date != null && !date.isBefore(start) && !date.isAfter(end)) play.quantity.coerceAtLeast(1) else 0
        }

    fun comparisonItem(): StatsBriefItem? {
        val currentCount = totalPlays
        val previousCount = when (range) {
            StatsTimeRange.THIS_MONTH -> {
                val currentStart = now.withDayOfMonth(1)
                val previousStart = currentStart.minusMonths(1)
                val previousEnd = previousStart
                    .plusDays((now.dayOfMonth - 1).toLong())
                    .coerceAtMost(currentStart.minusDays(1))
                countBetween(previousStart, previousEnd)
            }
            StatsTimeRange.THIS_YEAR -> {
                val previousStart = LocalDate.of(now.year - 1, 1, 1)
                val previousEnd = runCatching {
                    LocalDate.of(now.year - 1, now.monthValue, now.dayOfMonth)
                }.getOrElse { LocalDate.of(now.year - 1, now.monthValue, 1).withDayOfMonth(1).minusDays(1) }
                countBetween(previousStart, previousEnd)
            }
            StatsTimeRange.LAST_30 -> {
                val previousStart = now.minusDays(59)
                val previousEnd = now.minusDays(30)
                countBetween(previousStart, previousEnd)
            }
            StatsTimeRange.ALL -> {
                val previousStart = now.minusDays(59)
                val previousEnd = now.minusDays(30)
                val recentStart = now.minusDays(29)
                val recentCount = countBetween(recentStart, now)
                val previous = countBetween(previousStart, previousEnd)
                if (recentCount == 0 && previous == 0) return null
                val delta = recentCount - previous
                val metric = if (delta >= 0) "+$delta" else delta.toString()
                val body = when {
                    previous == 0 && recentCount > 0 -> "$recentCount plays in the last 30 days. The table is awake again."
                    delta > 0 -> "$recentCount recent plays, up from $previous in the 30 days before."
                    delta < 0 -> "$recentCount recent plays, down from $previous in the 30 days before."
                    else -> "$recentCount recent plays. Almost exactly the same as before."
                }
                return StatsBriefItem(
                    title = "Recent pulse",
                    body = body,
                    metric = metric,
                    rarity = if (delta >= 5) InsightRarity.NOTABLE else InsightRarity.COMMON
                )
            }
        }

        if (currentCount == 0 && previousCount == 0) return null
        val delta = currentCount - previousCount
        val metric = if (delta >= 0) "+$delta" else delta.toString()
        val label = range.displaySubtitle()
        val body = when {
            previousCount == 0 && currentCount > 0 -> "$label has $currentCount plays. No matching baseline before it."
            delta > 0 -> "$label is $delta plays ahead of the previous matching stretch."
            delta < 0 -> "$label is ${-delta} plays behind the previous matching stretch."
            else -> "$label is matching the previous stretch exactly."
        }
        return StatsBriefItem(
            title = "Pace check",
            body = body,
            metric = metric,
            rarity = if (delta >= 5) InsightRarity.NOTABLE else InsightRarity.COMMON
        )
    }

    comparisonItem()?.let { items += it }

    val gameCounts = groupBy { it.gameId to it.gameName }
        .mapValues { (_, plays) -> plays.sumOf { it.quantity.coerceAtLeast(1) } }
    gameCounts.maxByOrNull { it.value }?.let { (game, count) ->
        if (totalPlays >= 3 && count >= 2) {
            val share = count * 100 / totalPlays
            val body = if (share >= 35) {
                "${game.second} owns $share% of this view. Clear favorite energy."
            } else {
                "${game.second} leads the table here with $count plays."
            }
            items += StatsBriefItem(
                title = "Table favorite",
                body = body,
                metric = "${count}x",
                rarity = if (share >= 35) InsightRarity.NOTABLE else InsightRarity.COMMON,
                gameFilter = game.first to game.second
            )
        }
    }

    if (range == StatsTimeRange.ALL) {
        groupBy { it.gameId to it.gameName }
            .mapNotNull { (game, plays) ->
                val count = plays.sumOf { it.quantity.coerceAtLeast(1) }
                val last = plays.mapNotNull(::playDate).maxOrNull()
                val daysQuiet = last?.let { now.toEpochDay() - it.toEpochDay() }
                if (count >= 4 && daysQuiet != null && daysQuiet > 60) {
                    Triple(game, count, daysQuiet)
                } else null
            }
            .maxWithOrNull(compareBy<Triple<Pair<Int, String>, Int, Long>> { it.second }.thenBy { it.third })
            ?.let { (game, count, daysQuiet) ->
                val months = (daysQuiet / 30).coerceAtLeast(2)
                items += StatsBriefItem(
                    title = "Waiting on the shelf",
                    body = "${game.second} used to show up. $count plays, none in $months months.",
                    metric = "${months}mo",
                    rarity = if (count >= 25) InsightRarity.RARE else InsightRarity.NOTABLE,
                    gameFilter = game.first to game.second
                )
            }
    }

    buildTopRivalryPairs(limit = 1, roster = roster, currentPlayerName = currentPlayerName)
        .firstOrNull()
        ?.let { pair ->
            items += StatsBriefItem(
                title = "Rivalry watch",
                body = pair.narrativeLine,
                metric = "${pair.playsCount}x",
                rarity = if (pair.playsCount >= 10) InsightRarity.RARE else InsightRarity.NOTABLE
            )
        }

    val playerCounts = mutableMapOf<String, Int>()
    forEach { play ->
        play.players.filter { it.name.isNotBlank() }.forEach { player ->
            val name = resolveDisplayName(player.name, roster)
            playerCounts[name] = (playerCounts[name] ?: 0) + play.quantity.coerceAtLeast(1)
        }
    }
    playerCounts.maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.let { (name, count) ->
        items += StatsBriefItem(
            title = "Most present",
            body = "$name has been at the table for $count plays in this view.",
            metric = "${count}x",
            rarity = InsightRarity.COMMON,
            playerFilter = name
        )
    }

    return items
        .distinctBy { it.title to it.body }
        .sortedWith(compareByDescending<StatsBriefItem> { it.rarity.sortWeight }.thenBy { it.title })
        .take(3)
}

fun List<LoggedPlay>.buildSmartObservations(): List<SmartObservation> {
    if (size < 5) return emptyList()
    val result = mutableListOf<SmartObservation>()
    val now = LocalDate.now()
    val totalPlays = sumOf { it.quantity.coerceAtLeast(1) }
    val withDuration = filter { it.durationMinutes > 0 }
    val totalMinutes = withDuration.sumOf { it.durationMinutes }
    val uniqueGames = map { it.gameName }.toSet().size

    // Total time as days
    if (totalMinutes >= 180) {
        val hours = totalMinutes / 60
        val days = totalMinutes / (60 * 24)
        if (days >= 2) {
            result += SmartObservation(
                text = "${hours}h at the table. That's $days full days. Not nothing.",
                rarity = if (hours >= 1000) InsightRarity.LEGENDARY else if (hours >= 250) InsightRarity.EPIC else InsightRarity.RARE,
                subtext = "All-time table time"
            )
        } else {
            result += SmartObservation(
                text = "${hours}h at the table. The habit has shape.",
                rarity = InsightRarity.NOTABLE,
                subtext = "All-time table time"
            )
        }
    }

    // Dominant game by time share
    if (withDuration.size >= 5 && totalMinutes > 0) {
        val gameMinutes = withDuration.groupBy { it.gameName }.mapValues { (_, g) -> g.sumOf { it.durationMinutes } }
        val top = gameMinutes.maxByOrNull { it.value }
        if (top != null) {
            val pct = top.value * 100 / totalMinutes
            if (pct >= 20) {
                result += SmartObservation(
                    text = "Nearly ${pct}% of your table time belongs to ${top.key}.",
                    rarity = if (pct >= 35) InsightRarity.RARE else InsightRarity.NOTABLE,
                    subtext = "Most-played by time"
                )
            }
        }
    }

    // Weekend vs weekday session length
    val weekdayDur = filter { p ->
        p.durationMinutes > 0 &&
            runCatching { LocalDate.parse(p.date).dayOfWeek.value in 1..5 }.getOrDefault(false)
    }
    val weekendDur = filter { p ->
        p.durationMinutes > 0 &&
            runCatching { LocalDate.parse(p.date).dayOfWeek.value in 6..7 }.getOrDefault(false)
    }
    if (weekdayDur.size >= 4 && weekendDur.size >= 4) {
        val wdAvg = weekdayDur.sumOf { it.durationMinutes } / weekdayDur.size
        val weAvg = weekendDur.sumOf { it.durationMinutes } / weekendDur.size
        if (weAvg > wdAvg * 1.4) {
            result += SmartObservation(
                text = "Weekend sessions average ${formatDuration(weAvg)}. Weeknights: ${formatDuration(wdAvg)}. " +
                    "Two different tables, somehow.",
                rarity = InsightRarity.NOTABLE,
                subtext = "Table rhythm"
            )
        }
    }

    // Dormant beloved game
    val dormant = groupBy { it.gameName }
        .mapNotNull { (name, plays) ->
            val count = plays.sumOf { it.quantity.coerceAtLeast(1) }
            val last = plays.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()
            val days = last?.let { now.toEpochDay() - it.toEpochDay() }
            if (count >= 4 && days != null && days > 60) Triple(name, days, count) else null
        }
        .maxByOrNull { it.third }
    if (dormant != null) {
        val months = (dormant.second / 30).toInt()
        result += SmartObservation(
            text = "${dormant.first} hasn't hit the table in ${months} month${if (months > 1) "s" else ""}. " +
                "${dormant.third} plays, then quiet.",
            rarity = if (dormant.third >= 25) InsightRarity.RARE else InsightRarity.NOTABLE,
            subtext = "Waiting on the shelf"
        )
    }

    // New games discovered this year
    val thisYear = now.year
    val newThisYear = map { it.gameName }.toSet().count { name ->
        val first = filter { it.gameName == name }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.minOrNull()
        first?.year == thisYear
    }
    when {
        newThisYear >= 5 -> result += SmartObservation(
            text = "$newThisYear new-to-you games this year. A wide table.",
            rarity = if (newThisYear >= 20) InsightRarity.RARE else InsightRarity.NOTABLE,
            subtext = "Exploration"
        )
        newThisYear in 2..4 -> result += SmartObservation(
            "$newThisYear new-to-you games this year. Still exploring."
        )
    }

    // Dominant season
    val bySeason = IntArray(4)
    forEach { play ->
        runCatching {
            val m = LocalDate.parse(play.date).monthValue
            val s = when (m) { in 3..5 -> 0; in 6..8 -> 1; in 9..11 -> 2; else -> 3 }
            bySeason[s] += play.quantity.coerceAtLeast(1)
        }
    }
    val seasonTotal = bySeason.sum()
    if (seasonTotal > 0) {
        val peakIdx = bySeason.indices.maxByOrNull { bySeason[it] }!!
        val pct = bySeason[peakIdx] * 100 / seasonTotal
        if (pct >= 38) {
            val names = listOf("spring", "summer", "fall", "winter")
            result += SmartObservation(
                text = "${pct}% of your plays happen in ${names[peakIdx]}. The table has a season.",
                rarity = InsightRarity.NOTABLE,
                subtext = "Seasonality"
            )
        }
    }

    // Play pace vs last year
    val lastYear = thisYear - 1
    val thisYearCount = filter { runCatching { LocalDate.parse(it.date).year == thisYear }.getOrDefault(false) }
        .sumOf { it.quantity.coerceAtLeast(1) }
    val lastYearCount = filter { runCatching { LocalDate.parse(it.date).year == lastYear }.getOrDefault(false) }
        .sumOf { it.quantity.coerceAtLeast(1) }
    val monthsIn = now.monthValue
    if (lastYearCount >= 8 && thisYearCount > 0 && monthsIn >= 2) {
        val projected = thisYearCount * 12 / monthsIn
        when {
            projected > lastYearCount * 1.25 ->
                result += SmartObservation(
                    text = "On pace for $projected plays this year. $lastYear was $lastYearCount.",
                    rarity = if (projected >= 100) InsightRarity.RARE else InsightRarity.NOTABLE,
                    subtext = "Year pace"
                )
            projected < lastYearCount * 0.65 && monthsIn >= 4 ->
                result += SmartObservation(
                    "Quieter than $lastYear so far: $thisYearCount plays in $monthsIn months."
                )
        }
    }

    // Consistency vs burstiness
    val monthCounts = groupBy { p ->
        runCatching { LocalDate.parse(p.date).let { it.year * 100 + it.monthValue } }.getOrNull()
    }.filterKeys { it != null }.mapValues { (_, g) -> g.sumOf { it.quantity.coerceAtLeast(1) } }
    if (monthCounts.size >= 6) {
        val vals = monthCounts.values.toList()
        val avg = vals.average()
        val stdDev = sqrt(vals.map { (it - avg) * (it - avg) }.average())
        val cv = if (avg > 0) stdDev / avg else 0.0
        when {
            cv < 0.35 && avg >= 2 -> result += SmartObservation(
                "${avg.toInt()} plays a month, give or take. The table has a rhythm."
            )
            cv > 1.2 -> result += SmartObservation(
                text = "Quiet months, then a flurry. The table calls when it calls.",
                rarity = InsightRarity.NOTABLE,
                subtext = "Table rhythm"
            )
        }
    }

    // Depth vs breadth
    if (totalPlays >= 20 && uniqueGames >= 3) {
        val depth = totalPlays.toDouble() / uniqueGames
        when {
            depth > 5.0 -> result += SmartObservation(
                text = "$uniqueGames games, $totalPlays plays. You go deep.",
                rarity = if (totalPlays >= 100) InsightRarity.RARE else InsightRarity.NOTABLE,
                subtext = "Depth over breadth"
            )
            depth < 1.5 -> result += SmartObservation(
                text = "$uniqueGames different games across $totalPlays plays. A wide table.",
                rarity = if (uniqueGames >= 50) InsightRarity.EPIC else InsightRarity.NOTABLE,
                subtext = "Breadth of play"
            )
        }
    }

    // Anniversary insights — first play ±7 days from today, at least 1 year ago
    groupBy { it.gameName }.forEach { (gameName, plays) ->
        val firstPlay = plays
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .minOrNull() ?: return@forEach
        val yearsDiff = now.year - firstPlay.year
        if (yearsDiff < 1) return@forEach
        val anniversaryThisYear = runCatching {
            LocalDate.of(now.year, firstPlay.monthValue, firstPlay.dayOfMonth)
        }.getOrNull() ?: return@forEach
        val daysAway = abs(now.toEpochDay() - anniversaryThisYear.toEpochDay())
        if (daysAway > 7) return@forEach
        val gameTotalPlays = plays.sumOf { it.quantity.coerceAtLeast(1) }
        val yearsText = if (yearsDiff == 1) "One year ago" else "$yearsDiff years ago"
        result += SmartObservation(
            text    = "$yearsText, you played $gameName for the first time. $gameTotalPlays plays since.",
            rarity  = if (yearsDiff >= 2) InsightRarity.RARE else InsightRarity.NOTABLE,
            subtext = "Anniversary"
        )
    }

    // Patron game — the game you keep returning to after breaks (14+ day gaps)
    data class PatronCandidate(val name: String, val comebacks: Int, val totalPlays: Int)
    val patronCandidates = groupBy { it.gameName }.mapNotNull { (gameName, plays) ->
        val gameTotalPlays = plays.sumOf { it.quantity.coerceAtLeast(1) }
        if (gameTotalPlays < 8) return@mapNotNull null
        val dates = plays
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .sorted()
        if (dates.size < 4) return@mapNotNull null
        val comebacks = (1 until dates.size).count { i ->
            dates[i].toEpochDay() - dates[i - 1].toEpochDay() >= 14
        }
        if (comebacks < 3) return@mapNotNull null
        PatronCandidate(gameName, comebacks, gameTotalPlays)
    }
    patronCandidates.maxByOrNull { it.comebacks }?.let { top ->
        result += SmartObservation(
            text    = "${top.name} is the game you keep coming back to. ${top.comebacks} returns after a break.",
            rarity  = if (top.comebacks >= 6) InsightRarity.RARE else InsightRarity.NOTABLE,
            subtext = "Patron game"
        )
    }

    return result.distinctBy { it.text }
}

// ── Period in Review ─────────────────────────────────────────────────────────

data class PeriodReview(
    val periodLabel: String,   // "April in Review" / "2024 in Review"
    val headline: String       // "April: 14 plays, 4 games, 3 new players. Martin finally won Brass."
)

private fun buildPeriodHighlight(
    periodPlays: List<LoggedPlay>,
    earlierPlays: List<LoggedPlay>
): String? {
    // Build (playerKey, gameName) → prior win/play counts
    val priorWins  = mutableMapOf<Pair<String, String>, Int>()
    val priorGames = mutableMapOf<Pair<String, String>, Int>()
    earlierPlays.forEach { play ->
        play.players.forEach { player ->
            val pk = player.name.lowercase().trim()
            if (pk.isBlank()) return@forEach
            val key = pk to play.gameName
            priorGames[key] = (priorGames[key] ?: 0) + play.quantity.coerceAtLeast(1)
            if (player.isWinner) priorWins[key] = (priorWins[key] ?: 0) + play.quantity.coerceAtLeast(1)
        }
    }

    // "Finally won" — won in period, but never won before despite 2+ prior plays
    data class FirstWin(val displayName: String, val gameName: String, val waitedFor: Int)
    val firstWins = mutableListOf<FirstWin>()
    val seen      = mutableSetOf<Pair<String, String>>()
    periodPlays.forEach { play ->
        play.players.filter { it.isWinner && it.name.isNotBlank() }.forEach { player ->
            val pk  = player.name.lowercase().trim()
            val key = pk to play.gameName
            if (key in seen) return@forEach
            seen += key
            val wins  = priorWins[key]  ?: 0
            val games = priorGames[key] ?: 0
            if (wins == 0 && games >= 2) {
                firstWins += FirstWin(player.name.trim(), play.gameName, games)
            }
        }
    }
    firstWins.maxByOrNull { it.waitedFor }?.let { fw ->
        return "${fw.displayName} finally won ${fw.gameName}."
    }

    // Fallback: who dominated the most-played game in the period?
    val topGameName = periodPlays
        .groupBy { it.gameName }
        .maxByOrNull { (_, g) -> g.sumOf { it.quantity.coerceAtLeast(1) } }
        ?.key ?: return null
    val winsByPlayer = mutableMapOf<String, Int>()
    periodPlays.filter { it.gameName == topGameName }.forEach { play ->
        play.players.filter { it.isWinner && it.name.isNotBlank() }.forEach { player ->
            val name = player.name.trim()
            winsByPlayer[name] = (winsByPlayer[name] ?: 0) + play.quantity.coerceAtLeast(1)
        }
    }
    val topWinner = winsByPlayer.maxByOrNull { it.value } ?: return null
    if (topWinner.value < 2) return null
    return "${topWinner.key} led $topGameName — ${topWinner.value} wins."
}

fun List<LoggedPlay>.buildPeriodReview(): PeriodReview? {
    val now = LocalDate.now()

    // Determine which period to review (year takes priority over month)
    val periodStart: LocalDate
    val periodEnd:   LocalDate
    val periodName:  String
    val isYear:      Boolean

    when {
        now.dayOfYear <= 7 -> {
            val prevYear = now.year - 1
            periodStart  = LocalDate.of(prevYear, 1, 1)
            periodEnd    = LocalDate.of(prevYear, 12, 31)
            periodName   = "$prevYear"
            isYear       = true
        }
        now.dayOfMonth <= 5 -> {
            val prev    = now.withDayOfMonth(1).minusDays(1)
            periodStart = prev.withDayOfMonth(1)
            periodEnd   = prev
            periodName  = prev.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
            isYear      = false
        }
        else -> return null
    }

    val periodPlays = filter { play ->
        runCatching {
            val d = LocalDate.parse(play.date)
            !d.isBefore(periodStart) && !d.isAfter(periodEnd)
        }.getOrDefault(false)
    }
    if (periodPlays.isEmpty()) return null

    val totalPlays  = periodPlays.sumOf { it.quantity.coerceAtLeast(1) }
    val uniqueGames = periodPlays.map { it.gameName }.toSet().size

    val earlierPlays = filter { play ->
        runCatching { LocalDate.parse(play.date).isBefore(periodStart) }.getOrDefault(false)
    }
    val existingPlayers = earlierPlays
        .flatMap { it.players.map { p -> p.name.lowercase().trim() } }
        .filter { it.isNotBlank() }.toSet()
    val newPlayerCount  = periodPlays
        .flatMap { it.players.map { p -> p.name.lowercase().trim() } }
        .filter { it.isNotBlank() }.toSet()
        .count { it !in existingPlayers }

    val statsStr = buildString {
        append("$totalPlays ${if (totalPlays == 1) "play" else "plays"}")
        append(", $uniqueGames ${if (uniqueGames == 1) "game" else "games"}")
        if (newPlayerCount > 0) append(", $newPlayerCount new ${if (newPlayerCount == 1) "player" else "players"}")
    }

    val highlight = buildPeriodHighlight(periodPlays, earlierPlays)
    val headline  = "$periodName: $statsStr." + (if (highlight != null) " $highlight" else "")

    return PeriodReview(
        periodLabel = "$periodName in Review",
        headline    = headline
    )
}

// ── Contextual narrative header ───────────────────────────────────────────────

fun List<LoggedPlay>.buildContextualHeader(range: StatsTimeRange, currentStreak: Int = 0): String {
    if (isEmpty()) return "Nothing logged yet."
    val now = LocalDate.now()
    val monthName = now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { it.uppercase() }
    return when {
        currentStreak >= 7 -> "$currentStreak days in a row."
        currentStreak >= 3 -> "A good stretch."
        range == StatsTimeRange.THIS_MONTH -> "$monthName's table"
        range == StatsTimeRange.THIS_YEAR -> "${now.year} so far"
        range == StatsTimeRange.LAST_30 -> "Recent run"
        else -> {
            val recent = any { p ->
                runCatching { LocalDate.parse(p.date) >= now.minusDays(3) }.getOrDefault(false)
            }
            val totalPlays = sumOf { it.quantity.coerceAtLeast(1) }
            val years = mapNotNull { runCatching { LocalDate.parse(it.date).year }.getOrNull() }.toSet().size
            when {
                recent -> "Back at the table"
                totalPlays < 5 -> "Getting started."
                years >= 3 -> "$years years of gaming"
                else -> "Your gaming story"
            }
        }
    }
}

// ── Gamer archetype ───────────────────────────────────────────────────────────

enum class GamerArchetype(val title: String, val tagline: String) {
    DEDICANT("The Dedicant", "One game, understood deeply."),
    LOYALIST("The Loyalist", "Faithful to the favorites."),
    MARATHONER("The Marathoner", "Long sessions. Full commitment."),
    SOCIALITE("The Socialite", "Better with more people."),
    EXPLORER("The Explorer", "Always after the next one."),
    CURATOR("The Curator", "Wide collection, deliberate play.")
}

fun List<LoggedPlay>.computeGamerArchetype(): GamerArchetype? {
    val total = sumOf { it.quantity.coerceAtLeast(1) }
    if (total < 10) return null
    val uniqueGames = map { it.gameName }.toSet().size
    val gameCounts = groupBy { it.gameName }.mapValues { (_, g) -> g.sumOf { it.quantity.coerceAtLeast(1) } }
    val top1Share = (gameCounts.values.maxOrNull() ?: 0).toDouble() / total
    val top3Share = gameCounts.values.sortedDescending().take(3).sum().toDouble() / total
    val withDuration = filter { it.durationMinutes > 0 }
    val avgSession = if (withDuration.isNotEmpty()) withDuration.sumOf { it.durationMinutes }.toDouble() / withDuration.size else 0.0
    val avgPlayers = if (isNotEmpty()) sumOf { it.players.count { p -> p.name.isNotBlank() } }.toDouble() / size else 0.0
    val diversityRatio = if (total > 0) uniqueGames.toDouble() / total else 0.0
    return when {
        top1Share > 0.5 && total >= 15 -> GamerArchetype.DEDICANT
        top3Share > 0.70 -> GamerArchetype.LOYALIST
        avgSession > 150 -> GamerArchetype.MARATHONER
        avgPlayers > 4.0 -> GamerArchetype.SOCIALITE
        diversityRatio > 0.72 && uniqueGames >= 10 -> GamerArchetype.EXPLORER
        uniqueGames >= 20 && diversityRatio in 0.35..0.72 -> GamerArchetype.CURATOR
        else -> GamerArchetype.LOYALIST
    }
}

// ── 52-week play heatmap ──────────────────────────────────────────────────────

data class HeatmapDay(val date: LocalDate, val count: Int) // count = -1 for future
data class HeatmapWeek(val days: List<HeatmapDay>)
data class HeatmapData(
    val weeks: List<HeatmapWeek>,
    val maxCount: Int,
    val monthLabels: List<Pair<Int, String>> // weekIndex → abbreviated month name
)

fun List<LoggedPlay>.buildHeatmapData(weekCount: Int = 52): HeatmapData {
    val now = LocalDate.now()
    val countsByDate = groupBy { p -> runCatching { LocalDate.parse(p.date) }.getOrNull() }
        .filterKeys { it != null }
        .mapValues { (_, g) -> g.sumOf { it.quantity.coerceAtLeast(1) } }
        .mapKeys { it.key!! }

    val startMonday = now.minusWeeks(weekCount.toLong()).let { d ->
        d.minusDays((d.dayOfWeek.value - 1).toLong())
    }

    val allWeeks = mutableListOf<HeatmapWeek>()
    val monthLabels = mutableListOf<Pair<Int, String>>()
    var lastMonth = -1
    var weekStart = startMonday

    while (!weekStart.isAfter(now)) {
        val days = (0..6).map { d ->
            val day = weekStart.plusDays(d.toLong())
            HeatmapDay(day, if (!day.isAfter(now)) (countsByDate[day] ?: 0) else -1)
        }
        val weekIdx = allWeeks.size
        if (weekStart.monthValue != lastMonth) {
            monthLabels += weekIdx to weekStart.month
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
            lastMonth = weekStart.monthValue
        }
        allWeeks += HeatmapWeek(days)
        weekStart = weekStart.plusWeeks(1)
    }

    val maxCount = allWeeks.flatMap { it.days }.filter { it.count >= 0 }.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    return HeatmapData(allWeeks, maxCount, monthLabels)
}

// ── On This Day ───────────────────────────────────────────────────────────────

data class OnThisDayEntry(val yearsAgo: Int, val year: Int, val plays: List<LoggedPlay>)

fun List<LoggedPlay>.buildOnThisDay(): List<OnThisDayEntry> {
    val today = LocalDate.now()
    return (1..3).mapNotNull { yearsAgo ->
        val target = runCatching { LocalDate.of(today.year - yearsAgo, today.month, today.dayOfMonth) }.getOrNull()
            ?: return@mapNotNull null
        val plays = filter { runCatching { LocalDate.parse(it.date) == target }.getOrDefault(false) }
        if (plays.isNotEmpty()) OnThisDayEntry(yearsAgo, today.year - yearsAgo, plays) else null
    }
}

// ── Rivalry pairs at the table ────────────────────────────────────────────────

internal fun shortName(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return if (parts.size <= 1) name.trim()
    else "${parts.first()} ${parts.last().first().uppercaseChar()}."
}

data class RivalryPair(
    val playerA: String,
    val playerB: String,
    val playsCount: Int,
    val aWins: Int,
    val bWins: Int,
    val mostPlayedGame: String?,
    val narrativeLine: String
)

fun List<LoggedPlay>.buildTopRivalryPairs(
    limit: Int = 3,
    roster: List<Player> = emptyList(),
    currentPlayerName: String? = null
): List<RivalryPair> {
    val pairPlays = mutableMapOf<String, Triple<Int, Int, Int>>()
    val pairGames = mutableMapOf<String, MutableMap<String, Int>>()

    forEach { play ->
        val active = play.players.filter { it.name.isNotBlank() }
        if (active.size < 2) return@forEach
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val a = active[i]; val b = active[j]
                val aName = resolveDisplayName(a.name.trim(), roster)
                val bName = resolveDisplayName(b.name.trim(), roster)
                val sorted = listOf(aName, bName).sorted()
                val key = sorted.joinToString("|")
                val aIsFirst = aName == sorted[0]
                val (t, aw, bw) = pairPlays[key] ?: Triple(0, 0, 0)
                pairPlays[key] = Triple(
                    t + 1,
                    aw + if (if (aIsFirst) a.isWinner else b.isWinner) 1 else 0,
                    bw + if (if (aIsFirst) b.isWinner else a.isWinner) 1 else 0
                )
                pairGames.getOrPut(key) { mutableMapOf() }
                    .also { it[play.gameName] = (it[play.gameName] ?: 0) + 1 }
            }
        }
    }

    val currentNorm = currentPlayerName?.lowercase()?.trim()

    return pairPlays.entries
        .filter { it.value.first >= 3 }
        .sortedByDescending { it.value.first }
        .take(limit)
        .map { (key, data) ->
            val names = key.split("|")
            val (together, aWins, bWins) = data
            val aIsMe = currentNorm != null && names[0].lowercase().trim() == currentNorm
            val bIsMe = currentNorm != null && names[1].lowercase().trim() == currentNorm
            val tied = aWins == bWins
            val lead = abs(aWins - bWins)
            val scoreStr = if (aWins >= bWins) "$aWins–$bWins" else "$bWins–$aWins"
            val mostGame = pairGames[key]?.maxByOrNull { it.value }?.key
            val narrative = when {
                together < 5 -> when {
                    aIsMe -> "You and ${names[1]}. $together games together so far."
                    bIsMe -> "You and ${names[0]}. $together games together so far."
                    else  -> "${names[0]} and ${names[1]}. $together games together so far."
                }
                tied -> when {
                    aIsMe -> "Deadlocked with ${names[1]}. $aWins each after $together games."
                    bIsMe -> "Deadlocked with ${names[0]}. $bWins each after $together games."
                    else  -> "Deadlocked. $aWins each after $together games."
                }
                aWins >= bWins -> when {
                    aIsMe -> if (lead == 1) "You lead by one. $together games played." else "You lead $aWins–$bWins. $together games."
                    bIsMe -> if (lead == 1) "${names[0]} leads you by one. $together games played." else "${names[0]} leads you $aWins–$bWins. $together games."
                    else  -> if (lead == 1) "${names[0]} leads by one. $together games played." else "${names[0]} leads $scoreStr. $together games."
                }
                else -> when {
                    bIsMe -> if (lead == 1) "You lead by one. $together games played." else "You lead $bWins–$aWins. $together games."
                    aIsMe -> if (lead == 1) "${names[1]} leads you by one. $together games played." else "${names[1]} leads you $bWins–$aWins. $together games."
                    else  -> if (lead == 1) "${names[1]} leads by one. $together games played." else "${names[1]} leads $scoreStr. $together games."
                }
            }
            // Show "You" in bar labels when this player is the current user
            val labelA = if (aIsMe) "You" else names[0]
            val labelB = if (bIsMe) "You" else names[1]
            RivalryPair(labelA, labelB, together, aWins, bWins, mostGame, narrative)
        }
}
