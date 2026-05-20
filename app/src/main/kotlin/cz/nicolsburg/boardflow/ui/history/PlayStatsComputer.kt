package cz.nicolsburg.boardflow.ui.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import cz.nicolsburg.boardflow.data.CanonicalCollectionStore
import cz.nicolsburg.boardflow.data.DatePlayRow
import cz.nicolsburg.boardflow.data.NamePlayRow
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.StatsPlayScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ── Data holders ─────────────────────────────────────────────────────────────

data class ActivityBucket(
    val label: String,
    val peakLabel: String,
    val count: Int,
    val highlight: Boolean = false
)

data class GameStat(val gameId: Int, val name: String, val plays: Int)
data class PlayerStat(val displayName: String, val plays: Int, val wins: Int)
data class StatsInsight(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val detail: String? = null,
    val gameFilter: Pair<Int, String>? = null,
    val playerFilter: String? = null,
    val recentDaysFilter: Int? = null
)

data class PlayStats(
    val hasSourcePlays: Boolean,
    val totalPlays: Int,
    val uniqueGames: Int,
    val totalMinutes: Int,
    val activePlayers: Int,
    val activity: List<ActivityBucket>,
    val topGames: List<GameStat>,
    val topPlayers: List<PlayerStat>,
    val hIndex: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val hotPlayerStreak: Pair<String, Int>?,
    val longestSession: Pair<String, Int>?,
    val mostThisMonth: Pair<String, Int>?,
    val insights: List<StatsInsight>,
    val headerText: String,
    val activeObservations: List<SmartObservation>,
    val archetype: GamerArchetype?,
    val rivalryPairs: List<RivalryPair>,
    val periodReview: PeriodReview?,
    val onThisDay: List<OnThisDayEntry>,
    val heatmapData: HeatmapData,
    val statsPlayScope: StatsPlayScope
)

fun emptyPlayStats(scope: StatsPlayScope) = PlayStats(
    hasSourcePlays = false,
    totalPlays = 0, uniqueGames = 0, totalMinutes = 0, activePlayers = 0,
    activity = emptyList(), topGames = emptyList(), topPlayers = emptyList(),
    hIndex = 0, currentStreak = 0, bestStreak = 0,
    hotPlayerStreak = null, longestSession = null, mostThisMonth = null,
    insights = emptyList(), headerText = "", activeObservations = emptyList(),
    archetype = null, rivalryPairs = emptyList(), periodReview = null,
    onThisDay = emptyList(),
    heatmapData = HeatmapData(emptyList(), 1, emptyList()),
    statsPlayScope = scope
)

// ── Entry point ───────────────────────────────────────────────────────────────

suspend fun computePlayStats(
    store: CanonicalCollectionStore,
    sourcePlays: List<LoggedPlay>,
    filteredPlays: List<LoggedPlay>,
    roster: List<Player>,
    timeRange: StatsTimeRange,
    scope: StatsPlayScope,
    currentPlayerName: String?
): PlayStats {
    if (sourcePlays.isEmpty()) return emptyPlayStats(scope)

    val countAll = scope == StatsPlayScope.ALL_PLAYS
    val afterDate = timeRange.toAfterDate()

    val summary = store.getPlaySummary(countAll, afterDate)
    val topGameRows = store.getTopGamesByPlays(countAll, afterDate)
    val datePlayRows = store.getPlaysByDate(countAll, afterDate)
    val gamePlayCounts = store.getGamePlayCounts(countAll, afterDate)
    val longestSessionRow = store.getLongestSession(countAll, afterDate)

    val activePlayers = filteredPlays
        .flatMap { play -> play.players.map { it.name.trim().lowercase() } }
        .filter { it.isNotBlank() }.toSet().size

    val topGames = topGameRows.map { GameStat(it.gameId, it.gameName, it.plays) }
    val topPlayers = buildTopPlayers(filteredPlays, roster)
    val hIndex = computeHIndexFromCounts(gamePlayCounts)
    val activity = buildActivityBucketsFromData(datePlayRows, timeRange)
    val (curStreak, bestStreak) = computeStreaks(filteredPlays)
    val longestSession = longestSessionRow?.let { it.gameName to it.plays }
    val hotStreak = filteredPlays.hotPlayerStreak(roster)
    val mostThisMonth = if (timeRange == StatsTimeRange.ALL) filteredPlays.mostPlayedThisMonth() else null
    val insights = buildInsights(filteredPlays, topGames, topPlayers, hIndex, curStreak, bestStreak, longestSession, hotStreak, mostThisMonth)
    val headerText = filteredPlays.buildContextualHeader(timeRange, curStreak)

    val observations = sourcePlays.buildSmartObservations()
    val rangeObservations = filteredPlays.buildRangeObservations(timeRange, sourcePlays)
    val activeObservations = if (timeRange == StatsTimeRange.ALL) observations else rangeObservations

    val archetype = if (timeRange == StatsTimeRange.ALL) sourcePlays.computeGamerArchetype() else null
    val rivalryPairs = filteredPlays.buildTopRivalryPairs(roster = roster, currentPlayerName = currentPlayerName)
    val periodReview = sourcePlays.buildPeriodReview()
    val onThisDay = sourcePlays.buildOnThisDay()
    val heatmapData = sourcePlays.buildHeatmapData()

    return PlayStats(
        hasSourcePlays = true,
        totalPlays = summary.totalPlays,
        uniqueGames = summary.uniqueGames,
        totalMinutes = summary.totalMinutes,
        activePlayers = activePlayers,
        activity = activity,
        topGames = topGames,
        topPlayers = topPlayers,
        hIndex = hIndex,
        currentStreak = curStreak,
        bestStreak = bestStreak,
        hotPlayerStreak = hotStreak,
        longestSession = longestSession,
        mostThisMonth = mostThisMonth,
        insights = insights,
        headerText = headerText,
        activeObservations = activeObservations,
        archetype = archetype,
        rivalryPairs = rivalryPairs,
        periodReview = periodReview,
        onThisDay = onThisDay,
        heatmapData = heatmapData,
        statsPlayScope = scope
    )
}

