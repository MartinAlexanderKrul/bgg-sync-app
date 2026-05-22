package cz.nicolsburg.boardflow.core.navigation

import java.net.URLEncoder

object AppRoutes {
    const val SETTINGS = "settings"
    const val NEW_PLAY = "new_play"
    const val HISTORY = "history"
    const val COLLECTION = "collection"
    const val SYNC = "sync"
    const val PLAYERS = "players"
    const val SCAN = "scan/{gameId}/{gameName}"
    const val QR_IMPORT = "qr_import"
    const val PLAY_IMPORT = "play_import?data={data}"
    const val SESSION_IMPORT = "session_import?data={data}"
    const val LOG_PLAY = "log_play"
    const val CHALLENGES = "challenges"

    fun scan(gameId: Int, gameName: String): String =
        "scan/$gameId/${URLEncoder.encode(gameName, "UTF-8")}"

    fun playImport(data: String): String =
        "play_import?data=${URLEncoder.encode(data, "UTF-8")}"

    fun sessionImport(data: String): String =
        "session_import?data=${URLEncoder.encode(data, "UTF-8")}"
}
