package com.bgg.combined.data

import android.content.Context
import com.bgg.combined.model.GameItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object BggImageCache {

    private const val DIR = "bgg_thumbs"
    private val http = OkHttpClient()

    fun localFile(context: Context, objectId: String): File {
        val dir = File(context.filesDir, DIR).also { it.mkdirs() }
        return File(dir, "$objectId.jpg")
    }

    fun isCached(context: Context, objectId: String) = localFile(context, objectId).exists()

    fun download(context: Context, objectId: String, url: String): File? {
        val file = localFile(context, objectId)
        if (file.exists()) return file
        return try {
            val response = http.newCall(Request.Builder().url(url).build()).execute()
            if (response.code != 200) return null
            val bytes = response.body?.bytes() ?: return null
            file.writeBytes(bytes)
            file
        } catch (_: Exception) { null }
    }

    fun preloadAll(context: Context, games: List<GameItem>) {
        for (game in games) {
            val url = game.thumbnailUrl?.takeIf { it.isNotBlank() } ?: continue
            if (game.objectId.isBlank()) continue
            download(context, game.objectId, url)
        }
    }

    fun clearAll(context: Context) {
        File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
    }
}
