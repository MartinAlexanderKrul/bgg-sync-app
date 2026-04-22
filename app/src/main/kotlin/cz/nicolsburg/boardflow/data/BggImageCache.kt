package cz.nicolsburg.boardflow.data

import android.content.Context
import cz.nicolsburg.boardflow.model.GameItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object BggImageCache {

    private const val DIR = "bgg_thumbs"
    private const val MAX_CACHE_BYTES = 40L * 1024L * 1024L
    private const val MAX_CACHE_FILES = 500
    private val http = OkHttpClient()

    private fun cacheDir(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    fun localFile(context: Context, objectId: String): File = File(cacheDir(context), "$objectId.jpg")

    fun isCached(context: Context, objectId: String) = localFile(context, objectId).exists()

    fun download(context: Context, objectId: String, url: String): File? {
        prune(context)
        val file = localFile(context, objectId)
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        return try {
            val response = http.newCall(Request.Builder().url(url).build()).execute()
            if (response.code != 200) return null
            val bytes = response.body?.bytes() ?: return null
            file.writeBytes(bytes)
            file.setLastModified(System.currentTimeMillis())
            prune(context)
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
        cacheDir(context).listFiles()?.forEach { it.delete() }
    }

    fun prune(context: Context) {
        val files = cacheDir(context).listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList()
            ?: return

        var totalBytes = files.sumOf { it.length() }
        while (files.size > MAX_CACHE_FILES || totalBytes > MAX_CACHE_BYTES) {
            val oldest = files.removeFirstOrNull() ?: break
            totalBytes -= oldest.length()
            oldest.delete()
        }
    }
}
