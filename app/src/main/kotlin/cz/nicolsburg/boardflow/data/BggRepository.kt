package cz.nicolsburg.boardflow.data

import android.util.Log
import cz.nicolsburg.boardflow.BuildConfig
import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.BggCredentials
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.PlayerResult
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
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.LocalDate

class BggRepository {

    companion object {
        private const val TAG = "BggRepository"
    }

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
        .addInterceptor(
            HttpLoggingInterceptor { Log.d(TAG, it.replace('\n', ' ')) }.apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    suspend fun searchGames(query: String, xmlApiToken: String, exact: Boolean = false): Result<List<BggGame>> = withContext(Dispatchers.IO) {
        runCatching {
            if (xmlApiToken.isBlank()) {
                return@runCatching emptyList()
            }
            val url = "https://boardgamegeek.com/xmlapi2/search?query=${
                java.net.URLEncoder.encode(query, "UTF-8")
            }&type=boardgame,boardgameexpansion&exact=${if (exact) 1 else 0}"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $xmlApiToken")
                .build()
            val response = client.newCall(request).execute()
            if (response.code == 401) {
                return@runCatching emptyList()
            }
            if (!response.isSuccessful) {
                return@runCatching emptyList()
            }
            val body = response.body?.string() ?: return@runCatching emptyList()
            runCatching { parseSearchResults(body) }.getOrDefault(emptyList())
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
        runCatching<Unit> {
            val json = JSONObject()
                .put(
                    "credentials",
                    JSONObject()
                        .put("username", credentials.username)
                        .put("password", credentials.password)
                )
                .toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://boardgamegeek.com/login/api/v1")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Login failed: HTTP ${response.code}")
            val hasCookies = cookieStore["boardgamegeek.com"]?.any { it.name == "SessionID" } == true
            if (!hasCookies) throw Exception("Login failed: no session cookie received")
            Log.i(TAG, "Login success for ${credentials.username}")
        }
    }

