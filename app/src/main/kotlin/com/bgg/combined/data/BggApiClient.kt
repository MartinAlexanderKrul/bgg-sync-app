package com.bgg.combined.data

import com.bgg.combined.SyncConfig
import com.bgg.combined.model.GameItem
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class BggApiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var sessionCookies: String = ""
    @Volatile private var loggedIn = false

    fun loginIfNeeded(username: String, password: String) {
        if (loggedIn || password.isBlank()) return
        val body = FormBody.Builder()
            .add("credentials[username]", username)
            .add("credentials[password]", password)
            .build()
        val resp = http.newCall(
            Request.Builder()
                .url("https://boardgamegeek.com/login/api/v1")
                .header("User-Agent", "bgg-combined/1.0")
                .post(body)
                .build()
        ).execute()
        if (resp.code == 200 || resp.code == 204) {
            sessionCookies = resp.headers.values("Set-Cookie")
                .mapNotNull { it.split(";").firstOrNull()?.trim() }
                .joinToString("; ")
            loggedIn = true
        } else {
            throw RuntimeException("BGG login failed HTTP ${resp.code}")
        }
    }

    data class BggGame(
        val objectid: String,
        val objectname: String,
        val yearpublished: String,
        val minplayers: String,
        val maxplayers: String,
        val playingtime: String,
        val minplaytime: String,
        val maxplaytime: String,
        val rank: String,
        val average: String,
        val baverage: String,
        val numowned: String,
        val avgweight: String,
        val bggrecplayers: String,
        val bggbestplayers: String,
        val bggrecagerange: String,
        val bgglanguagedependence: String,
        val bggurl: String,
        val own: String = "",
        val wishlist: String = ""
    ) {
        fun asMap(): Map<String, String> = mapOf(
            "objectid"             to objectid,
            "objectname"           to objectname,
            "yearpublished"        to yearpublished,
            "minplayers"           to minplayers,
            "maxplayers"           to maxplayers,
            "playingtime"          to playingtime,
            "minplaytime"          to minplaytime,
            "maxplaytime"          to maxplaytime,
            "rank"                 to rank,
            "average"              to average,
            "baverage"             to baverage,
            "numowned"             to numowned,
            "avgweight"            to avgweight,
            "bggrecplayers"        to bggrecplayers,
            "bggbestplayers"       to bggbestplayers,
            "bggrecagerange"       to bggrecagerange,
            "bgglanguagedependence" to bgglanguagedependence,
            "bggurl"               to bggurl,
            "own"                  to own,
            "wishlist"             to wishlist
        )
    }

    fun fetchWishlistGameItems(username: String): List<GameItem> {
        loginIfNeeded(username, SyncConfig.BGG_PASSWORD)
        val body = fetchWithRetry("https://boardgamegeek.com/xmlapi2/collection?username=$username&wishlist=1&stats=1")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray()))
        doc.documentElement.normalize()
        val items = doc.getElementsByTagName("item")
        val result = mutableListOf<GameItem>()
        for (i in 0 until items.length) {
            val item  = items.item(i) as Element
            val id    = item.getAttribute("objectid")
            val name  = item.getElementsByTagName("name").item(0)?.textContent?.trim() ?: continue
            val thumb = item.getElementsByTagName("thumbnail").item(0)?.textContent?.trim()?.ifBlank { null }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            val stats  = item.getElementsByTagName("stats").item(0) as? Element
            val rating = (stats?.getElementsByTagName("average")?.item(0) as? Element)?.getAttribute("value")?.toDoubleOrNull()
            val rank   = (stats?.getElementsByTagName("rank")?.item(0) as? Element)?.getAttribute("value")?.toIntOrNull()
            result.add(GameItem(
                name = name, objectId = id, rank = rank, rating = rating, weight = null,
                minPlayers = stats?.getAttribute("minplayers")?.toIntOrNull(),
                maxPlayers = stats?.getAttribute("maxplayers")?.toIntOrNull(),
                playingTime = stats?.getAttribute("playingtime")?.toIntOrNull(),
                yearPublished = item.getElementsByTagName("yearpublished").item(0)?.textContent?.trim()?.toIntOrNull(),
                isOwned = false, isWishlisted = true, numPlays = 0, thumbnailUrl = thumb,
                shareUrl = null, language = null, bestPlayers = null, recommendedPlayers = null
            ))
        }
        return result
    }

    fun fetchOwnedThumbnails(username: String): Map<String, String> {
        loginIfNeeded(username, SyncConfig.BGG_PASSWORD)
        val body = fetchWithRetry("https://boardgamegeek.com/xmlapi2/collection?username=$username&own=1")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray()))
        doc.documentElement.normalize()
        val items = doc.getElementsByTagName("item")
        val result = mutableMapOf<String, String>()
        for (i in 0 until items.length) {
            val item  = items.item(i) as Element
            val id    = item.getAttribute("objectid")
            val thumb = item.getElementsByTagName("thumbnail").item(0)?.textContent?.trim()?.ifBlank { null }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            if (id.isNotBlank() && thumb != null) result[id] = thumb
        }
        return result
    }

    fun fetchCollection(username: String): List<BggGame> {
        loginIfNeeded(username, SyncConfig.BGG_PASSWORD)
        val url = "https://boardgamegeek.com/xmlapi2/collection?username=$username&own=1&stats=1"
        val body = fetchWithRetry(url)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray()))
        doc.documentElement.normalize()
        val items = doc.getElementsByTagName("item")
        val games = mutableListOf<BggGame>()
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id    = item.getAttribute("objectid")
            val name  = item.getElementsByTagName("name").item(0)?.textContent ?: ""
            val year  = item.getElementsByTagName("yearpublished").item(0)?.textContent ?: ""
            val stats = item.getElementsByTagName("stats").item(0) as? Element
            val minP  = stats?.getAttribute("minplayers") ?: ""
            val maxP  = stats?.getAttribute("maxplayers") ?: ""
            val time  = stats?.getAttribute("playingtime") ?: ""
            val minT  = stats?.getAttribute("minplaytime") ?: ""
            val maxT  = stats?.getAttribute("maxplaytime") ?: ""
            val numO  = stats?.getAttribute("numowned") ?: ""
            val avg   = (stats?.getElementsByTagName("average")?.item(0) as? Element)?.getAttribute("value") ?: ""
            val bavg  = (stats?.getElementsByTagName("bayesaverage")?.item(0) as? Element)?.getAttribute("value") ?: ""
            val rank  = (stats?.getElementsByTagName("rank")?.item(0) as? Element)?.getAttribute("value") ?: ""
            val status = item.getElementsByTagName("status").item(0) as? Element
            games.add(BggGame(
                objectid = id, objectname = name, yearpublished = year,
                minplayers = minP, maxplayers = maxP, playingtime = time, minplaytime = minT, maxplaytime = maxT,
                rank = rank, average = avg, baverage = bavg, numowned = numO, avgweight = "",
                bggrecplayers = "", bggbestplayers = "", bggrecagerange = "", bgglanguagedependence = "",
                bggurl = "https://boardgamegeek.com/boardgame/$id",
                own = status?.getAttribute("own") ?: "",
                wishlist = status?.getAttribute("wishlist") ?: ""
            ))
        }
        return games
    }

    private fun fetchWithRetry(url: String): String {
        repeat(5) {
            val req = Request.Builder().url(url)
                .apply { if (sessionCookies.isNotBlank()) header("Cookie", sessionCookies) }
                .build()
            val response = http.newCall(req).execute()
            when {
                response.code == 200 -> return response.body?.string() ?: ""
                response.code == 202 -> Thread.sleep(5_000)
                else -> throw RuntimeException("BGG API HTTP ${response.code}")
            }
        }
        throw RuntimeException("BGG API still queued after 5 retries")
    }
}
