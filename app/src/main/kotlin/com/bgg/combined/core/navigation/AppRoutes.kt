package com.bgg.combined.core.navigation

import java.net.URLEncoder

object AppRoutes {
    const val SETTINGS = "settings"
    const val NEW_PLAY = "new_play"
    const val HISTORY = "history"
    const val COLLECTION = "collection"
    const val SYNC = "sync"
    const val PLAYERS = "players"
    const val SCAN = "scan/{gameId}/{gameName}"
    const val LOG_PLAY = "log_play"

    fun scan(gameId: Int, gameName: String): String =
        "scan/$gameId/${URLEncoder.encode(gameName, "UTF-8")}"
}