    suspend fun logPlay(
        gameId: Int,
        date: LocalDate,
        players: List<PlayerResult>,
        playerBggUsernames: Map<Int, String> = emptyMap(),
        durationMinutes: Int = 0,
        location: String = "",
        comments: String = "",
        quantity: Int = 1,
        incomplete: Boolean = false,
        nowInStats: Boolean = true,
        playId: String? = null
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder().apply {
                add("ajax", "1"); add("action", "save"); add("version", "2"); add("objecttype", "thing")
                add("objectid", gameId.toString()); add("playdate", date.toString())
                add("dateinput", date.toString()); add("length", durationMinutes.toString())
                add("location", location); add("comments", comments)
                add("quantity", quantity.coerceAtLeast(1).toString())
                add("incomplete", if (incomplete) "1" else "0")
                add("nowinstats", if (nowInStats) "1" else "0")
                if (playId != null) add("playid", playId)
                players.forEachIndexed { index, player ->
                    val position = index + 1
                    val bggUsername = playerBggUsernames[index]
                    add("players[$position][name]", player.name)
                    add("players[$position][position]", position.toString())
                    add("players[$position][score]", player.score)
                    add("players[$position][color]", player.color)
                    add("players[$position][win]", if (player.isWinner) "1" else "0")
                    add("players[$position][new]", if (player.isNew) "1" else "0")
                    add("players[$position][rating]", player.rating.takeUnless { it.isBlank() || it == "N/A" } ?: "0")
                    add("players[$position][selected]", if (bggUsername.isNullOrBlank()) "0" else "1")
                    if (!bggUsername.isNullOrBlank()) add("players[$position][username]", bggUsername)
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
            val savedPlayId = extractSavedPlayId(responseBody) ?: playId
            if (savedPlayId == null && !looksLikePlaySaveAccepted(responseBody)) {
                throw Exception("Unexpected BGG response: $responseBody")
            }
            Log.i(TAG, "Play logged: gameId=$gameId date=$date players=${players.size} savedPlayId=$savedPlayId")
            savedPlayId
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
                TAG,
                "Delete play confirmation step: body=${initialBody.take(200)}"
            )

            if (initialBody.contains("Play date required", ignoreCase = true)) {
                throw Exception("BGG requested additional play data before delete confirmation")
            }

            val confirmFields = parseHiddenInputs(initialBody).toMutableMap()
            if (confirmFields.isEmpty() && looksLikeDeleteAccepted(initialBody)) {
                return@runCatching
            }
            if (confirmFields.isEmpty()) {
                confirmFields["action"] = "delete"
                confirmFields["playid"] = playId
                confirmFields["final"] = "1"
            }
            confirmFields["ajax"] = "1"
            confirmFields["final"] = "1"
            confirmFields.putIfAbsent("action", "delete")
            confirmFields.putIfAbsent("playid", playId)

            val confirmBody = executeGeekplayPost(confirmFields)
            Log.i(
                TAG,
                "Delete play confirm step: body=${confirmBody.take(200)}"
            )
            val accepted = looksLikeDeleteAccepted(confirmBody)
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
        val htmlCandidates = buildList {
            add(html)
            decodeEmbeddedHtml(html)?.let { add(it) }
        }
        val matches = Regex(
            """<input[^>]*type=["']hidden["'][^>]*name=["']([^"']+)["'][^>]*value=["']([^"']*)["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        return buildMap {
            htmlCandidates.forEach { candidate ->
                matches.findAll(candidate).forEach { match ->
                    put(match.groupValues[1], htmlEntityDecode(match.groupValues[2]))
                }
            }
        }
    }

    private fun decodeEmbeddedHtml(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val json = JSONObject(trimmed)
            sequenceOf("html", "content", "form", "dialog", "markup")
                .mapNotNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
                .firstOrNull()
                ?.let(::htmlEntityDecode)
        }.getOrNull()
    }

    private fun htmlEntityDecode(value: String): String {
        return value
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun looksLikeDeleteAccepted(body: String): Boolean {
        return body.contains("\"error\":null")
            || body.contains("\"error\": false")
            || body.contains("deleted", ignoreCase = true)
            || body.contains("success", ignoreCase = true)
            || body.contains("play has been deleted", ignoreCase = true)
            || body.isBlank()
    }

    private fun looksLikePlaySaveAccepted(body: String): Boolean {
        return body.contains("\"error\":null")
            || body.contains("\"error\": null")
            || body.contains("\"error\":false")
            || body.contains("\"error\": false")
            || body.contains("saved", ignoreCase = true)
            || body.isBlank()
    }

    private fun extractSavedPlayId(body: String): String? {
        runCatching {
            val json = JSONObject(body.trim())
            listOf("playid", "playId", "id").forEach { key ->
                json.optString(key).takeIf { it.isNotBlank() && it != "0" }?.let { return it }
            }
        }

        Regex(
            """"(?:playid|playId|id)"\s*:\s*"?([0-9]+)"?""",
            RegexOption.IGNORE_CASE
        ).find(body)?.groupValues?.getOrNull(1)?.let { return it }

        Regex(
            """name=["']playid["'][^>]*value=["']([0-9]+)["']""",
            RegexOption.IGNORE_CASE
        ).find(body)?.groupValues?.getOrNull(1)?.let { return it }

        Regex(
            """\bplayid\b\s*[:=]\s*["']?([0-9]+)""",
            RegexOption.IGNORE_CASE
        ).find(body)?.groupValues?.getOrNull(1)?.let { return it }

        return null
    }

    suspend fun getPlays(username: String): Result<List<LoggedPlay>> = withContext(Dispatchers.IO) {
        runCatching {
            val allPlays = mutableListOf<LoggedPlay>()
            var page = 1; val maxPages = 150
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
        var quantity = 1; var incomplete = false; var nowInStats = true
        var comments = ""; var insideComments = false
        var players = mutableListOf<PlayerResult>(); var insidePlayers = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "plays"    -> total = parser.getAttributeValue(null, "total")?.toIntOrNull() ?: 0
                    "play"     -> {
                        playId = parser.getAttributeValue(null, "id")
                        date = parser.getAttributeValue(null, "date") ?: ""
                        length = parser.getAttributeValue(null, "length")?.toIntOrNull() ?: 0
                        location = parser.getAttributeValue(null, "location") ?: ""
                        quantity = parser.getAttributeValue(null, "quantity")?.toIntOrNull() ?: 1
                        incomplete = parser.getAttributeValue(null, "incomplete") == "1"
                        nowInStats = parser.getAttributeValue(null, "nowinstats") != "0"
                        gameName = null; gameId = null; players = mutableListOf(); comments = ""
                    }
                    "item"     -> { gameName = parser.getAttributeValue(null, "name"); gameId = parser.getAttributeValue(null, "objectid")?.toIntOrNull() }
                    "players"  -> insidePlayers = true
                    "player"   -> if (insidePlayers) {
                        val name = parser.getAttributeValue(null, "name") ?: ""
                        val score = parser.getAttributeValue(null, "score") ?: ""
                        val win = parser.getAttributeValue(null, "win") == "1"
                        val color = parser.getAttributeValue(null, "color") ?: ""
                        val rating = parser.getAttributeValue(null, "rating") ?: ""
                        val isNew = parser.getAttributeValue(null, "new") == "1"
                        if (name.isNotBlank()) players.add(PlayerResult(name, score, win, color, rating, isNew))
                    }
                    "comments" -> insideComments = true
                }
                XmlPullParser.TEXT -> if (insideComments) comments += parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "players"  -> insidePlayers = false
                    "comments" -> insideComments = false
                    "play"     -> {
                        val parsedPlayId = playId
                        val parsedGameName = gameName
                        val parsedGameId = gameId
                        if (parsedPlayId != null && parsedGameName != null && parsedGameId != null) {
                        plays.add(LoggedPlay(
                            id = parsedPlayId, gameId = parsedGameId, gameName = parsedGameName, date = date,
                            players = players.toList(), durationMinutes = length, location = location,
                            postedToBgg = true, comments = comments.trim(),
                            quantity = quantity, incomplete = incomplete, nowInStats = nowInStats
                        ))
                        }
                    }
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
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    val parsedId = currentId
                    val parsedName = currentName
                    if (parsedId != null && parsedName != null) {
                        games.add(BggGame(id = parsedId, name = parsedName, yearPublished = currentYear, thumbnailUrl = null))
                    }
                }
            }
            event = parser.next()
        }
        return games.sortedBy { it.name.lowercase() }
    }

    suspend fun rateGame(gameId: Int, rating: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching<Unit> {
            require(rating in 1..10) { "Rating must be between 1 and 10" }
            val formBody = FormBody.Builder()
                .add("ajax", "1")
                .add("action", "rating")
                .add("objecttype", "thing")
                .add("objectid", gameId.toString())
                .add("rating", rating.toString())
                .build()
            val request = Request.Builder()
                .url("https://boardgamegeek.com/geekcollection.php")
                .post(formBody)
                .addHeader("Referer", "https://boardgamegeek.com/boardgame/$gameId")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("Failed to rate game: HTTP ${response.code}")
            Log.i(TAG, "Rated game $gameId with $rating: ${body.take(100)}")
        }
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
                    "item" -> {
                        val parsedId = currentId
                        val parsedName = currentName
                        if (parsedId != null && parsedName != null) {
                            games.add(BggGame(id = parsedId, name = parsedName, yearPublished = currentYear, thumbnailUrl = null))
                        }
                        currentId = null; currentName = null; currentYear = null
                    }
                }
            }
            event = parser.next()
        }
        return games
    }
}
