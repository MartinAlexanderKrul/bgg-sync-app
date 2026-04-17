package com.bgg.combined

/**
 * Central configuration constants for the Google Sheets sync functionality.
 * Edit BGG_USERNAME and sheet column indices to match your setup.
 */
object SyncConfig {
    const val APP_NAME        = "bgg-combined"
    const val BGG_USERNAME    = "Nicolsburg"
    /** BGG password — injected at build time from the BGG_PASSWORD environment variable. */
    val BGG_PASSWORD: String get() = BuildConfig.BGG_PASSWORD

    const val SHEET_TAB_NAME   = "GAMES"
    const val HEADER_ROW_INDEX = 0
    const val COL_GAME_NAME    = 0   // column A
    const val COL_SHARE_URL    = 28  // column AC
    const val COL_QR_IMAGE     = 29  // column AD

    val OAUTH_SCOPES = listOf(
        "https://www.googleapis.com/auth/drive",
        "https://www.googleapis.com/auth/spreadsheets"
    )

    val PROTECTED_COLUMNS = setOf("language")

    val NUMERIC_COLUMNS = setOf(
        "objectid", "collid", "imageid", "publisherid",
        "own", "fortrade", "want", "wanttobuy", "wanttoplay",
        "prevowned", "preordered", "wishlist", "wishlistpriority",
        "numplays", "quantity",
        "minplayers", "maxplayers",
        "playingtime", "minplaytime", "maxplaytime",
        "yearpublished", "year",
        "rank", "numowned",
        "rating", "baverage", "average", "score", "communityrating",
        "avgweight", "weight"
    )
}