// ── Computation functions ─────────────────────────────────────────────────────

fun buildActivityBucketsFromData(data: List<DatePlayRow>, range: StatsTimeRange): List<ActivityBucket> {
    val now = LocalDate.now()
    val byDay = data.mapNotNull { row ->
        runCatching { LocalDate.parse(row.date) to row.plays }.getOrNull()
    }.toMap()

    fun dailyBuckets(start: LocalDate, end: LocalDate): List<ActivityBucket> {
        val days = (end.toEpochDay() - start.toEpochDay()).coerceAtLeast(0).toInt()
        return (0..days).map { offset ->
            val day = start.plusDays(offset.toLong())
            ActivityBucket(
                label = day.dayOfMonth.toString(),
                peakLabel = day.format(DateTimeFormatter.ofPattern("MMM d")),
                count = byDay[day] ?: 0,
                highlight = day == now
            )
        }
    }

    when (range) {
        StatsTimeRange.THIS_MONTH -> {
            val start = LocalDate.of(now.year, now.monthValue, 1)
            return dailyBuckets(start, now)
        }
        StatsTimeRange.LAST_30 -> return dailyBuckets(now.minusDays(29), now)
        else -> Unit
    }

    val byMonth = byDay.entries
        .groupBy { (date, _) -> date.year * 100 + date.monthValue }
        .mapValues { (_, entries) -> entries.sumOf { it.value } }
    val currentYM = now.year * 100 + now.monthValue

    return when (range) {
        StatsTimeRange.THIS_YEAR -> {
            (1..now.monthValue).map { m ->
                val d = LocalDate.of(now.year, m, 1)
                val ym = d.year * 100 + d.monthValue
                ActivityBucket(
                    label = d.format(DateTimeFormatter.ofPattern("MMM")),
                    peakLabel = d.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                    count = byMonth[ym] ?: 0,
                    highlight = ym == currentYM
                )
            }
        }
        StatsTimeRange.ALL -> {
            if (byMonth.isEmpty()) return emptyList()
            val earliestYM = byMonth.keys.min()
            val earliestYear = earliestYM / 100
            val earliestMonth = earliestYM % 100
            val monthSpan = (now.year - earliestYear) * 12 + (now.monthValue - earliestMonth) + 1
            if (monthSpan > 18) {
                (earliestYear..now.year).map { year ->
                    val yearCount = byMonth.entries.filter { it.key / 100 == year }.sumOf { it.value }
                    ActivityBucket(
                        label = year.toString(),
                        peakLabel = year.toString(),
                        count = yearCount,
                        highlight = year == now.year
                    )
                }
            } else {
                val start = LocalDate.of(earliestYear, earliestMonth, 1)
                (0 until monthSpan).map { m ->
                    val d = start.plusMonths(m.toLong())
                    val ym = d.year * 100 + d.monthValue
                    ActivityBucket(
                        label = d.format(DateTimeFormatter.ofPattern("MMM ''yy")),
                        peakLabel = d.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                        count = byMonth[ym] ?: 0,
                        highlight = ym == currentYM
                    )
                }
            }
        }
        else -> emptyList()
    }
}

fun buildTopPlayers(plays: List<LoggedPlay>, roster: List<Player>, limit: Int = 8): List<PlayerStat> {
    fun resolveDisplayName(raw: String): String {
        val lower = raw.lowercase().trim()
        return roster.firstOrNull { p ->
            p.displayName.lowercase().trim() == lower ||
                p.aliases.any { a -> a.lowercase().trim() == lower }
        }?.displayName ?: "Unknown"
    }
    val map = mutableMapOf<String, Pair<Int, Int>>()
    plays.forEach { play ->
        play.players.filter { it.name.isNotBlank() }.forEach { pr ->
            val name = resolveDisplayName(pr.name)
            val (p, w) = map[name] ?: (0 to 0)
            map[name] = (p + 1) to (if (pr.isWinner) w + 1 else w)
        }
    }
    return map.entries.sortedByDescending { it.value.first }.take(limit)
        .map { (name, pw) -> PlayerStat(name, pw.first, pw.second) }
}

