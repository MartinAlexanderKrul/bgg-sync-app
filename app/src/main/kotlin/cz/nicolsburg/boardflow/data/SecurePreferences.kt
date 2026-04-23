package cz.nicolsburg.boardflow.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import cz.nicolsburg.boardflow.model.BggCredentials
import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.GameItem
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
            sheetTabName = sheetTabName,
            syncSpreadsheetId = syncSpreadsheetId,
            syncSheetTabName = syncSheetTabName,
            googleAuthorizedEmail = googleAuthorizedEmail,
            players = getPlayers(),
            recentGames = getRecentGames(),
            availableModels = getAvailableModels(),
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
                if (s.has("sheetTabName")) sheetTabName = s.getString("sheetTabName")
                if (s.has("syncSpreadsheetId")) syncSpreadsheetId = s.getString("syncSpreadsheetId")
                if (s.has("syncSheetTabName")) syncSheetTabName = s.getString("syncSheetTabName")
                if (s.has("googleAuthorizedEmail")) googleAuthorizedEmail = s.getString("googleAuthorizedEmail")
            },
            onSecureSettings = { s ->
                if (s.has("bggPassword")) bggPassword = s.getString("bggPassword")
                if (s.has("geminiApiKey")) geminiApiKey = s.getString("geminiApiKey")
            },
            onPlayers = { players -> savePlayers(players) },
            onRecentGamesJson = { jsonArray -> prefs.edit().putString(KEY_RECENT_GAMES, jsonArray).apply() },
            onAvailableModelsJson = { jsonArray -> prefs.edit().putString(KEY_AVAILABLE_MODELS, jsonArray).apply() },
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
        private const val KEY_SHEET_TAB_NAME      = "sheet_tab_name"
        private const val KEY_SYNC_SPREADSHEET_ID = "sync_spreadsheet_id"
        private const val KEY_SYNC_SHEET_TAB_NAME = "sync_sheet_tab_name"
        private const val KEY_GOOGLE_AUTHORIZED_EMAIL = "google_authorized_email"
        private const val KEY_COLLECTION_SNAPSHOT_PREFIX = "collection_snapshot_"
    }
}
