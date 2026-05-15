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

data class BggCredentials(
    val username: String,
    val password: String
)

data class LoggedPlay(
    val id: String,
    val gameId: Int,
    val gameName: String,
    val date: String,
    val players: List<PlayerResult>,
    val durationMinutes: Int,
    val location: String,
    val postedToBgg: Boolean,
    val comments: String = "",
    val quantity: Int = 1,
    val incomplete: Boolean = false,
    val nowInStats: Boolean = true
)

enum class StatsPlayScope(val label: String, val description: String) {
    ALL_PLAYS("All logged plays", "Every saved play appears in History stats."),
    COUNTED_ONLY("Counted plays only", "Only plays marked Count in stats are included.")
}

data class Player(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    val bggUsername: String = ""
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
    val gameId: Int,
    val gameName: String,
    val players: List<PlayerResult>,
    val location: String,
    val lastPlayTimestamp: Long
) {
    fun isActive(): Boolean =
        System.currentTimeMillis() - lastPlayTimestamp < 4L * 60 * 60 * 1000

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
