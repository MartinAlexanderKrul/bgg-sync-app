package cz.nicolsburg.boardflow.data

import android.content.Context
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.File

class BggCache(context: Context) {

    private val filesDir = context.filesDir
    private val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    fun exists(username: String): Boolean = cacheFile(username).exists() || legacyCacheFile().exists()

    fun save(username: String, games: List<BggApiClient.BggGame>) {
        mapper.writeValue(cacheFile(username), Envelope(games = games))
    }

    fun load(username: String): List<BggApiClient.BggGame> {
        val scoped = cacheFile(username)
        val source = when {
            scoped.exists() -> scoped
            legacyCacheFile().exists() -> legacyCacheFile()
            else -> return emptyList()
        }
        return mapper.readValue(source, Envelope::class.java).games
    }

    fun delete(username: String) {
        cacheFile(username).delete()
    }

    private fun cacheFile(username: String): File {
        val safe = username.trim().lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .ifBlank { "default" }
        return File(filesDir, "bgg_collection_$safe.json")
    }

    private fun legacyCacheFile(): File = File(filesDir, "bgg_collection.json")

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Envelope(val games: List<BggApiClient.BggGame> = emptyList())
}
