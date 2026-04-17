package com.bgg.combined.core.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bgg.combined.data.BggRepository
import com.bgg.combined.data.GeminiRepository
import com.bgg.combined.data.SecurePreferences

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val securePreferences = SecurePreferences(appContext)
    val bggRepository = BggRepository()
    val geminiRepo = GeminiRepository()

    fun isOnline(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
