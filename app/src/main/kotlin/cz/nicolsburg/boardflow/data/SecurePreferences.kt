package cz.nicolsburg.boardflow.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cz.nicolsburg.boardflow.model.BggCredentials
import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.GameRecognitionHint
import cz.nicolsburg.boardflow.model.PlayerRecognitionHint
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    private val storageDir = File(context.filesDir, "boardflow_store").also { it.mkdirs() }

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

    var sleevePreferredManufacturer: String
        get() = prefs.getString(KEY_SLEEVE_PREFERRED_MANUFACTURER, "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString(KEY_SLEEVE_PREFERRED_MANUFACTURER, value).apply()

    var statsPlayScope: String
        get() = prefs.getString(KEY_STATS_PLAY_SCOPE, "ALL_PLAYS") ?: "ALL_PLAYS"
        set(value) = prefs.edit().putString(KEY_STATS_PLAY_SCOPE, value).apply()

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

    fun clearLegacyCollectionArtifacts() {
        prefs.edit().apply {
            remove(KEY_COLLECTION)
            remove(KEY_COLLECTION_TIMESTAMP)
            apply()
        }
        prefs.all.keys
            .filter { it.startsWith(KEY_COLLECTION_SNAPSHOT_PREFIX) }
            .forEach { key -> prefs.edit().remove(key).apply() }
        File(storageDir, "collection_snapshots").deleteRecursively()
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

    fun clearLegacyLoggedPlayArtifacts() {
        prefs.edit().remove(KEY_LOGGED_PLAYS).apply()
        File(storageDir, "logged_plays.json").delete()
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

    // --- Log play session context ---
    fun saveSessionContext(ctx: cz.nicolsburg.boardflow.model.SessionContext) {
        val playersArr = org.json.JSONArray()
        ctx.players.forEach { p ->
            playersArr.put(JSONObject().apply {
                put("name", p.name)
                put("score", p.score)
                put("isWinner", p.isWinner)
                put("color", p.color)
                put("rating", p.rating)
                put("isNew", p.isNew)
            })
        }
        val json = JSONObject().apply {
            put("gameId", ctx.gameId)
            put("gameName", ctx.gameName)
            put("location", ctx.location)
            put("lastPlayTimestamp", ctx.lastPlayTimestamp)
            put("players", playersArr)
        }
        prefs.edit().putString(KEY_SESSION_CONTEXT, json.toString()).apply()
    }

    fun loadSessionContext(): cz.nicolsburg.boardflow.model.SessionContext? {
        val jsonStr = prefs.getString(KEY_SESSION_CONTEXT, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val arr = json.getJSONArray("players")
            val players = (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                cz.nicolsburg.boardflow.model.PlayerResult(
                    name     = p.getString("name"),
                    score    = p.optString("score", "0"),
                    isWinner = p.optBoolean("isWinner", false),
                    color    = p.optString("color", ""),
                    rating   = p.optString("rating", ""),
                    isNew    = p.optBoolean("isNew", false)
                )
            }
            cz.nicolsburg.boardflow.model.SessionContext(
                gameId            = json.getInt("gameId"),
                gameName          = json.getString("gameName"),
                players           = players,
                location          = json.optString("location", ""),
                lastPlayTimestamp = json.getLong("lastPlayTimestamp")
            )
        } catch (_: Exception) { null }
    }

    fun clearSessionContext() {
        prefs.edit().remove(KEY_SESSION_CONTEXT).apply()
    }

    // --- Sleeves excluded games ---
    fun getSleevesExcludedGameIds(): Set<String> {
        val json = prefs.getString(KEY_SLEEVES_EXCLUDED, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    fun saveSleevesExcludedGameIds(ids: Set<String>) {
        val json = JSONArray()
        ids.forEach { json.put(it) }
        prefs.edit().putString(KEY_SLEEVES_EXCLUDED, json.toString()).apply()
    }

    fun getLastGameInsightKey(gameId: Int): String? =
        prefs.getString("$KEY_GAME_INSIGHT_PREFIX$gameId", null)?.takeIf { it.isNotBlank() }

    fun setLastGameInsightKey(gameId: Int, key: String) {
        prefs.edit().putString("$KEY_GAME_INSIGHT_PREFIX$gameId", key).apply()
    }

    // --- Game recognition hints ---
    fun saveGameRecognitionHint(hint: GameRecognitionHint) {
        val existing = loadGameRecognitionHints().toMutableList()
        val idx = existing.indexOfFirst { it.gameObjectId == hint.gameObjectId }
        val merged = if (idx >= 0) {
            val old = existing[idx]
            hint.copy(
                normalizedTitles = (old.normalizedTitles + hint.normalizedTitles).distinct(),
                normalizedCategories = (old.normalizedCategories + hint.normalizedCategories).distinct(),
                timesConfirmed = old.timesConfirmed + 1
            )
        } else hint
        if (idx >= 0) existing[idx] = merged else existing.add(merged)
        val json = JSONArray()
        existing.forEach { h ->
            json.put(JSONObject().apply {
                put("gameObjectId", h.gameObjectId)
                put("gameName", h.gameName)
                put("normalizedTitles", JSONArray().also { a -> h.normalizedTitles.forEach { a.put(it) } })
                put("normalizedCategories", JSONArray().also { a -> h.normalizedCategories.forEach { a.put(it) } })
                put("confirmedAt", h.confirmedAt)
                put("timesConfirmed", h.timesConfirmed)
            })
        }
        prefs.edit().putString(KEY_GAME_RECOGNITION_HINTS, json.toString()).apply()
    }

    fun loadGameRecognitionHints(): List<GameRecognitionHint> {
        val json = prefs.getString(KEY_GAME_RECOGNITION_HINTS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val titlesArr = obj.optJSONArray("normalizedTitles") ?: JSONArray()
                val catsArr = obj.optJSONArray("normalizedCategories") ?: JSONArray()
                GameRecognitionHint(
                    gameObjectId = obj.getString("gameObjectId"),
                    gameName = obj.getString("gameName"),
                    normalizedTitles = (0 until titlesArr.length()).map { titlesArr.getString(it) },
                    normalizedCategories = (0 until catsArr.length()).map { catsArr.getString(it) },
                    confirmedAt = obj.getLong("confirmedAt"),
                    timesConfirmed = obj.getInt("timesConfirmed")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun deleteGameRecognitionHint(gameObjectId: String) {
        val updated = loadGameRecognitionHints().filter { it.gameObjectId != gameObjectId }
        val json = JSONArray()
        updated.forEach { h ->
            json.put(JSONObject().apply {
                put("gameObjectId", h.gameObjectId)
                put("gameName", h.gameName)
                put("normalizedTitles", JSONArray().also { a -> h.normalizedTitles.forEach { a.put(it) } })
                put("normalizedCategories", JSONArray().also { a -> h.normalizedCategories.forEach { a.put(it) } })
                put("confirmedAt", h.confirmedAt)
                put("timesConfirmed", h.timesConfirmed)
            })
        }
        prefs.edit().putString(KEY_GAME_RECOGNITION_HINTS, json.toString()).apply()
    }

    fun replaceGameRecognitionHint(hint: GameRecognitionHint) {
        val existing = loadGameRecognitionHints().toMutableList()
        val idx = existing.indexOfFirst { it.gameObjectId == hint.gameObjectId }
        if (idx >= 0) existing[idx] = hint else existing.add(hint)
        val json = JSONArray()
        existing.forEach { h ->
            json.put(JSONObject().apply {
                put("gameObjectId", h.gameObjectId)
                put("gameName", h.gameName)
                put("normalizedTitles", JSONArray().also { a -> h.normalizedTitles.forEach { a.put(it) } })
                put("normalizedCategories", JSONArray().also { a -> h.normalizedCategories.forEach { a.put(it) } })
                put("confirmedAt", h.confirmedAt)
                put("timesConfirmed", h.timesConfirmed)
            })
        }
        prefs.edit().putString(KEY_GAME_RECOGNITION_HINTS, json.toString()).apply()
    }

    fun clearGameRecognitionHints() {
        prefs.edit().remove(KEY_GAME_RECOGNITION_HINTS).apply()
    }

    // --- Player recognition hints ---
    fun savePlayerRecognitionHint(hint: PlayerRecognitionHint) {
        val existing = loadPlayerRecognitionHints().toMutableList()
        val idx = existing.indexOfFirst {
            it.scannedNameNormalized == hint.scannedNameNormalized &&
            it.confirmedRosterPlayerId == hint.confirmedRosterPlayerId
        }
        val merged = if (idx >= 0) {
            hint.copy(timesConfirmed = existing[idx].timesConfirmed + 1)
        } else hint
        if (idx >= 0) existing[idx] = merged else existing.add(merged)
        prefs.edit().putString(KEY_PLAYER_RECOGNITION_HINTS, serializePlayerHints(existing)).apply()
    }

    fun loadPlayerRecognitionHints(): List<PlayerRecognitionHint> {
        val json = prefs.getString(KEY_PLAYER_RECOGNITION_HINTS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                PlayerRecognitionHint(
                    scannedNameNormalized   = obj.getString("scannedNameNormalized"),
                    confirmedRosterPlayerId = obj.getString("confirmedRosterPlayerId"),
                    playerDisplayName       = obj.getString("playerDisplayName"),
                    timesConfirmed          = obj.getInt("timesConfirmed"),
                    lastConfirmedAt         = obj.getLong("lastConfirmedAt")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun clearPlayerRecognitionHints() {
        prefs.edit().remove(KEY_PLAYER_RECOGNITION_HINTS).apply()
    }

    private fun serializePlayerHints(hints: List<PlayerRecognitionHint>): String {
        val json = JSONArray()
        hints.forEach { h ->
            json.put(JSONObject().apply {
                put("scannedNameNormalized",   h.scannedNameNormalized)
                put("confirmedRosterPlayerId", h.confirmedRosterPlayerId)
                put("playerDisplayName",       h.playerDisplayName)
                put("timesConfirmed",          h.timesConfirmed)
                put("lastConfirmedAt",         h.lastConfirmedAt)
            })
        }
        return json.toString()
    }

    // --- Legacy BGG history cache compatibility ---
    fun clearLegacyBggPlayCacheArtifacts() {
        prefs.edit().remove(KEY_BGG_PLAYS_CACHE).remove(KEY_BGG_PLAYS_CACHE_TS).apply()
        File(storageDir, "bgg_plays_cache.json").delete()
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

    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNCED_AT, value).apply()

    // --- Export / Import all local data ---
    fun exportAll(
        includeSensitiveData: Boolean = false,
        collectionSnapshot: List<GameItem>? = null,
        loggedPlays: List<LoggedPlay>? = null,
        cachedBggPlays: List<LoggedPlay>? = null
    ): String {
        return BackupSerializer.export(
            includeSensitiveData = includeSensitiveData,
            bggUsername = bggUsername,
            bggPassword = bggPassword,
            geminiApiKey = geminiApiKey,
            geminiModelEndpoint = geminiModelEndpoint,
            appTheme = appTheme,
            statsPlayScope = statsPlayScope,
            sheetTabName = sheetTabName,
            syncSpreadsheetId = syncSpreadsheetId,
            syncSheetTabName = syncSheetTabName,
            googleAuthorizedEmail = googleAuthorizedEmail,
            sleevesExcludedGameIds = getSleevesExcludedGameIds(),
            players = getPlayers(),
            recentGames = getRecentGames(),
            availableModels = getAvailableModels(),
            recognitionHints = loadGameRecognitionHints(),
            collectionSnapshot = collectionSnapshot ?: emptyList(),
            loggedPlays = loggedPlays ?: emptyList(),
            cachedBggPlays = cachedBggPlays ?: emptyList()
        )
    }

    fun importAll(json: String): ImportedBackupData {
        return BackupSerializer.import(
            json = json,
            onSettings = { s ->
                if (s.has("bggUsername")) bggUsername = s.getString("bggUsername")
                if (s.has("geminiModel")) geminiModelEndpoint = s.getString("geminiModel")
                if (s.has("appTheme")) appTheme = s.getString("appTheme")
                if (s.has("statsPlayScope")) statsPlayScope = s.getString("statsPlayScope")
                if (s.has("sheetTabName")) sheetTabName = s.getString("sheetTabName")
                when {
                    s.has("googleSpreadsheetId") -> syncSpreadsheetId = s.getString("googleSpreadsheetId")
                    s.has("syncSpreadsheetId") -> syncSpreadsheetId = s.getString("syncSpreadsheetId")
                }
                if (s.has("syncSheetTabName")) syncSheetTabName = s.getString("syncSheetTabName")
                if (s.has("googleAuthorizedEmail")) googleAuthorizedEmail = s.getString("googleAuthorizedEmail")
                s.optJSONArray("sleevesExcludedGameIds")?.let { excluded ->
                    saveSleevesExcludedGameIds((0 until excluded.length()).map { excluded.getString(it) }.toSet())
                }
            },
            onSecureSettings = { s ->
                if (s.has("bggPassword")) bggPassword = s.getString("bggPassword")
                if (s.has("geminiApiKey")) geminiApiKey = s.getString("geminiApiKey")
            },
            onPlayers = { players -> savePlayers(players) },
            onRecentGamesJson = { jsonArray -> prefs.edit().putString(KEY_RECENT_GAMES, jsonArray).apply() },
            onAvailableModelsJson = { jsonArray -> prefs.edit().putString(KEY_AVAILABLE_MODELS, jsonArray).apply() },
            onRecognitionHints = { hints ->
                prefs.edit().putString(KEY_GAME_RECOGNITION_HINTS, org.json.JSONArray().also { arr ->
                    hints.forEach { h ->
                        arr.put(org.json.JSONObject().apply {
                            put("gameObjectId", h.gameObjectId)
                            put("gameName", h.gameName)
                            put("normalizedTitles", org.json.JSONArray().also { a -> h.normalizedTitles.forEach { a.put(it) } })
                            put("normalizedCategories", org.json.JSONArray().also { a -> h.normalizedCategories.forEach { a.put(it) } })
                            put("confirmedAt", h.confirmedAt)
                            put("timesConfirmed", h.timesConfirmed)
                        })
                    }
                }.toString()).apply()
            },
            clearLegacyCachedCollection = { prefs.edit().remove(KEY_COLLECTION).remove(KEY_COLLECTION_TIMESTAMP).apply() }
        )
    }

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
        private const val KEY_STATS_PLAY_SCOPE    = "stats_play_scope"
        private const val KEY_SHEET_TAB_NAME      = "sheet_tab_name"
        private const val KEY_SYNC_SPREADSHEET_ID = "sync_spreadsheet_id"
        private const val KEY_SYNC_SHEET_TAB_NAME = "sync_sheet_tab_name"
        private const val KEY_GOOGLE_AUTHORIZED_EMAIL = "google_authorized_email"
        private const val KEY_COLLECTION_SNAPSHOT_PREFIX = "collection_snapshot_"
        private const val KEY_SLEEVES_EXCLUDED = "sleeves_excluded_game_ids"
        private const val KEY_SLEEVE_PREFERRED_MANUFACTURER = "sleeve_preferred_manufacturer"
        private const val KEY_SESSION_CONTEXT  = "log_play_session_context"
        private const val KEY_GAME_INSIGHT_PREFIX = "game_insight_last_"
        private const val KEY_LAST_SYNCED_AT      = "last_synced_at"
        private const val KEY_GAME_RECOGNITION_HINTS  = "game_recognition_hints"
        private const val KEY_PLAYER_RECOGNITION_HINTS = "player_recognition_hints"
    }
}