fun computeStreaks(plays: List<LoggedPlay>): Pair<Int, Int> {
    val dates = plays.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSortedSet()
    if (dates.isEmpty()) return 0 to 0
    val sorted = dates.toList()
    var best = 1; var run = 1
    for (i in 1 until sorted.size) {
        run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
        if (run > best) best = run
    }
    val today = LocalDate.now()
    val current = if (sorted.last() >= today.minusDays(1)) {
        var s = 1; var d = sorted.last().minusDays(1)
        while (dates.contains(d)) { s++; d = d.minusDays(1) }
        s
    } else 0
    return current to best
}

private fun computeHIndexFromCounts(counts: List<NamePlayRow>): Int {
    val sorted = counts.map { it.plays }.sortedDescending()
    var h = 0
    sorted.forEachIndexed { i, c -> if (c >= i + 1) h = i + 1 }
    return h
}

fun buildDayOfWeekDist(plays: List<LoggedPlay>): List<Pair<String, Int>> {
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val counts = IntArray(7)
    plays.forEach { play ->
        runCatching {
            val dow = LocalDate.parse(play.date).dayOfWeek.value - 1
            counts[dow] += play.quantity.coerceAtLeast(1)
        }
    }
    return labels.zip(counts.toList())
}

fun countRecentPlays(plays: List<LoggedPlay>, days: Long): Int {
    val cutoff = LocalDate.now().minusDays(days - 1)
    return plays.sumOf { play ->
        val date = runCatching { LocalDate.parse(play.date) }.getOrNull()
        if (date != null && !date.isBefore(cutoff)) play.quantity.coerceAtLeast(1) else 0
    }
}

fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun buildInsights(
    plays: List<LoggedPlay>,
    topGames: List<GameStat>,
    topPlayers: List<PlayerStat>,
    hIndex: Int,
    currentStreak: Int,
    bestStreak: Int,
    longestSession: Pair<String, Int>?,
    hotPlayerStreak: Pair<String, Int>?,
    mostThisMonth: Pair<String, Int>?
): List<StatsInsight> {
    val recent7 = countRecentPlays(plays, 7)

    return buildList {
        if (currentStreak > 1) add(StatsInsight(
            icon = Icons.Default.LocalFireDepartment,
            value = "${currentStreak}d",
            label = "On a Roll",
            detail = "$currentStreak days in a row — keep it going",
            recentDaysFilter = when {
                currentStreak <= 7  -> 7
                currentStreak <= 30 -> 31
                else                -> 365
            }
        ))
        hotPlayerStreak?.let { (name, streak) ->
            add(StatsInsight(
                icon = Icons.Default.LocalFireDepartment,
                value = "${streak}W",
                label = "Hot Hand",
                detail = "$name is on a tear right now",
                playerFilter = name
            ))
        }
        if (recent7 > 2) add(StatsInsight(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            value = "$recent7",
            label = "This Week",
            detail = "$recent7 plays in the last 7 days",
            recentDaysFilter = 7
        ))
        mostThisMonth?.let { (name, count) ->
            add(StatsInsight(
                icon = Icons.Default.CalendarToday,
                value = "${count}×",
                label = "This Month",
                detail = name,
                recentDaysFilter = 31
            ))
        }
        if (bestStreak > 1) add(StatsInsight(
            icon = Icons.Default.EmojiEvents,
            value = "${bestStreak}d",
            label = "Personal Best",
            detail = "Your longest daily streak ever"
        ))
        longestSession?.let { (name, minutes) ->
            val gameId = plays.firstOrNull { it.gameName == name }?.gameId
            add(StatsInsight(
                icon = Icons.Default.Schedule,
                value = formatDuration(minutes),
                label = "Marathon",
                detail = name,
                gameFilter = gameId?.let { it to name }
            ))
        }
        topGames.firstOrNull()?.let { game ->
            add(StatsInsight(
                icon = Icons.Default.Star,
                value = "${game.plays}×",
                label = "Signature Game",
                detail = game.name,
                gameFilter = game.gameId to game.name
            ))
        }
        topPlayers.firstOrNull()?.let { player ->
            add(StatsInsight(
                icon = Icons.Default.Group,
                value = "${player.plays}",
                label = "Table Regular",
                detail = "${player.displayName} shows up the most",
                playerFilter = player.displayName
            ))
        }
        add(StatsInsight(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            value = "H-$hIndex",
            label = "Gamer Level",
            detail = if (hIndex > 0) "$hIndex games played at least $hIndex times each" else "Replay favorites to level up"
        ))
    }
}

// ── afterDate helper ──────────────────────────────────────────────────────────

fun StatsTimeRange.toAfterDate(): String {
    val now = LocalDate.now()
    return when (this) {
        StatsTimeRange.ALL       -> "0001-01-01"
        StatsTimeRange.THIS_YEAR -> "${now.year}-01-01"
        StatsTimeRange.THIS_MONTH -> "${now.year}-${now.monthValue.toString().padStart(2, '0')}-01"
        StatsTimeRange.LAST_30   -> now.minusDays(30).toString()
    }
}
