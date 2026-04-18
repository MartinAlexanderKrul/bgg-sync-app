package com.bgg.combined.model

data class BggGame(
    val id: Int,
    val name: String,
    val yearPublished: String?,
    val thumbnailUrl: String?
)

data class PlayerResult(
    val name: String,
    val score: String,        // keep as String to handle edge cases like "DNF"
    val isWinner: Boolean
)

data class ExtractedPlay(
    val players: List<PlayerResult>,
    val rawText: String,        // full AI response for debugging / fallback display
    val date: String? = null   // date of the play, if available
)

data class BggCredentials(
    val username: String,
    val password: String
)

data class LoggedPlay(
    val id: String,             // UUID
    val gameId: Int,
    val gameName: String,
    val date: String,           // yyyy-MM-dd
    val players: List<PlayerResult>,
    val durationMinutes: Int,
    val location: String,
    val postedToBgg: Boolean,
    val comments: String = ""
)

data class Player(
    val id: String,
    val displayName: String,
    val aliases: List<String>,   // Known name variations (not including displayName)
    val bggUsername: String = "" // BGG account username for this player
)

/**
 * Related games for a selected game, fetched once from BGG's /thing endpoint.
 * [isExpansion] is true when the selected game itself is an expansion.
 * [baseGames] contains the games this expands (non-empty only when [isExpansion] = true).
 * [expansions] contains known expansions of this game.
 */
data class GameRelations(
    val isExpansion: Boolean,
    val baseGames: List<BggGame>,
    val expansions: List<BggGame>
)

/**
 * A single row from the Google Sheet, mapped to typed fields for display in
 * the Collection Browser screen.
 */
data class GameItem(
    val name: String,
    val objectId: String,
    val rank: Int?,
    val rating: Double?,        // BGG average (0–10)
    val weight: Double?,        // Complexity (1–5)
    val minPlayers: Int?,
    val maxPlayers: Int?,
    val playingTime: Int?,      // in minutes
    val yearPublished: Int?,
    val isOwned: Boolean,
    val isWishlisted: Boolean,
    val numPlays: Int?,
    val thumbnailUrl: String?,  // may be protocol-relative "//cf.geekdo-images.com/…"
    val shareUrl: String?,      // Google Drive folder link
    val language: String?,
    val bestPlayers: String?,        // raw BGG value e.g. "3", "2-4", "3, 4"
    val recommendedPlayers: String?  // bggrecplayers e.g. "2-5"
)

/** A saved spreadsheet entry (id + optional display name). */
data class SpreadsheetDetails(
    val id: String,
    val title: String,
    val firstSheetTitle: String,
    val webViewUrl: String? = null
)

/** A single structured log entry shown in the Sync screen. */
data class LogEntry(
    val name: String,           // game title or short header text
    val status: String,         // one-line status message
    val type: Type
) {
    enum class Type { HEADER, UPDATED, INSERTED, DONE, ERROR, INFO }

    val icon: String get() = when (type) {
        Type.HEADER   -> "📋"
        Type.UPDATED  -> "↻"
        Type.INSERTED -> "+"
        Type.DONE     -> "✓"
        Type.ERROR    -> "✗"
        Type.INFO     -> "·"
    }
}
