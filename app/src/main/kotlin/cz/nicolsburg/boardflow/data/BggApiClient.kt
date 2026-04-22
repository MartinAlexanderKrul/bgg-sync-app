package cz.nicolsburg.boardflow.data

import android.util.Log
import cz.nicolsburg.boardflow.model.GameItem
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class BggApiClient {
    companion object {
        private const val TAG = "BggApiClient"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var sessionCookies: String = ""
    @Volatile private var loggedIn = false

    data class SleevePageInfo(
        val status: GameItem.SleeveStatus,
        val cardSets: List<GameItem.Sleeves.CardSet> = emptyList(),
        val sourceUrl: String,
        val note: String? = null
    )

    fun loginIfNeeded(username: String, password: String) {
        if (loggedIn || password.isBlank()) return
        val body = FormBody.Builder()
            .add("credentials[username]", username)
            .add("credentials[password]", password)
            .build()
        http.newCall(
            Request.Builder()
                .url("https://boardgamegeek.com/login/api/v1")
                .header("User-Agent", "BoardFlow/1.0")
                .post(body)
                .build()
        ).execute().use { resp ->
            if (resp.code == 200 || resp.code == 204) {
                sessionCookies = resp.headers.values("Set-Cookie")
                    .mapNotNull { it.split(";").firstOrNull()?.trim() }
                    .joinToString("; ")
                loggedIn = true
            } else {
                throw RuntimeException("BGG login failed HTTP ${resp.code}")
            }
        }
    }

    data class BggGame(
        val objectid: String,
        val objecttype: String,
        val objectname: String,
        val thumbnailUrl: String?,
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
        val numplays: String = "",
        val own: String = "",
        val wishlist: String = ""
    ) {
        fun asMap(): Map<String, String> = mapOf(
            "objectid" to objectid,
            "objecttype" to objecttype,
            "objectname" to objectname,
            "yearpublished" to yearpublished,
            "minplayers" to minplayers,
            "maxplayers" to maxplayers,
            "playingtime" to playingtime,
            "minplaytime" to minplaytime,
            "maxplaytime" to maxplaytime,
            "rank" to rank,
            "average" to average,
            "baverage" to baverage,
            "numowned" to numowned,
            "avgweight" to avgweight,
            "bggrecplayers" to bggrecplayers,
            "bggbestplayers" to bggbestplayers,
            "bggrecagerange" to bggrecagerange,
            "bgglanguagedependence" to bgglanguagedependence,
            "thumbnail" to (thumbnailUrl ?: ""),
            "bggurl" to bggurl,
            "numplays" to numplays,
            "own" to own,
            "wishlist" to wishlist
        )
    }

    fun fetchWishlistGameItems(username: String, password: String? = null): List<GameItem> {
        password?.let { loginIfNeeded(username, it) }
        val body = fetchWithRetry("https://boardgamegeek.com/xmlapi2/collection?username=$username&wishlist=1&stats=1")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray()))
        doc.documentElement.normalize()
        val items = doc.getElementsByTagName("item")
        val result = mutableListOf<GameItem>()
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("objectid")
            val name = item.getElementsByTagName("name").item(0)?.textContent?.trim() ?: continue
            val thumb = item.getElementsByTagName("thumbnail").item(0)?.textContent?.trim()?.ifBlank { null }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            val stats = item.getElementsByTagName("stats").item(0) as? Element
            val rating = (stats?.getElementsByTagName("average")?.item(0) as? Element)?.getAttribute("value")?.toDoubleOrNull()
            val rank = (stats?.getElementsByTagName("rank")?.item(0) as? Element)?.getAttribute("value")?.toIntOrNull()
            result.add(
                GameItem(
                    identity = GameItem.Identity(
                        objectId = id,
                        name = name
                    ),
                    stats = GameItem.Stats(
                        rank = rank,
                        averageRating = rating,
                        bayesAverage = null,
                        weight = null,
                        yearPublished = item.getElementsByTagName("yearpublished").item(0)?.textContent?.trim()?.toIntOrNull(),
                        playingTime = stats?.getAttribute("playingtime")?.toIntOrNull(),
                        minPlayTime = stats?.getAttribute("minplaytime")?.toIntOrNull(),
                        maxPlayTime = stats?.getAttribute("maxplaytime")?.toIntOrNull(),
                        numOwned = null,
                        languageDependence = null,
                        language = null
                    ),
                    players = GameItem.Players(
                        minPlayers = stats?.getAttribute("minplayers")?.toIntOrNull(),
                        maxPlayers = stats?.getAttribute("maxplayers")?.toIntOrNull(),
                        bestPlayers = null,
                        recommendedPlayers = null,
                        recommendedAge = null
                    ),
                            ownership = GameItem.Ownership(
                                isOwned = false,
                                isWishlisted = true,
                                bggPlayCount = 0
                            ),
                    sleeves = GameItem.Sleeves(),
                    media = GameItem.Media(
                        thumbnailUrl = thumb
                    ),
                    links = GameItem.Links(
                        bggUrl = id.takeIf { it.isNotBlank() }?.let { "https://boardgamegeek.com/boardgame/$it" },
                        driveUrl = null,
                        qrImageUrl = null
                    ),
                    sources = GameItem.Sources(
                        spreadsheetValues = emptyMap(),
                        bggValues = buildMap {
                            put("objectid", id)
                            put("objectname", name)
                            put("wishlist", "1")
                            rank?.let { put("rank", it.toString()) }
                            rating?.let { put("average", it.toString()) }
                        }
                    )
                )
            )
        }
        return result
    }

    fun fetchOwnedThumbnails(username: String, password: String? = null): Map<String, String> {
        password?.let { loginIfNeeded(username, it) }
        val body = fetchWithRetry("https://boardgamegeek.com/xmlapi2/collection?username=$username&own=1")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray()))
        doc.documentElement.normalize()
        val items = doc.getElementsByTagName("item")
        val result = mutableMapOf<String, String>()
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("objectid")
            val thumb = item.getElementsByTagName("thumbnail").item(0)?.textContent?.trim()?.ifBlank { null }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            if (id.isNotBlank() && thumb != null) result[id] = thumb
        }
        return result
    }

    fun fetchCollection(username: String, password: String? = null): List<BggGame> {
        password?.let { loginIfNeeded(username, it) }
        val url = "https://boardgamegeek.com/xmlapi2/collection?username=$username&own=1&stats=1"
        val body = fetchWithRetry(url)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(body.toByteArray()))
        doc.documentElement.normalize()
        val items = doc.getElementsByTagName("item")
        val games = mutableListOf<BggGame>()
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("objectid")
            val name = item.getElementsByTagName("name").item(0)?.textContent ?: ""
            val thumb = item.getElementsByTagName("thumbnail").item(0)?.textContent?.trim()?.ifBlank { null }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            val year = item.getElementsByTagName("yearpublished").item(0)?.textContent ?: ""
            val stats = item.getElementsByTagName("stats").item(0) as? Element
            val minP = stats?.getAttribute("minplayers") ?: ""
            val maxP = stats?.getAttribute("maxplayers") ?: ""
            val time = stats?.getAttribute("playingtime") ?: ""
            val minT = stats?.getAttribute("minplaytime") ?: ""
            val maxT = stats?.getAttribute("maxplaytime") ?: ""
            val numO = stats?.getAttribute("numowned") ?: ""
            val avg = (stats?.getElementsByTagName("average")?.item(0) as? Element)?.getAttribute("value") ?: ""
            val bavg = (stats?.getElementsByTagName("bayesaverage")?.item(0) as? Element)?.getAttribute("value") ?: ""
            val rank = (stats?.getElementsByTagName("rank")?.item(0) as? Element)?.getAttribute("value") ?: ""
            val status = item.getElementsByTagName("status").item(0) as? Element
            games.add(
                BggGame(
                    objectid = id,
                    objecttype = item.getAttribute("subtype").ifBlank { "boardgame" },
                    objectname = name,
                    thumbnailUrl = thumb,
                    yearpublished = year,
                    minplayers = minP,
                    maxplayers = maxP,
                    playingtime = time,
                    minplaytime = minT,
                    maxplaytime = maxT,
                    rank = rank,
                    average = avg,
                    baverage = bavg,
                    numowned = numO,
                    avgweight = "",
                    bggrecplayers = "",
                    bggbestplayers = "",
                    bggrecagerange = "",
                    bgglanguagedependence = "",
                    bggurl = "https://boardgamegeek.com/boardgame/$id",
                    numplays = status?.getAttribute("numplays") ?: "",
                    own = status?.getAttribute("own") ?: "",
                    wishlist = status?.getAttribute("wishlist") ?: ""
                )
            )
        }
        return games
    }

    fun fetchSleeveInfo(
        gameId: String,
        gameName: String? = null,
        bggUrl: String? = null,
        objectType: String? = null
    ): SleevePageInfo {
        require(gameId.isNotBlank()) { "Missing BGG game id" }
        fetchSleeveInfoFromApi(gameId)?.let { return it }
        val slug = gameName
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
        val preferredRoute = inferBggRoute(bggUrl = bggUrl, objectType = objectType)
        val routeCandidates = buildList {
            add(preferredRoute)
            if (preferredRoute != "boardgameexpansion") add("boardgameexpansion")
            if (preferredRoute != "boardgame") add("boardgame")
        }.distinct()
        val urls = buildList {
            routeCandidates.forEach { route ->
                if (slug != null) add("https://boardgamegeek.com/$route/$gameId/$slug/sleeves")
                add("https://boardgamegeek.com/$route/$gameId/sleeves")
                if (slug != null) add("https://boardgamegeek.com/$route/$gameId/$slug")
                add("https://boardgamegeek.com/$route/$gameId")
            }
        }
        var lastFailure: Exception? = null
        var lastFetchedUrl: String? = null
        var lastHtmlSnippet: String? = null
        for (url in urls) {
            try {
                Log.i(
                    TAG,
                    "Fetching BGG sleeves for gameId=$gameId gameName=${gameName ?: ""} route=$preferredRoute objectType=${objectType ?: ""} sourceUrl=${bggUrl ?: ""} url=$url"
                )
                val html = fetchHtml(url)
                lastFetchedUrl = url
                lastHtmlSnippet = html.take(800).replace(Regex("\\s+"), " ").trim()
                parseSleeveInfo(html, url)?.let { return it }
                Log.i(
                    TAG,
                    "Could not parse sleeve data from $url snippet=${lastHtmlSnippet ?: ""}"
                )
            } catch (e: Exception) {
                Log.i(TAG, "BGG sleeves fetch failed for $url: ${e.message}")
                lastFailure = e
            }
        }
        return SleevePageInfo(
            status = GameItem.SleeveStatus.ERROR,
            sourceUrl = lastFetchedUrl ?: urls.first(),
            note = lastFailure?.message ?: "Could not parse sleeve data from BGG page"
        )
    }

    private fun fetchSleeveInfoFromApi(gameId: String): SleevePageInfo? {
        val url = "https://api.geekdo.com/api/cardsetsbygame?objectid=$gameId&nosession=1"
        return try {
            Log.i(TAG, "Fetching BGG sleeve API for gameId=$gameId url=$url")
            val body = fetchJson(url)
            val compact = body.take(1000).replace(Regex("\\s+"), " ").trim()
            Log.i(TAG, "BGG sleeve API response for gameId=$gameId body=$compact")

            val json = body.trim()
            val parsed = when {
                json.startsWith("{") -> parseSleeveApiObject(JSONObject(json), url)
                json.startsWith("[") -> parseSleeveApiArray(JSONArray(json), url)
                else -> SleevePageInfo(
                    status = GameItem.SleeveStatus.ERROR,
                    sourceUrl = url,
                    note = "Unexpected BGG sleeve API response"
                )
            }

            if (parsed.status == GameItem.SleeveStatus.FOUND) {
                Log.i(
                    TAG,
                    "Parsed BGG sleeve API for gameId=$gameId: ${parsed.cardSets.joinToString(" || ") { "${it.label}:${it.count ?: "?"}:${it.size ?: "?"}:${it.notes ?: ""}" }}"
                )
            } else {
                Log.i(TAG, "BGG sleeve API returned no card sets for gameId=$gameId")
            }
            return parsed
        } catch (e: Exception) {
            Log.i(TAG, "BGG sleeve API failed for gameId=$gameId: ${e.message}")
            null
        }
    }

    private fun inferBggRoute(bggUrl: String?, objectType: String?): String {
        val routeFromUrl = bggUrl
            ?.let { Regex("""boardgamegeek\.com/(boardgameexpansion|boardgameaccessory|boardgame)/""", RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
        if (routeFromUrl != null) return routeFromUrl

        return when (objectType?.trim()?.lowercase()) {
            "boardgameexpansion", "rpgitem", "videogameexpansion" -> "boardgameexpansion"
            "boardgameaccessory" -> "boardgameaccessory"
            else -> "boardgame"
        }
    }

    private fun fetchWithRetry(url: String): String {
        repeat(5) {
            val req = Request.Builder().url(url)
                .apply { if (sessionCookies.isNotBlank()) header("Cookie", sessionCookies) }
                .build()
            http.newCall(req).execute().use { response ->
                when {
                    response.code == 200 -> return response.body?.string() ?: ""
                    response.code == 202 -> Thread.sleep(5_000)
                    else -> throw RuntimeException("BGG API HTTP ${response.code}")
                }
            }
        }
        throw RuntimeException("BGG API still queued after 5 retries")
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://boardgamegeek.com/")
            .apply {
                if (sessionCookies.isNotBlank()) {
                    header("Cookie", sessionCookies)
                }
            }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = response.body?.string().orEmpty().take(400).replace(Regex("\\s+"), " ")
                Log.i(TAG, "BGG sleeves HTTP ${response.code} for $url body=$snippet")
                throw RuntimeException("BGG sleeves page HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun fetchJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0 Mobile Safari/537.36")
            .header("Accept", "application/json,text/plain,*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://boardgamegeek.com/")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = response.body?.string().orEmpty().take(400).replace(Regex("\\s+"), " ")
                Log.i(TAG, "BGG sleeve API HTTP ${response.code} for $url body=$snippet")
                throw RuntimeException("BGG sleeve API HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseSleeveInfo(html: String, url: String): SleevePageInfo? {
        val pageTitle = Regex("""(?is)<title>(.*?)</title>""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        val hasCardsSleeves = html.contains("Cards/Sleeves", ignoreCase = true)
        val hasNoCardInfo = html.contains("Looks like we don't have any card information", ignoreCase = true)
        Log.i(
            TAG,
            "Parsing BGG sleeves url=$url title=$pageTitle cardsSection=$hasCardsSleeves noCardInfo=$hasNoCardInfo"
        )
        if (html.contains("Looks like we don't have any card information", ignoreCase = true)) {
            return SleevePageInfo(
                status = GameItem.SleeveStatus.MISSING,
                sourceUrl = url,
                note = "No sleeve data is listed on BGG yet."
            )
        }

        val text = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)</(p|div|li|h1|h2|h3|h4|tr|td)>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        val lines = text
            .lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }

        val relevantLines = lines.filter {
            it.contains("sleeve", ignoreCase = true) ||
                it.contains("card", ignoreCase = true) ||
                it.contains("mm", ignoreCase = true)
        }.take(20)
        if (relevantLines.isNotEmpty()) {
            Log.i(TAG, "Relevant sleeve lines for $url: ${relevantLines.joinToString(" || ")}")
        } else {
            Log.i(TAG, "No relevant sleeve lines found for $url")
            logBootstrapSignals(html, url)
        }

        val cardSets = mutableListOf<GameItem.Sleeves.CardSet>()
        val cardSetPattern = Regex("""^(.+?)\s*\(([\d.]+\s*x\s*[\d.]+\s*mm)\)\s*for\s*(\d+)\s+cards?.*$""", RegexOption.IGNORE_CASE)
        val sizeOnlyPattern = Regex("""^([A-Za-z0-9'\/,&+ -]+):?\s*(\d+)\s*@\s*([\d.]+\s*x\s*[\d.]+\s*mm).*$""", RegexOption.IGNORE_CASE)
        val countPattern = Regex("""^Number of (?:NEW |extra |replaced )?Cards:\s*(\d+).*$""", RegexOption.IGNORE_CASE)
        val sizedCardsPattern = Regex("""^Game contains\s*(\d+)\s*cards\s*sized\s*([\d.]+\s*x\s*[\d.]+\s*mm).*$""", RegexOption.IGNORE_CASE)
        val manufacturerSizePattern = Regex("""^(?:Arcane Tinmen|Fantasy Flight|Mayday|Swan Panasia|Sleeve Kings|Ultra ?Pro|Gamegenic|Dragon Shield|KMC|Ultimate Guard|BCW|Paladin).*?(\d+(?:\.\d+)?\s*x\s*\d+(?:\.\d+)?\s*mm).*$""", RegexOption.IGNORE_CASE)

        lines.forEachIndexed { index, line ->
            cardSetPattern.matchEntire(line)?.let { match ->
                cardSets += GameItem.Sleeves.CardSet(
                    label = match.groupValues[1].trim(),
                    count = match.groupValues[3].toIntOrNull(),
                    size = match.groupValues[2].trim()
                )
                return@forEachIndexed
            }
            sizeOnlyPattern.matchEntire(line)?.let { match ->
                cardSets += GameItem.Sleeves.CardSet(
                    label = match.groupValues[1].trim(),
                    count = match.groupValues[2].toIntOrNull(),
                    size = match.groupValues[3].trim()
                )
                return@forEachIndexed
            }
            countPattern.matchEntire(line)?.let { match ->
                val nextLines = listOfNotNull(lines.getOrNull(index + 1), lines.getOrNull(index + 2))
                val sizeMatch = nextLines.firstNotNullOfOrNull {
                    Regex("""([\d.]+\s*x\s*[\d.]+\s*mm)""", RegexOption.IGNORE_CASE).find(it)
                }
                if (sizeMatch != null) {
                    cardSets += GameItem.Sleeves.CardSet(
                        label = "Cards",
                        count = match.groupValues[1].toIntOrNull(),
                        size = sizeMatch.groupValues[1].trim()
                    )
                }
                return@forEachIndexed
            }
            sizedCardsPattern.matchEntire(line)?.let { match ->
                cardSets += GameItem.Sleeves.CardSet(
                    label = "Cards",
                    count = match.groupValues[1].toIntOrNull(),
                    size = match.groupValues[2].trim()
                )
                return@forEachIndexed
            }
            if (line.contains("Cards/Sleeves", ignoreCase = true)) {
                val nextLines = lines.drop(index + 1).take(20)
                val counts = nextLines.mapNotNull {
                    Regex("""Number of (?:NEW |extra |replaced )?Cards:\s*(\d+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull()
                }
                val sizes = nextLines.mapNotNull {
                    manufacturerSizePattern.find(it)?.groupValues?.getOrNull(1)?.trim()
                }
                if (counts.isNotEmpty() && sizes.isNotEmpty()) {
                    cardSets += GameItem.Sleeves.CardSet(
                        label = "Cards",
                        count = counts.first(),
                        size = sizes.first()
                    )
                }
            }
        }

        val distinctCardSets = cardSets.distinctBy { "${it.label}|${it.count}|${it.size}|${it.notes}" }
        if (distinctCardSets.isNotEmpty()) {
            Log.i(
                TAG,
                "Parsed sleeve data for $url: ${distinctCardSets.joinToString(" || ") { "${it.label}:${it.count ?: "?"}:${it.size ?: "?"}:${it.notes ?: ""}" }}"
            )
        }
        return if (distinctCardSets.isNotEmpty()) {
            SleevePageInfo(
                status = GameItem.SleeveStatus.FOUND,
                cardSets = distinctCardSets,
                sourceUrl = url
            )
        } else null
    }

    private fun logBootstrapSignals(html: String, url: String) {
        val scriptSources = Regex("""<script[^>]+src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.groupValues[1] }
            .take(12)
            .toList()
        if (scriptSources.isNotEmpty()) {
            Log.i(TAG, "BGG script sources for $url: ${scriptSources.joinToString(" || ")}")
        }

        val windowSignals = Regex("""window\.([A-Za-z0-9_]+)\s*=""")
            .findAll(html)
            .map { it.groupValues[1] }
            .distinct()
            .take(12)
            .toList()
        if (windowSignals.isNotEmpty()) {
            Log.i(TAG, "BGG window signals for $url: ${windowSignals.joinToString(", ")}")
        }

        val apiHints = Regex("""(?:https://[^"' ]+|/api/[^"' ]+|/graphql[^"' ]*|graphql[^"' ]*)""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.value }
            .filter {
                it.contains("api", ignoreCase = true) ||
                    it.contains("graphql", ignoreCase = true) ||
                    it.contains("json", ignoreCase = true)
            }
            .distinct()
            .take(20)
            .toList()
        if (apiHints.isNotEmpty()) {
            Log.i(TAG, "BGG API hints for $url: ${apiHints.joinToString(" || ")}")
        }

        val sleeveScriptSnippets = Regex("""(?is)<script[^>]*>(.*?)</script>""")
            .findAll(html)
            .map { it.groupValues[1].replace(Regex("\\s+"), " ").trim() }
            .filter {
                it.contains("sleeve", ignoreCase = true) ||
                    it.contains("cardset", ignoreCase = true) ||
                    it.contains("cards/sleeves", ignoreCase = true)
            }
            .take(3)
            .toList()
        if (sleeveScriptSnippets.isNotEmpty()) {
            Log.i(TAG, "BGG sleeve script hints for $url: ${sleeveScriptSnippets.joinToString(" || ") { it.take(400) }}")
        }
    }

    private fun parseSleeveApiObject(json: JSONObject, url: String): SleevePageInfo {
        Log.i(TAG, "BGG sleeve API object keys: ${json.keys().asSequence().toList().joinToString(", ")}")
        val candidates = mutableListOf<GameItem.Sleeves.CardSet>()
        collectCardSetCandidates(json, candidates)
        val cardSets = normalizeCardSets(candidates)
        if (cardSets.isNotEmpty()) {
            return SleevePageInfo(
                status = GameItem.SleeveStatus.FOUND,
                cardSets = cardSets,
                sourceUrl = url
            )
        }
        return SleevePageInfo(
            status = GameItem.SleeveStatus.MISSING,
            sourceUrl = url,
            note = "No BGG sleeve data yet."
        )
    }

    private fun parseSleeveApiArray(json: JSONArray, url: String): SleevePageInfo {
        Log.i(TAG, "BGG sleeve API array length: ${json.length()}")
        val candidates = mutableListOf<GameItem.Sleeves.CardSet>()
        collectCardSetCandidates(json, candidates)
        val cardSets = normalizeCardSets(candidates)
        if (cardSets.isNotEmpty()) {
            return SleevePageInfo(
                status = GameItem.SleeveStatus.FOUND,
                cardSets = cardSets,
                sourceUrl = url
            )
        }
        return SleevePageInfo(
            status = GameItem.SleeveStatus.MISSING,
            sourceUrl = url,
            note = "No BGG sleeve data yet."
        )
    }

    private fun collectCardSetCandidates(node: Any?, output: MutableList<GameItem.Sleeves.CardSet>) {
        when (node) {
            is JSONObject -> {
                createCardSetCandidate(node)?.let(output::add)
                node.keys().forEach { key ->
                    collectCardSetCandidates(node.opt(key), output)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectCardSetCandidates(node.opt(index), output)
                }
            }
        }
    }

    private fun createCardSetCandidate(json: JSONObject): GameItem.Sleeves.CardSet? {
        val count = listOf("count", "numcards", "cardcount", "quantity", "qty")
            .firstNotNullOfOrNull { key -> json.opt(key)?.toString()?.toIntOrNull() }
        val size = json.optString("size").takeIf { it.isNotBlank() }
            ?: formatSize(
                width = json.optNumber("width"),
                height = json.optNumber("height")
            )
            ?: formatSize(
                width = json.optNumber("cardwidth"),
                height = json.optNumber("cardheight")
            )
            ?: formatSize(
                width = json.optNumber("sleevewidth"),
                height = json.optNumber("sleeveheight")
            )
        if (count == null && size == null) return null

        val label = listOf("name", "label", "cardname", "cardtype", "typename", "versionname")
            .firstNotNullOfOrNull { key ->
                json.optString(key)
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }
            ?: "Cards"
        val notes = listOf("notes", "note", "quantitynote", "comment", "description")
            .firstNotNullOfOrNull { key ->
                json.optString(key)
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }

        return GameItem.Sleeves.CardSet(
            label = label,
            count = count,
            size = size,
            notes = notes
        )
    }

    private fun JSONObject.optNumber(key: String): Double? {
        val value = opt(key)
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun formatSize(width: Double?, height: Double?): String? {
        if (width == null || height == null) return null
        return "${formatDimension(width)} x ${formatDimension(height)} mm"
    }

    private fun formatDimension(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    private fun normalizeCardSets(cardSets: List<GameItem.Sleeves.CardSet>): List<GameItem.Sleeves.CardSet> {
        val distinct = cardSets.distinctBy { "${it.label}|${it.count}|${it.size}|${it.notes}" }.toMutableList()
        val merged = mutableListOf<GameItem.Sleeves.CardSet>()

        distinct.forEach { cardSet ->
            val hasReferenceNote = cardSet.notes?.contains("reference", ignoreCase = true) == true ||
                cardSet.label.contains("reference", ignoreCase = true)
            val matchingIndex = if (hasReferenceNote && !cardSet.size.isNullOrBlank()) {
                merged.indexOfFirst {
                    it.size == cardSet.size &&
                        !it.label.contains("reference", ignoreCase = true)
                }
            } else {
                -1
            }

            if (matchingIndex >= 0) {
                val existing = merged[matchingIndex]
                merged[matchingIndex] = existing.copy(
                    count = (existing.count ?: 0) + (cardSet.count ?: 0)
                )
            } else {
                merged += cardSet.copy(
                    notes = cardSet.notes?.takeIf { !it.contains("reference", ignoreCase = true) }
                )
            }
        }

        return merged
    }
}
