package com.bgg.combined.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bgg.combined.model.BggCredentials
import com.bgg.combined.model.BggGame
import com.bgg.combined.model.GameItem
import com.bgg.combined.model.LoggedPlay
import com.bgg.combined.model.Player
import com.bgg.combined.model.PlayerResult
import org.json.JSONArray
import org.json.JSONObject

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "bgg_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var bggUsername: String
        get() = prefs.getString(KEY_BGG_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BGG_USERNAME, value).apply()

    var bggPassword: String
        get() = prefs.getString(KEY_BGG_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BGG_PASSWORD, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value).apply()

    var geminiModelEndpoint: String
        get() = prefs.getString(KEY_GEMINI_MODEL, "gemini-flash-latest") ?: "gemini-flash-latest"
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value).apply()

    var appTheme: String
        get() = prefs.getString(KEY_APP_THEME, "DARK") ?: "DARK"
        set(value) = prefs.edit().putString(KEY_APP_THEME, value).apply()

    // --- Available Gemini models cache ---
    fun saveAvailableModels(models: List<String>) {
        val json = JSONArray()
        models.forEach { json.put(it) }
        prefs.edit().putString(KEY_AVAILABLE_MODELS, json.toString()).apply()
    }

    fun getAvailableModels(): List<String> {
        val json = prefs.getString(KEY_AVAILABLE_MODELS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getNextModel(currentModel: String): String? {
        val models = getAvailableModels()
        if (models.isEmpty()) return null
        val currentIndex = models.indexOf(currentModel)
        return when {
            currentIndex >= 0 && currentIndex < models.size - 1 -> models[currentIndex + 1]
            currentIndex == -1 && models.isNotEmpty() -> models[0]
            else -> null
        }
    }

    fun getCredentials(): BggCredentials? {
        val u = bggUsername
        val p = bggPassword
        return if (u.isNotBlank() && p.isNotBlank()) BggCredentials(u, p) else null
    }

    fun hasCredentials(): Boolean = bggUsername.isNotBlank() && bggPassword.isNotBlank()
    fun hasGeminiKey(): Boolean = geminiApiKey.isNotBlank()

    // --- Cached BGG collection ---
    fun saveCollection(games: List<BggGame>) {
        val json = JSONArray()
        games.forEach { g ->
            json.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("year", g.yearPublished ?: "")
            })
        }
        prefs.edit().apply {
            putString(KEY_COLLECTION, json.toString())
            putLong(KEY_COLLECTION_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    fun getCollection(): List<BggGame> {
        val json = prefs.getString(KEY_COLLECTION, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                BggGame(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    yearPublished = obj.getString("year").takeIf { it.isNotBlank() },
                    thumbnailUrl = null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun hasCollection(): Boolean {
        val json = prefs.getString(KEY_COLLECTION, "[]") ?: "[]"
        return json != "[]" && json.length > 2
    }

    fun clearCollection() {
        prefs.edit().apply {
            remove(KEY_COLLECTION)
            remove(KEY_COLLECTION_TIMESTAMP)
            apply()
        }
    }

    fun saveCollectionSnapshot(spreadsheetId: String, games: List<GameItem>) {
        val key = collectionSnapshotKey(spreadsheetId)
        val payload = JSONArray()
        games.forEach { payload.put(gameItemToJson(it)) }
        prefs.edit()
            .putString(key, payload.toString())
            .putLong("$key-ts", System.currentTimeMillis())
            .apply()
    }

    fun getCollectionSnapshot(spreadsheetId: String): List<GameItem> {
        val key = collectionSnapshotKey(spreadsheetId)
        val json = prefs.getString(key, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { index -> jsonToGameItem(array.getJSONObject(index)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearCollectionSnapshot(spreadsheetId: String) {
        val key = collectionSnapshotKey(spreadsheetId)
        prefs.edit()
            .remove(key)
            .remove("$key-ts")
            .apply()
    }

    // --- Recent games cache ---
    fun addRecentGame(game: BggGame) {
        val recent = getRecentGames().toMutableList()
        recent.removeAll { it.id == game.id }
        recent.add(0, game)
        if (recent.size > 50) recent.subList(50, recent.size).clear()
        val json = JSONArray()
        recent.forEach { g ->
            json.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("year", g.yearPublished ?: "")
            })
        }
        prefs.edit().putString(KEY_RECENT_GAMES, json.toString()).apply()
    }

    fun getRecentGames(): List<BggGame> {
        val json = prefs.getString(KEY_RECENT_GAMES, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                BggGame(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    yearPublished = obj.getString("year").takeIf { it.isNotBlank() },
                    thumbnailUrl = null
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Logged play history ---
    fun saveLoggedPlay(play: LoggedPlay) {
        val existing = getLoggedPlays().toMutableList()
        existing.add(0, play)
        val json = JSONArray()
        existing.forEach { p -> json.put(playToJson(p)) }
        prefs.edit().putString(KEY_LOGGED_PLAYS, json.toString()).apply()
    }

    fun getLoggedPlays(): List<LoggedPlay> {
        val json = prefs.getString(KEY_LOGGED_PLAYS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i -> jsonToPlay(array.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearLoggedPlays() {
        prefs.edit().remove(KEY_LOGGED_PLAYS).apply()
    }

    fun updateLoggedPlay(playId: String, transform: (LoggedPlay) -> LoggedPlay) {
        val plays = getLoggedPlays().map { if (it.id == playId) transform(it) else it }
        val json = JSONArray()
        plays.forEach { p -> json.put(playToJson(p)) }
        prefs.edit().putString(KEY_LOGGED_PLAYS, json.toString()).apply()
    }

    // --- Player roster ---
    fun savePlayers(players: List<Player>) {
        val json = JSONArray()
        players.forEach { p ->
            json.put(JSONObject().apply {
                put("id", p.id)
                put("displayName", p.displayName)
                put("aliases", JSONArray().also { arr -> p.aliases.forEach { arr.put(it) } })
                put("bggUsername", p.bggUsername)
            })
        }
        prefs.edit().putString(KEY_PLAYERS, json.toString()).apply()
    }

    fun getPlayers(): List<Player> {
        val json = prefs.getString(KEY_PLAYERS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val aliasArr = obj.getJSONArray("aliases")
                Player(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    aliases = (0 until aliasArr.length()).map { aliasArr.getString(it) },
                    bggUsername = obj.optString("bggUsername", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // --- BGG plays disk cache ---
    fun saveBggPlaysCache(plays: List<LoggedPlay>) {
        val json = JSONArray()
        plays.forEach { p -> json.put(playToJson(p)) }
        prefs.edit()
            .putString(KEY_BGG_PLAYS_CACHE, json.toString())
            .putLong(KEY_BGG_PLAYS_CACHE_TS, System.currentTimeMillis())
            .apply()
    }

    fun getBggPlaysCache(): List<LoggedPlay> {
        val json = prefs.getString(KEY_BGG_PLAYS_CACHE, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i -> jsonToPlay(array.getJSONObject(i)) }
        } catch (_: Exception) { emptyList() }
    }

    fun getBggPlaysCacheAgeMinutes(): Long {
        val ts = prefs.getLong(KEY_BGG_PLAYS_CACHE_TS, 0L)
        if (ts == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - ts) / 60_000
    }

    // --- Sync/Sheet preferences (from boardgames project) ---

    var sheetTabName: String
        get() = prefs.getString(KEY_SHEET_TAB_NAME, "GAMES")?.let {
            if (it.isBlank() || it == "test") "GAMES" else it
        } ?: "GAMES"
        set(value) = prefs.edit().putString(KEY_SHEET_TAB_NAME, value.trim()).apply()

    var syncSpreadsheetId: String
        get() = prefs.getString(KEY_SYNC_SPREADSHEET_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYNC_SPREADSHEET_ID, value.trim()).apply()

    var syncSheetTabName: String
        get() = prefs.getString(KEY_SYNC_SHEET_TAB_NAME, "GAMES")?.let {
            if (it.isBlank()) "GAMES" else it
        } ?: "GAMES"
        set(value) = prefs.edit().putString(KEY_SYNC_SHEET_TAB_NAME, value.trim()).apply()

    var googleAuthorizedEmail: String
        get() = prefs.getString(KEY_GOOGLE_AUTHORIZED_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOOGLE_AUTHORIZED_EMAIL, value.trim()).apply()

    // --- Export / Import all local data ---
    fun exportAll(includeSensitiveData: Boolean = false): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportDate", java.time.LocalDate.now().toString())
        root.put("includesSensitiveData", includeSensitiveData)
        root.put("settings", JSONObject().apply {
            put("bggUsername", bggUsername)
            put("geminiModel", geminiModelEndpoint)
            put("appTheme", appTheme)
            put("sheetTabName", sheetTabName)
            put("syncSpreadsheetId", syncSpreadsheetId)
            put("syncSheetTabName", syncSheetTabName)
            put("googleAuthorizedEmail", googleAuthorizedEmail)
        })
        if (includeSensitiveData) {
            root.put("secureSettings", JSONObject().apply {
                put("bggPassword", bggPassword)
                put("geminiApiKey", geminiApiKey)
            })
        }
        root.put("players", JSONArray().also { arr ->
            getPlayers().forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("displayName", p.displayName)
                    put("bggUsername", p.bggUsername)
                    put("aliases", JSONArray().also { a -> p.aliases.forEach { a.put(it) } })
                })
            }
        })
        root.put("loggedPlays", JSONArray().also { arr ->
            getLoggedPlays().forEach { p -> arr.put(playToJson(p)) }
        })
        root.put("recentGames", JSONArray().also { arr ->
            getRecentGames().forEach { g ->
                arr.put(JSONObject().apply {
                    put("id", g.id); put("name", g.name); put("year", g.yearPublished ?: "")
                })
            }
        })
        root.put("cachedCollection", JSONArray().also { arr ->
            getCollection().forEach { g ->
                arr.put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("year", g.yearPublished ?: "")
                })
            }
        })
        root.put("cachedCollectionTimestamp", prefs.getLong(KEY_COLLECTION_TIMESTAMP, 0L))
        root.put("cachedBggPlays", JSONArray().also { arr ->
            getBggPlaysCache().forEach { p -> arr.put(playToJson(p)) }
        })
        root.put("cachedBggPlaysTimestamp", prefs.getLong(KEY_BGG_PLAYS_CACHE_TS, 0L))
        root.put("availableModels", JSONArray().also { arr ->
            getAvailableModels().forEach { model -> arr.put(model) }
        })
        root.put("collectionSnapshots", JSONObject().also { snapshots ->
            prefs.all
                .filterKeys { it.startsWith(KEY_COLLECTION_SNAPSHOT_PREFIX) && !it.endsWith("-ts") }
                .forEach { (key, value) ->
                    val spreadsheetId = key.removePrefix(KEY_COLLECTION_SNAPSHOT_PREFIX)
                    snapshots.put(spreadsheetId, value?.toString() ?: "[]")
                    snapshots.put("${spreadsheetId}__ts", prefs.getLong("$key-ts", 0L))
                }
        })
        return root.toString(2)
    }

    fun importAll(json: String) {
        val root = JSONObject(json)
        root.optJSONObject("settings")?.let { s ->
            if (s.has("bggUsername")) bggUsername = s.getString("bggUsername")
            if (s.has("geminiModel")) geminiModelEndpoint = s.getString("geminiModel")
            if (s.has("appTheme")) appTheme = s.getString("appTheme")
            if (s.has("sheetTabName")) sheetTabName = s.getString("sheetTabName")
            if (s.has("syncSpreadsheetId")) syncSpreadsheetId = s.getString("syncSpreadsheetId")
            if (s.has("syncSheetTabName")) syncSheetTabName = s.getString("syncSheetTabName")
            if (s.has("googleAuthorizedEmail")) googleAuthorizedEmail = s.getString("googleAuthorizedEmail")
        }
        root.optJSONObject("secureSettings")?.let { s ->
            if (s.has("bggPassword")) bggPassword = s.getString("bggPassword")
            if (s.has("geminiApiKey")) geminiApiKey = s.getString("geminiApiKey")
        }
        root.optJSONArray("players")?.let { arr ->
            val players = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val aliasArr = obj.optJSONArray("aliases") ?: JSONArray()
                Player(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    aliases = (0 until aliasArr.length()).map { aliasArr.getString(it) },
                    bggUsername = obj.optString("bggUsername", "")
                )
            }
            savePlayers(players)
        }
        root.optJSONArray("loggedPlays")?.let { arr ->
            val plays = (0 until arr.length()).map { i -> jsonToPlay(arr.getJSONObject(i)) }
            val out = JSONArray()
            plays.forEach { p -> out.put(playToJson(p)) }
            prefs.edit().putString(KEY_LOGGED_PLAYS, out.toString()).apply()
        }
        root.optJSONArray("recentGames")?.let { arr ->
            prefs.edit().putString(KEY_RECENT_GAMES, arr.toString()).apply()
        }
        root.optJSONArray("cachedCollection")?.let { arr ->
            prefs.edit()
                .putString(KEY_COLLECTION, arr.toString())
                .putLong(KEY_COLLECTION_TIMESTAMP, root.optLong("cachedCollectionTimestamp", System.currentTimeMillis()))
                .apply()
        }
        root.optJSONArray("cachedBggPlays")?.let { arr ->
            prefs.edit()
                .putString(KEY_BGG_PLAYS_CACHE, arr.toString())
                .putLong(KEY_BGG_PLAYS_CACHE_TS, root.optLong("cachedBggPlaysTimestamp", System.currentTimeMillis()))
                .apply()
        }
        root.optJSONArray("availableModels")?.let { arr ->
            prefs.edit().putString(KEY_AVAILABLE_MODELS, arr.toString()).apply()
        }
        root.optJSONObject("collectionSnapshots")?.let { snapshots ->
            val editor = prefs.edit()
            val keys = snapshots.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.endsWith("__ts")) continue
                val value = snapshots.optString(key, "[]")
                val prefKey = collectionSnapshotKey(key)
                editor.putString(prefKey, value)
                editor.putLong("$prefKey-ts", snapshots.optLong("${key}__ts", System.currentTimeMillis()))
            }
            editor.apply()
        }
    }

    // --- Private helpers ---
    private fun playToJson(p: LoggedPlay): JSONObject = JSONObject().apply {
        put("id", p.id); put("gameId", p.gameId); put("gameName", p.gameName)
        put("date", p.date); put("durationMinutes", p.durationMinutes)
        put("location", p.location); put("postedToBgg", p.postedToBgg)
        put("comments", p.comments)
        put("players", JSONArray().also { arr ->
            p.players.forEach { pl ->
                arr.put(JSONObject().apply {
                    put("name", pl.name); put("score", pl.score); put("isWinner", pl.isWinner)
                })
            }
        })
    }

    private fun jsonToPlay(obj: JSONObject): LoggedPlay {
        val pa = obj.optJSONArray("players") ?: JSONArray()
        return LoggedPlay(
            id = obj.getString("id"),
            gameId = obj.getInt("gameId"),
            gameName = obj.getString("gameName"),
            date = obj.getString("date"),
            players = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                PlayerResult(p.getString("name"), p.getString("score"), p.getBoolean("isWinner"))
            },
            durationMinutes = obj.optInt("durationMinutes", 0),
            location = obj.optString("location", ""),
            postedToBgg = obj.optBoolean("postedToBgg", true),
            comments = obj.optString("comments", "")
        )
    }

    private fun gameItemToJson(game: GameItem): JSONObject = JSONObject().apply {
        put("lastCachedAt", game.lastCachedAt)
        put("identity", JSONObject().apply {
            put("objectId", game.identity.objectId)
            put("name", game.identity.name)
        })
        put("stats", JSONObject().apply {
            put("rank", game.stats.rank)
            put("averageRating", game.stats.averageRating)
            put("bayesAverage", game.stats.bayesAverage)
            put("weight", game.stats.weight)
            put("yearPublished", game.stats.yearPublished)
            put("playingTime", game.stats.playingTime)
            put("minPlayTime", game.stats.minPlayTime)
            put("maxPlayTime", game.stats.maxPlayTime)
            put("numOwned", game.stats.numOwned)
            put("languageDependence", game.stats.languageDependence)
            put("language", game.stats.language)
        })
        put("players", JSONObject().apply {
            put("minPlayers", game.players.minPlayers)
            put("maxPlayers", game.players.maxPlayers)
            put("bestPlayers", game.players.bestPlayers)
            put("recommendedPlayers", game.players.recommendedPlayers)
            put("recommendedAge", game.players.recommendedAge)
        })
        put("ownership", JSONObject().apply {
            put("isOwned", game.ownership.isOwned)
            put("isWishlisted", game.ownership.isWishlisted)
            put("sheetPlayCount", game.ownership.sheetPlayCount)
            put("historyPlayCount", game.ownership.historyPlayCount)
        })
        put("sleeves", JSONObject().apply {
            put("status", game.sleeves.status.name)
            put("sourceUrl", game.sleeves.sourceUrl)
            put("note", game.sleeves.note)
            put("lastFetchedAt", game.sleeves.lastFetchedAt)
            put("cardSets", JSONArray().also { arr ->
                game.sleeves.cardSets.forEach { cardSet ->
                    arr.put(JSONObject().apply {
                        put("label", cardSet.label)
                        put("count", cardSet.count)
                        put("size", cardSet.size)
                        put("notes", cardSet.notes)
                    })
                }
            })
        })
        put("media", JSONObject().apply {
            put("thumbnailUrl", game.media.thumbnailUrl)
        })
        put("links", JSONObject().apply {
            put("bggUrl", game.links.bggUrl)
            put("driveUrl", game.links.driveUrl)
            put("qrImageUrl", game.links.qrImageUrl)
        })
        put("sources", JSONObject().apply {
            put("spreadsheetValues", mapToJson(game.sources.spreadsheetValues))
            put("bggValues", mapToJson(game.sources.bggValues))
        })
    }

    private fun jsonToGameItem(obj: JSONObject): GameItem {
        val identity = obj.optJSONObject("identity") ?: JSONObject()
        val stats = obj.optJSONObject("stats") ?: JSONObject()
        val players = obj.optJSONObject("players") ?: JSONObject()
        val ownership = obj.optJSONObject("ownership") ?: JSONObject()
        val sleeves = obj.optJSONObject("sleeves") ?: JSONObject()
        val media = obj.optJSONObject("media") ?: JSONObject()
        val links = obj.optJSONObject("links") ?: JSONObject()
        val sources = obj.optJSONObject("sources") ?: JSONObject()
        return GameItem(
            identity = GameItem.Identity(
                objectId = identity.optString("objectId", ""),
                name = identity.optString("name", "")
            ),
            stats = GameItem.Stats(
                rank = stats.optNullableInt("rank"),
                averageRating = stats.optNullableDouble("averageRating"),
                bayesAverage = stats.optNullableDouble("bayesAverage"),
                weight = stats.optNullableDouble("weight"),
                yearPublished = stats.optNullableInt("yearPublished"),
                playingTime = stats.optNullableInt("playingTime"),
                minPlayTime = stats.optNullableInt("minPlayTime"),
                maxPlayTime = stats.optNullableInt("maxPlayTime"),
                numOwned = stats.optNullableInt("numOwned"),
                languageDependence = stats.optNullableString("languageDependence"),
                language = stats.optNullableString("language")
            ),
            players = GameItem.Players(
                minPlayers = players.optNullableInt("minPlayers"),
                maxPlayers = players.optNullableInt("maxPlayers"),
                bestPlayers = players.optNullableString("bestPlayers"),
                recommendedPlayers = players.optNullableString("recommendedPlayers"),
                recommendedAge = players.optNullableString("recommendedAge")
            ),
            ownership = GameItem.Ownership(
                isOwned = ownership.optBoolean("isOwned", false),
                isWishlisted = ownership.optBoolean("isWishlisted", false),
                sheetPlayCount = ownership.optNullableInt("sheetPlayCount"),
                historyPlayCount = ownership.optInt("historyPlayCount", 0)
            ),
            sleeves = GameItem.Sleeves(
                status = sleeves.optString("status", GameItem.SleeveStatus.UNKNOWN.name)
                    .let { value -> runCatching { GameItem.SleeveStatus.valueOf(value) }.getOrDefault(GameItem.SleeveStatus.UNKNOWN) },
                cardSets = sleeves.optJSONArray("cardSets")?.let { array ->
                    (0 until array.length()).map { index ->
                        val cardSet = array.optJSONObject(index) ?: JSONObject()
                        GameItem.Sleeves.CardSet(
                            label = cardSet.optString("label", ""),
                            count = cardSet.optNullableInt("count"),
                            size = cardSet.optNullableString("size"),
                            notes = cardSet.optNullableString("notes")
                        )
                    }.filter { it.label.isNotBlank() || !it.size.isNullOrBlank() || it.count != null }
                } ?: emptyList(),
                sourceUrl = sleeves.optNullableString("sourceUrl"),
                note = sleeves.optNullableString("note"),
                lastFetchedAt = sleeves.optNullableLong("lastFetchedAt")
            ),
            media = GameItem.Media(
                thumbnailUrl = media.optNullableString("thumbnailUrl")
            ),
            links = GameItem.Links(
                bggUrl = links.optNullableString("bggUrl"),
                driveUrl = links.optNullableString("driveUrl"),
                qrImageUrl = links.optNullableString("qrImageUrl")
            ),
            sources = GameItem.Sources(
                spreadsheetValues = jsonToMap(sources.optJSONObject("spreadsheetValues")),
                bggValues = jsonToMap(sources.optJSONObject("bggValues"))
            ),
            lastCachedAt = obj.optLong("lastCachedAt", System.currentTimeMillis())
        )
    }

    private fun mapToJson(values: Map<String, String>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) -> put(key, value) }
    }

    private fun jsonToMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val map = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.optString(key, "")
        }
        return map
    }

    private fun JSONObject.optNullableString(key: String): String? =
        optString(key, "").trim().ifBlank { null }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun collectionSnapshotKey(spreadsheetId: String): String =
        "${KEY_COLLECTION_SNAPSHOT_PREFIX}${spreadsheetId.trim()}"

    companion object {
        private const val KEY_BGG_USERNAME        = "bgg_username"
        private const val KEY_BGG_PASSWORD        = "bgg_password"
        private const val KEY_GEMINI_KEY          = "gemini_api_key"
        private const val KEY_GEMINI_MODEL        = "gemini_model_endpoint"
        private const val KEY_AVAILABLE_MODELS    = "available_gemini_models"
        private const val KEY_RECENT_GAMES        = "recent_games"
        private const val KEY_COLLECTION          = "cached_collection"
        private const val KEY_COLLECTION_TIMESTAMP = "collection_timestamp"
        private const val KEY_LOGGED_PLAYS        = "logged_plays"
        private const val KEY_PLAYERS             = "players"
        private const val KEY_BGG_PLAYS_CACHE     = "bgg_plays_cache"
        private const val KEY_BGG_PLAYS_CACHE_TS  = "bgg_plays_cache_ts"
        private const val KEY_APP_THEME           = "app_theme"
        private const val KEY_SHEET_TAB_NAME      = "sheet_tab_name"
        private const val KEY_SYNC_SPREADSHEET_ID = "sync_spreadsheet_id"
        private const val KEY_SYNC_SHEET_TAB_NAME = "sync_sheet_tab_name"
        private const val KEY_GOOGLE_AUTHORIZED_EMAIL = "google_authorized_email"
        private const val KEY_COLLECTION_SNAPSHOT_PREFIX = "collection_snapshot_"
    }
}
