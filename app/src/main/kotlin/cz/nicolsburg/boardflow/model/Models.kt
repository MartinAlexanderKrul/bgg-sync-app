package cz.nicolsburg.boardflow.model

data class BggGame(
    val id: Int,
    val name: String,
    val yearPublished: String?,
    val thumbnailUrl: String?
)

data class PlayerResult(
    val name: String,
    val score: String,
    val isWinner: Boolean,
    val color: String = "",
    val rating: String = "",
    val isNew: Boolean = false
)

data class ExtractedPlay(
    val players: List<PlayerResult>,
    val rawText: String,
    val date: String? = null,
    val detectedGameTitle: String? = null,
    val detectedGameConfidence: Float? = null,
    val detectedScoringCategories: List<String> = emptyList(),
    val gameDetectionEvidence: String? = null,
    val isMalformed: Boolean = false,
    val modelUsed: String? = null
)

/** A ranked match produced by GameRecognitionEngine against the local collection. */
data class GameCandidate(
    val game: BggGame,
    val score: Float,
    val matchReason: String,
    /** "title", "category-template", or "none" — which signal drove the score. */
    val primarySignal: String = "title",
    /** Number of saved template categories that matched detected categories. */
    val templateOverlap: Int = 0
)

data class RecommendationPick(
    val game: BggGame,
    val reason: String
)

data class RecommendationLane(
    val id: String,
    val title: String,
    val subtitle: String,
    val picks: List<RecommendationPick>
)

data class BggCredentials(
    val username: String,
    val password: String
)

data class SessionMemory(
    val moods: List<String> = emptyList(),
    val momentType: String = "",
    val note: String = "",
    val quote: String = "",
    val chronicleLine: String = "",
    val chronicleSourceKey: String = "",
    val chronicleCreatedAt: Long? = null
)

data class LoggedPlay(
    val id: String,
    val gameId: Int,
    val gameName: String,
    val date: String,
    val playedAt: Long? = null,
    val sessionId: String? = null,
    val players: List<PlayerResult>,
    val durationMinutes: Int,
    val location: String,
    val postedToBgg: Boolean,
    val comments: String = "",
    val quantity: Int = 1,
    val incomplete: Boolean = false,
    val nowInStats: Boolean = true,
    val memory: SessionMemory? = null
)

data class PlaySession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val sessionDate: String,
    val location: String,
    val title: String = ""
)

data class SessionHubWinner(
    val playerName: String,
    val wins: Int
)

data class SessionHub(
    val anchorPlayId: String,
    val sessionId: String?,
    val title: String?,
    val date: String,
    val location: String,
    val plays: List<LoggedPlay>,
    val totalLoggedPlays: Int,
    val uniqueGames: Int,
    val uniquePlayerNames: List<String>,
    val totalDurationMinutes: Int,
    val winners: List<SessionHubWinner>,
    val moods: List<String>,
    val quotes: List<String>
) {
    val anchorPlay: LoggedPlay
        get() = plays.firstOrNull { it.id == anchorPlayId } ?: plays.first()
}

