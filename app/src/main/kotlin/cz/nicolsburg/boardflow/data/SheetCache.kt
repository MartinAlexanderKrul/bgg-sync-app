package cz.nicolsburg.boardflow.data

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import cz.nicolsburg.boardflow.model.GameItem
import java.io.File

class SheetCache(private val context: Context) {
    private val cacheFile: File
        get() = File(context.filesDir, "sheet_collection_cache.json")

    fun save(games: List<GameItem>) {
        val mapper = jacksonObjectMapper()
        cacheFile.writeText(mapper.writeValueAsString(games))
    }

    fun load(): List<GameItem> {
        val mapper = jacksonObjectMapper()
        return if (cacheFile.exists()) {
            try {
                mapper.readValue(cacheFile)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun exists(): Boolean = cacheFile.exists()

    fun delete() {
        if (cacheFile.exists()) cacheFile.delete()
    }
}
