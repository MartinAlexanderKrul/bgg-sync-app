package com.bgg.combined

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bgg.combined.data.BggRepository
import com.bgg.combined.data.GeminiRepository
import com.bgg.combined.data.SecurePreferences

object Routes {
    const val SETTINGS   = "settings"
    const val NEW_PLAY   = "new_play"
    const val HISTORY    = "history"
    const val COLLECTION = "collection"
    const val SYNC       = "sync"
    const val PLAYERS    = "players"
    const val SCAN       = "scan/{gameId}/{gameName}"
    const val LOG_PLAY   = "log_play"

    fun scan(gameId: Int, gameName: String) =
        "scan/$gameId/${java.net.URLEncoder.encode(gameName, "UTF-8")}"
}

/** Minimal manual DI container. */
class AppContainer(context: Context) {
    val appContext         = context.applicationContext
    val securePreferences  = SecurePreferences(context)
    val bggRepository      = BggRepository()
    val geminiRepo         = GeminiRepository()

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