fun List<LoggedPlay>.deriveSessionHub(anchor: LoggedPlay, sessionTitle: String? = null): SessionHub {
    val explicitSessionId = anchor.sessionId?.takeIf { it.isNotBlank() }
    val anchorDate = anchor.date
    val anchorLocation = anchor.location.normalizeSessionLocation()
    val anchorPlayers = anchor.normalizedSessionPlayers()

    val sessionPlays = filter { play ->
        when {
            explicitSessionId != null -> play.sessionId == explicitSessionId
            else -> play.date == anchorDate && play.belongsToDerivedSession(
                anchorId = anchor.id,
                anchorLocation = anchorLocation,
                anchorPlayers = anchorPlayers
            )
        }
    }.ifEmpty { listOf(anchor) }

    val uniquePlayerNames = linkedSetOf<String>().apply {
        sessionPlays.forEach { play ->
            play.players.forEach { player ->
                val trimmed = player.name.trim()
                if (trimmed.isNotBlank()) add(trimmed)
            }
        }
    }.toList()

    val winnerCounts = linkedMapOf<String, Int>()
    sessionPlays.forEach { play ->
        play.players.filter { it.isWinner }.forEach { winner ->
            val name = winner.name.trim()
            if (name.isNotBlank()) {
                winnerCounts[name] = (winnerCounts[name] ?: 0) + 1
            }
        }
    }

    val moods = linkedSetOf<String>().apply {
        sessionPlays.forEach { play ->
            play.memory?.moods?.forEach { mood ->
                val trimmed = mood.trim()
                if (trimmed.isNotBlank()) add(trimmed)
            }
        }
    }.toList()

    val quotes = linkedSetOf<String>().apply {
        sessionPlays.forEach { play ->
            play.memory?.quote?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }.toList()

    return SessionHub(
        anchorPlayId = anchor.id,
        sessionId = explicitSessionId,
        title = sessionTitle?.trim()?.ifBlank { null },
        date = anchorDate,
        location = anchor.location.trim(),
        plays = sessionPlays,
        totalLoggedPlays = sessionPlays.sumOf { it.quantity.coerceAtLeast(1) },
        uniqueGames = sessionPlays.map { it.gameId }.distinct().size,
        uniquePlayerNames = uniquePlayerNames,
        totalDurationMinutes = sessionPlays.sumOf { it.durationMinutes.coerceAtLeast(0) },
        winners = winnerCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { SessionHubWinner(playerName = it.key, wins = it.value) },
        moods = moods,
        quotes = quotes
    )
}

private fun LoggedPlay.belongsToDerivedSession(
    anchorId: String,
    anchorLocation: String,
    anchorPlayers: Set<String>
): Boolean {
    if (id == anchorId) return true

    val playPlayers = normalizedSessionPlayers()
    val sharedPlayers = playPlayers.intersect(anchorPlayers).size
    val playerThreshold = when {
        anchorPlayers.isEmpty() || playPlayers.isEmpty() -> 0
        anchorPlayers.size == 1 || playPlayers.size == 1 -> 1
        else -> 2
    }

    val playLocation = location.normalizeSessionLocation()
    val bothHaveLocation = anchorLocation.isNotBlank() && playLocation.isNotBlank()

    return when {
        bothHaveLocation -> playLocation == anchorLocation && sharedPlayers >= playerThreshold
        anchorLocation.isBlank() && playLocation.isBlank() -> sharedPlayers >= playerThreshold
        else -> false
    }
}

private fun LoggedPlay.normalizedSessionPlayers(): Set<String> =
    players.mapNotNull { player ->
        player.name.trim().lowercase().takeIf { it.isNotBlank() }
    }.toSet()

private fun String.normalizeSessionLocation(): String =
    trim().lowercase()

fun String.trimMemorySuffix(): String {
    fun String.isMemoryLine() = startsWith("\$\$mood:") || startsWith("\$\$quote:") || isBlank()
    val idx = lastIndexOf("\n\n")
    if (idx == -1) {
        return if (lines().all { it.isMemoryLine() }) "" else trimEnd()
    }
    val suffix = substring(idx + 2)
    return if (suffix.lines().all { it.isMemoryLine() }) substring(0, idx).trimEnd() else trimEnd()
}

@Deprecated("Use trimMemorySuffix", ReplaceWith("trimMemorySuffix()"))
fun String.trimMoodSuffix(): String = trimMemorySuffix()

enum class StatsPlayScope(val label: String, val description: String) {
    ALL_PLAYS("All logged plays", "Every saved play appears in History stats."),
    COUNTED_ONLY("Counted plays only", "Only plays marked Count in stats are included.")
}

data class Player(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    val bggUsername: String = "",
    val lastPlayedAt: Long? = null,
    val isHidden: Boolean = false
)

data class GameRelations(
    val isExpansion: Boolean,
    val baseGames: List<BggGame>,
    val expansions: List<BggGame>
)

/**
 * Merged collection record built from spreadsheet values plus live BGG enrichment.
 *
 * Canonical play count is always BGG numplays.
 */
data class GameItem(
    val identity: Identity,
    val stats: Stats,
    val players: Players,
    val ownership: Ownership,
    val sleeves: Sleeves,
    val media: Media,
    val links: Links,
    val sources: Sources,
    val lastCachedAt: Long = System.currentTimeMillis()
) {
    data class Identity(
        val objectId: String,
        val name: String
    )

    data class Stats(
        val rank: Int?,
        val averageRating: Double?,
        val bayesAverage: Double?,
        val weight: Double?,
        val yearPublished: Int?,
        val playingTime: Int?,
        val minPlayTime: Int?,
        val maxPlayTime: Int?,
        val numOwned: Int?,
        val languageDependence: String?,
        val language: String?
    )

    data class Players(
        val minPlayers: Int?,
        val maxPlayers: Int?,
        val bestPlayers: String?,
        val recommendedPlayers: String?,
        val notRecommendedPlayers: String?,
        val recommendedAge: String?
    )

    data class Ownership(
        val isOwned: Boolean,
        val isWishlisted: Boolean,
        /**
         * Canonical play count from BGG numplays.
         */
        val bggPlayCount: Int?
    )

    data class Sleeves(
        val status: SleeveStatus = SleeveStatus.UNKNOWN,
        val cardSets: List<CardSet> = emptyList(),
        val sourceUrl: String? = null,
        val note: String? = null,
        val lastFetchedAt: Long? = null
    ) {
        data class CardSet(
            val label: String,
            val count: Int?,
            val size: String?,
            val notes: String? = null
        )
    }

    enum class SleeveStatus {
        UNKNOWN,
        FOUND,
        MISSING,
        ERROR
    }

    data class Media(
        val thumbnailUrl: String?
    )

    data class Links(
        val bggUrl: String?,
        val driveUrl: String?,
        val qrImageUrl: String?
    )

    data class Sources(
        val spreadsheetValues: Map<String, String>,
        val bggValues: Map<String, String>
    )

    val name: String get() = identity.name
    val objectId: String get() = identity.objectId
    val rank: Int? get() = stats.rank
    val rating: Double? get() = stats.averageRating
    val bayesAverage: Double? get() = stats.bayesAverage
    val weight: Double? get() = stats.weight
    val yearPublished: Int? get() = stats.yearPublished
    val playingTime: Int? get() = stats.playingTime
    val minPlayTime: Int? get() = stats.minPlayTime
    val maxPlayTime: Int? get() = stats.maxPlayTime
    val numOwned: Int? get() = stats.numOwned
    val languageDependence: String? get() = stats.languageDependence
    val language: String? get() = stats.language
    val minPlayers: Int? get() = players.minPlayers
    val maxPlayers: Int? get() = players.maxPlayers
    val bestPlayers: String? get() = players.bestPlayers
    val recommendedPlayers: String? get() = players.recommendedPlayers
    val notRecommendedPlayers: String? get() = players.notRecommendedPlayers
    val recommendedAge: String? get() = players.recommendedAge
    val isOwned: Boolean get() = ownership.isOwned
    val isWishlisted: Boolean get() = ownership.isWishlisted
    val numPlays: Int? get() = ownership.bggPlayCount
    val sleeveStatus: SleeveStatus get() = sleeves.status
    val sleeveCardSets: List<Sleeves.CardSet> get() = sleeves.cardSets
    val sleeveSourceUrl: String? get() = sleeves.sourceUrl
    val sleeveNote: String? get() = sleeves.note
    val sleevesLastFetchedAt: Long? get() = sleeves.lastFetchedAt
    val thumbnailUrl: String? get() = media.thumbnailUrl
    val bggUrl: String? get() = links.bggUrl
    val shareUrl: String? get() = links.driveUrl
    val qrImageUrl: String? get() = links.qrImageUrl
    val spreadsheetValues: Map<String, String> get() = sources.spreadsheetValues
    val bggValues: Map<String, String> get() = sources.bggValues

    fun withSleeves(sleeves: Sleeves): GameItem =
        copy(sleeves = sleeves)
}

data class SpreadsheetDetails(
    val id: String,
    val title: String,
    val firstSheetTitle: String,
    val webViewUrl: String? = null
)

data class SessionContext(
    val sessionId: String,
    val gameId: Int,
    val gameName: String,
    val players: List<PlayerResult>,
    val location: String,
    val title: String = "",
    val startedAt: Long,
    val lastPlayTimestamp: Long
) {
    fun isActive(): Boolean =
        System.currentTimeMillis() - lastPlayTimestamp < 6L * 60 * 60 * 1000

    fun isRecent(): Boolean =
        System.currentTimeMillis() - lastPlayTimestamp < 60L * 60 * 1000
}

enum class InsightRarity(val label: String, val sortWeight: Int) {
    COMMON("Moment", 0),
    NOTABLE("Notable", 1),
    RARE("Landmark", 2),
    EPIC("Chronicle", 3),
    LEGENDARY("Legacy", 4)
}

sealed class RecordMoment {
    data class FirstWin(val playerName: String, val gameName: String) : RecordMoment()
    data class NewHighScore(val playerName: String, val gameName: String) : RecordMoment()
    data class WinStreak(val playerName: String, val streakLength: Int) : RecordMoment()

    val rarity: InsightRarity
        get() = when (this) {
            is FirstWin    -> InsightRarity.NOTABLE
            is NewHighScore -> InsightRarity.NOTABLE
            is WinStreak   -> when {
                streakLength >= 7 -> InsightRarity.EPIC
                streakLength >= 4 -> InsightRarity.RARE
                else              -> InsightRarity.NOTABLE
            }
        }

    /** Emoji version — used in the post-log PostSaveCard celebration beat. */
    val displayText: String
        get() = when (this) {
            is FirstWin    -> "🎉 $playerName finally wins one."
            is NewHighScore -> "🏆 A new personal best for $playerName. Log it."
            is WinStreak   -> "🔥 $playerName has won ${streakLength} in a row. The table has noticed."
        }

    /** No-emoji version — used in historical insight strips (PlayDetailsDialog). */
    val stripText: String
        get() = when (this) {
            is FirstWin    -> "$playerName wins it for the first time."
            is NewHighScore -> "New personal best for $playerName."
            is WinStreak   -> "$playerName is on a ${streakLength}-win streak."
        }
}

data class PlayerRecognitionHint(
    val scannedNameNormalized: String,
    val confirmedRosterPlayerId: String,
    val playerDisplayName: String,
    val timesConfirmed: Int,
    val lastConfirmedAt: Long
)

data class GameRecognitionHint(
    val gameObjectId: String,
    val gameName: String,
    val normalizedTitles: List<String>,
    val normalizedCategories: List<String>,
    val confirmedAt: Long,
    val timesConfirmed: Int
)

sealed class ScanRecognitionResult {
    /** Auto-switch happened; game was confirmed without user interaction. */
    data class AutoSwitched(val gameName: String) : ScanRecognitionResult()
    /** Gemini detected a title but it was not found in the local collection. */
    data class NoCollectionMatch(val detectedTitle: String) : ScanRecognitionResult()
    /** Gemini could not detect the game, or confidence was too low. */
    object LowConfidence : ScanRecognitionResult()
}

enum class ChallengeType(val label: String) {
    PLAY_N_TIMES("Play N plays"),
    PLAY_SPECIFIC_GAME("Play a game N times"),
    PLAY_N_DISTINCT("Play N different games"),
    PLAYER_WIN_STREAK("Reach a player win streak"),
    PLAY_WITH_GROUP_N_TIMES("Play with a group N times"),
    PLAY_STREAK("Stay consistent week over week"),
    PLAY_N_UNPLAYED("Play unplayed owned games")
}

enum class ChallengeStatus {
    ACTIVE,
    PAUSED,
    ARCHIVED
}

data class Challenge(
    val id: String,
    val title: String,
    val type: ChallengeType,
    val targetCount: Int,
    val gameId: Int? = null,
    val gameName: String? = null,
    val playerIds: List<String> = emptyList(),
    val playerNames: List<String> = emptyList(),
    val startDate: String? = null,
    val endDate: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val streakPeriod: String? = null,
    val status: ChallengeStatus = ChallengeStatus.ACTIVE
)

data class ChallengeProgress(
    val challenge: Challenge,
    val currentCount: Int,
    val goalCount: Int,
    val remainingText: String? = null
) {
    val isComplete: Boolean get() = currentCount >= goalCount
    val isFailed: Boolean get() {
        if (isComplete) return false
        val end = challenge.endDate ?: return false
        return runCatching { java.time.LocalDate.parse(end) }.getOrNull()
            ?.isBefore(java.time.LocalDate.now()) == true
    }
    val isArchived: Boolean get() = challenge.status == ChallengeStatus.ARCHIVED
    val isPaused: Boolean get() = challenge.status == ChallengeStatus.PAUSED
    val isActive: Boolean get() = challenge.status == ChallengeStatus.ACTIVE && !isComplete && !isFailed
    val fraction: Float
        get() = (currentCount.toFloat() / goalCount.coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class LogPlayPrefill(
    val location: String,
    val durationSuggestion: String = ""
)

data class LogEntry(
    val name: String,
    val status: String,
    val type: Type
) {
    enum class Type { HEADER, UPDATED, INSERTED, DONE, ERROR, INFO }

    val icon: String
        get() = when (type) {
            Type.HEADER -> "list"
            Type.UPDATED -> "sync"
            Type.INSERTED -> "+"
            Type.DONE -> "done"
            Type.ERROR -> "error"
            Type.INFO -> "info"
        }
}
