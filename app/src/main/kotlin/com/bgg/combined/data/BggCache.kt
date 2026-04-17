package com.bgg.combined.data

import android.content.Context
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.File

class BggCache(context: Context) {

    private val cacheFile = File(context.filesDir, "bgg_collection.json")
    private val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    fun exists() = cacheFile.exists()

    fun save(games: List<BggApiClient.BggGame>) {
        mapper.writeValue(cacheFile, Envelope(games = games))
    }

    fun load(): List<BggApiClient.BggGame> {
        return mapper.readValue(cacheFile, Envelope::class.java).games
    }

    fun delete() { cacheFile.delete() }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Envelope(val games: List<BggApiClient.BggGame> = emptyList())
}
