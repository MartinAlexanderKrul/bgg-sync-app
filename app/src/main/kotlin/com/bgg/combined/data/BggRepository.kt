package com.bgg.combined.data

import android.util.Log
import com.bgg.combined.model.BggGame
import com.bgg.combined.model.BggCredentials
import com.bgg.combined.model.LoggedPlay
import com.bgg.combined.model.PlayerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.LocalDate

class BggRepository {

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val valid = cookies.filter { it.expiresAt > System.currentTimeMillis() }
            cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
                valid.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                    add(newCookie)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host] ?: emptyList()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    suspend fun searchGames(query: String): Result<List<BggGame>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://boardgamegeek.com/xmlapi2/search?query=${
                java.net.URLEncoder.encode(query, "UTF-8")
            }&type=boardgame&exact=0"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string() ?: return@runCatching emptyList()
            parseSearchResults(body)
        }
    }

    suspend fun getUserCollection(username: String): Result<List<BggGame>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://boardgamegeek.com/xmlapi2/collection?username=${
                java.net.URLEncoder.encode(username, "UTF-8")
            }&own=1&subtype=boardgame&stats=1&excludesubtype=boardgameexpansion"
            var attempts = 0
            val maxAttempts = 3
            while (attempts < maxAttempts) {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string() ?: return@runCatching emptyList()
                when (response.code) {
                    200 -> return@runCatching parseCollectionResults(body)
                    202 -> { attempts++; if (attempts < maxAttempts) kotlinx.coroutines.delay(2000) else throw Exception("Collection still processing. Please try again in a moment.") }
                    401 -> throw Exception("Cannot access collection for '$username'. The profile may be private.")
                    404 -> throw Exception("User '$username' not found on BGG.")
                    else -> throw Exception("Failed to load collection: HTTP ${response.code}")
                }
            }
            emptyList()
        }
    }

    suspend fun getUserCollectionAuthenticated(credentials: BggCredentials): Result<List<BggGame>> = withContext(Dispatchers.IO) {
        runCatching {
            login(credentials).getOrThrow()
            val url = "https://boardgamegeek.com/xmlapi2/collection?username=${
                java.net.URLEncoder.encode(credentials.username, "UTF-8")
            }&own=1&subtype=boardgame"
            var attempts = 0
            val maxAttempts = 3
            while (attempts < maxAttempts) {
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string() ?: return@runCatching emptyList()
                when (response.code) {
                    200 -> return@runCatching parseCollectionResults(body)
                    202 -> { attempts++; if (attempts < maxAttempts) kotlinx.coroutines.delay(2000) else throw Exception("Collection still processing. Please try again in a moment.") }
                    401 -> throw Exception("Authentication failed. Please check your BGG credentials in Settings.")
                    404 -> throw Exception("User '${credentials.username}' not found on BGG.")
                    else -> throw Exception("Failed to load collection: HTTP ${response.code}")
                }
            }
            emptyList()
        }
    }

    suspend fun login(credentials: BggCredentials): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = """{"credentials":{"username":"${credentials.username}","password":"${credentials.password}"}}"""
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://boardgamegeek.com/login/api/v1")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Login failed: HTTP ${response.code}")
            val hasCookies = cookieStore["boardgamegeek.com"]?.any { it.name == "SessionID" } == true
            if (!hasCookies) throw Exception("Login failed: no session cookie received")
        }
    }

    suspend fun logPlay(
        gameId: Int,
        date: LocalDate,
        players: List<PlayerResult>,
        playerBggUsernames: Map<Int, String> = emptyMap(),
        durationMinutes: Int = 0,
        location: String = "",
        comments: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder().apply {
                add("ajax", "1"); add("action", "save"); add("objecttype", "thing")
                add("objectid", gameId.toString()); add("playdate", date.toString())
                add("dateinput", date.toString()); add("length", durationMinutes.toString())
                add("location", location); add("comments", comments)
                add("quantity", "1"); add("incomplete", "0"); add("nowinstats", "0")
                players.forEachIndexed { index, player ->
                    add("players[$index][name]", player.name)
                    add("players[$index][score]", player.score)
                    add("players[$index][win]", if (player.isWinner) "1" else "0")
                    add("players[$index][new]", "0")
                    add("players[$index][rating]", "0")
                    add("players[$index][selected]", "0")
                    val bggUsername = playerBggUsernames[index]
                    if (!bggUsername.isNullOrBlank()) add("players[$index][username]", bggUsername)
                }
            }.build()
            val request = Request.Builder()
                .url("https://boardgamegeek.com/geekplay.php")
                .post(formBody)
                .addHeader("Referer", "https://boardgamegeek.com")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Failed to log play: HTTP ${response.code}")
            if (!responseBody.contains("playid") && !responseBody.contains("\"error\":null"))
                throw Exception("Unexpected BGG response: $responseBody")
        }
    }

    suspend fun deletePlay(playId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deleteRequest = linkedMapOf(
                "ajax" to "1",
                "action" to "delete",
                "version" to "2",
                "objecttype" to "thing",
                "playid" to playId
            )

            val initialBody = executeGeekplayPost(deleteRequest)
            Log.i(
                "BggRepository",
                "Delete play confirmation step: body=${initialBody.take(200)}"
            )

            if (initialBody.contains("Play date required", ignoreCase = true)) {
                throw Exception("BGG requested additional play data before delete confirmation")
            }

            val confirmFields = parseHiddenInputs(initialBody).toMutableMap()
            if (confirmFields.isEmpty()) {
                throw Exception("BGG delete confirmation form did not include hidden fields")
            }
            confirmFields["ajax"] = "1"
            confirmFields["final"] = "1"

            val confirmBody = executeGeekplayPost(confirmFields)
            Log.i(
                "BggRepository",
                "Delete play confirm step: body=${confirmBody.take(200)}"
            )
            val accepted = confirmBody.contains("\"error\":null")
                || confirmBody.contains("\"error\": false")
                || confirmBody.contains("deleted", ignoreCase = true)
                || confirmBody.contains("success", ignoreCase = true)
                || confirmBody.contains("play has been deleted", ignoreCase = true)
                || confirmBody.isBlank()
            if (!accepted) {
                throw Exception("Unexpected BGG confirm-delete response: ${confirmBody.take(160)}")
            }
        }
    }

    private fun executeGeekplayPost(fields: Map<String, String>): String {
        val request = Request.Builder()
            .url("https://boardgamegeek.com/geekplay.php")
            .post(
                FormBody.Builder().apply {
                    fields.forEach { (key, value) -> add(key, value) }
                }.build()
            )
            .addHeader("Referer", "https://boardgamegeek.com")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw Exception("BGG geekplay request failed: HTTP ${response.code}")
        }
        return responseBody
    }

    private fun parseHiddenInputs(html: String): Map<String, String> {
        val matches = Regex(
            """<input[^>]*type=["']hidden["'][^>]*name=["']([^"']+)["'][^>]*value=["']([^"']*)["'][^>]*>""",
            RegexOption.IGNORE_CASE
        ).findAll(html)
        return buildMap {
            matches.forEach { match ->
                put(match.groupValues[1], match.groupValues[2])
            }
        }
    }

    suspend fun getPlays(username: String): Result<List<LoggedPlay>> = withContext(Dispatchers.IO) {
        runCatching {
            val allPlays = mutableListOf<LoggedPlay>()
            var page = 1; val maxPages = 10
            while (page <= maxPages) {
                val url = "https://boardgamegeek.com/xmlapi2/plays?username=${
                    java.net.URLEncoder.encode(username, "UTF-8")
                }&type=thing&page=$page"
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) {
                    if (page == 1) throw Exception("Failed to fetch plays: HTTP ${response.code}")
                    break
                }
                val body = response.body?.string() ?: break
                val (plays, total) = parsePlays(body)
                allPlays.addAll(plays)
                if (plays.isEmpty() || allPlays.size >= total) break
                page++
            }
            allPlays
        }
    }

    private fun parsePlays(xml: String): Pair<List<LoggedPlay>, Int> {
        val plays = mutableListOf<LoggedPlay>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var total = 0; var playId: String? = null; var date = ""; var length = 0
        var location = ""; var gameName: String? = null; var gameId: Int? = null
        var players = mutableListOf<PlayerResult>(); var insidePlayers = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "plays"   -> total = parser.getAttributeValue(null, "total")?.toIntOrNull() ?: 0
                    "play"    -> { playId = parser.getAttributeValue(null, "id"); date = parser.getAttributeValue(null, "date") ?: ""; length = parser.getAttributeValue(null, "length")?.toIntOrNull() ?: 0; location = parser.getAttributeValue(null, "location") ?: ""; gameName = null; gameId = null; players = mutableListOf() }
                    "item"    -> { gameName = parser.getAttributeValue(null, "name"); gameId = parser.getAttributeValue(null, "objectid")?.toIntOrNull() }
                    "players" -> insidePlayers = true
                    "player"  -> if (insidePlayers) { val name = parser.getAttributeValue(null, "name") ?: ""; val score = parser.getAttributeValue(null, "score") ?: ""; val win = parser.getAttributeValue(null, "win") == "1"; if (name.isNotBlank()) players.add(PlayerResult(name, score, win)) }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "players" -> insidePlayers = false
                    "play"    -> if (playId != null && gameName != null && gameId != null) { plays.add(LoggedPlay(id = playId!!, gameId = gameId!!, gameName = gameName!!, date = date, players = players.toList(), durationMinutes = length, location = location, postedToBgg = true)) }
                }
            }
            event = parser.next()
        }
        return Pair(plays, total)
    }

    private fun parseSearchResults(xml: String): List<BggGame> {
        val games = mutableListOf<BggGame>()
        val factory = XmlPullParserFactory.newInstance(); val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var currentId: Int? = null; var currentName: String? = null; var currentYear: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> { currentId = parser.getAttributeValue(null, "id")?.toIntOrNull(); currentName = null; currentYear = null }
                    "name" -> { if (parser.getAttributeValue(null, "type") == "primary") currentName = parser.getAttributeValue(null, "value") }
                    "yearpublished" -> { currentYear = parser.getAttributeValue(null, "value") }
                }
                XmlPullParser.END_TAG -> { if (parser.name == "item" && currentId != null && currentName != null) { games.add(BggGame(id = currentId!!, name = currentName!!, yearPublished = currentYear, thumbnailUrl = null)) } }
            }
            event = parser.next()
        }
        return games.sortedByDescending { it.yearPublished ?: "0" }.take(20)
    }

    private fun parseCollectionResults(xml: String): List<BggGame> {
        val games = mutableListOf<BggGame>()
        val factory = XmlPullParserFactory.newInstance(); val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var currentId: Int? = null; var currentName: String? = null; var currentYear: String? = null; var insideName = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> { currentId = parser.getAttributeValue(null, "objectid")?.toIntOrNull(); currentName = null; currentYear = null }
                    "name" -> { insideName = true }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()
                    if (text?.isNotBlank() == true) {
                        if (insideName && currentName == null) currentName = text
                        else if (currentYear == null && text.toIntOrNull() != null) currentYear = text
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "name" -> insideName = false
                    "item" -> { if (currentId != null && currentName != null) { games.add(BggGame(id = currentId!!, name = currentName!!, yearPublished = currentYear, thumbnailUrl = null)) }; currentId = null; currentName = null; currentYear = null }
                }
            }
            event = parser.next()
        }
        return games
    }
}
