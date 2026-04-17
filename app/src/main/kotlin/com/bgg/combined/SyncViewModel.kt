package com.bgg.combined

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bgg.combined.data.BggApiClient
import com.bgg.combined.data.BggCache
import com.bgg.combined.data.BggImageCache
import com.bgg.combined.data.CsvParser
import com.bgg.combined.data.GoogleApiClient
import com.bgg.combined.data.SecurePreferences
import com.bgg.combined.model.GameItem
import com.bgg.combined.model.LogEntry
import com.bgg.combined.model.SavedSheet
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncViewModel(app: Application) : AndroidViewModel(app) {

    // ── Auth state ────────────────────────────────────────────────────────────

    private val _account = MutableStateFlow<GoogleSignInAccount?>(null)
    val account: StateFlow<GoogleSignInAccount?> = _account.asStateFlow()

    fun setAccount(account: GoogleSignInAccount?) { _account.value = account }

    // ── Log + busy state ──────────────────────────────────────────────────────

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var syncJob: Job? = null

    // ── Sheet config state ────────────────────────────────────────────────────

    private val _spreadsheetId = MutableStateFlow("")
    val spreadsheetId: StateFlow<String> = _spreadsheetId.asStateFlow()

    private val _sheetTabName = MutableStateFlow(SyncConfig.SHEET_TAB_NAME)
    val sheetTabName: StateFlow<String> = _sheetTabName.asStateFlow()

    fun setSpreadsheetId(id: String) { _spreadsheetId.value = extractSheetId(id) }
    fun setSheetTabName(name: String) { _sheetTabName.value = name.trim().ifBlank { SyncConfig.SHEET_TAB_NAME } }

    // ── Saved sheets ─────────────────────────────────────────────────────────

    private val securePrefs = SecurePreferences(app)

    private val _savedSheets = MutableStateFlow<List<SavedSheet>>(securePrefs.getSavedSheets())
    val savedSheets: StateFlow<List<SavedSheet>> = _savedSheets.asStateFlow()

    fun saveSheet(id: String, name: String) {
        securePrefs.saveSheet(id, name)
        _savedSheets.value = securePrefs.getSavedSheets()
    }

    fun deleteSheet(id: String) {
        securePrefs.deleteSheet(id)
        _savedSheets.value = securePrefs.getSavedSheets()
    }

    // ── Collection state (for CollectionScreen) ───────────────────────────────

    private val _collectionGames = MutableStateFlow<List<GameItem>>(emptyList())
    val collectionGames: StateFlow<List<GameItem>> = _collectionGames.asStateFlow()

    private val _collectionLoading = MutableStateFlow(false)
    val collectionLoading: StateFlow<Boolean> = _collectionLoading.asStateFlow()

    private val _collectionError = MutableStateFlow<String?>(null)
    val collectionError: StateFlow<String?> = _collectionError.asStateFlow()

    // ── CSV sync ──────────────────────────────────────────────────────────────

    fun syncCsv(account: GoogleSignInAccount, resolver: ContentResolver, csvUri: Uri) =
        runSync("CSV Sync  ·  tab: ${_sheetTabName.value}") {
            entry("Reading CSV file…", "", LogEntry.Type.INFO)
            val rows = CsvParser.parse(resolver, csvUri)
            entry("CSV loaded", "${rows.size} games found", LogEntry.Type.INFO)
            entry("Connecting to Google Sheets…", "", LogEntry.Type.INFO)
            val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
            val headerMap = api.readHeaderMap()
            val allRows   = api.readAllColumns()
            val objectidCol = headerMap["objectid"] ?: throw IllegalStateException("No 'objectid' column in sheet header.")
            val sheetById = buildSheetById(allRows, objectidCol)
            var updated = 0; var appended = 0; var failed = 0
            for (csvRow in rows) {
                if (!isActive) break
                val objectid = csvRow["objectid"]?.trim() ?: ""
                val name     = csvRow["objectname"]?.trim()?.ifBlank { objectid } ?: objectid
                try {
                    val rowIdx = if (objectid.isBlank()) null else sheetById[objectid]
                    if (rowIdx != null) {
                        val existing = if (rowIdx < allRows.size) allRows[rowIdx] else emptyList()
                        api.writeCsvRow(rowIdx, csvRow, headerMap, existing)
                        entry(name, "Updated  ·  row ${rowIdx + 1}", LogEntry.Type.UPDATED); updated++
                    } else {
                        sheetById.replaceAll { _, v -> v + 1 }
                        val newRowIdx = api.insertRowAfterHeader()
                        api.writeCsvRow(newRowIdx, csvRow, headerMap, emptyList())
                        entry(name, "Added  ·  row ${newRowIdx + 1}", LogEntry.Type.INSERTED)
                        if (objectid.isNotBlank()) sheetById[objectid] = newRowIdx
                        appended++
                    }
                } catch (e: Exception) { entry(name, e.message ?: "Unknown error", LogEntry.Type.ERROR); failed++ }
            }
            val stopped = !isActive
            val summary = buildString {
                if (stopped) append("Stopped early  ·  ")
                append("↻ $updated updated")
                if (appended > 0) append("  +$appended new")
                if (failed  > 0) append("  ✗ $failed failed")
            }
            entry("Sync complete", summary, if (stopped) LogEntry.Type.ERROR else LogEntry.Type.DONE)
        }

    fun createFolders(account: GoogleSignInAccount) = runSync("Create Folders & QR Codes") {
        entry("Reading sheet…", "", LogEntry.Type.INFO)
        val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
        val rows = api.readGameRows()
        val toProcess     = rows.filter { it.shareUrl.isBlank() }
        val toSaveLocally = rows.filter { it.shareUrl.isNotBlank() }
        entry("Sheet read", "${toProcess.size} need folders  ·  ${toSaveLocally.size} already processed", LogEntry.Type.INFO)
        var created = 0; var downloaded = 0; var skipped = 0; var failed = 0
        for (row in toProcess) {
            if (!isActive) break
            try {
                val shareUrl = api.createSharedFolder(row.gameName)
                val qrPng    = com.bgg.combined.data.QrGenerator.generatePng(shareUrl, row.gameName)
                val saved    = com.bgg.combined.data.QrGenerator.saveToGallery(getApplication(), row.gameName, qrPng)
                api.uploadQr(row.gameName, qrPng)
                val qrUrl    = api.getLastQrFileUrl()
                api.writeResultToRow(row.rowIndex, shareUrl, qrUrl)
                val detail = if (saved) "Folder + QR created  ·  saved to Gallery" else "Folder + QR created  ·  QR already in Gallery"
                entry(row.gameName, detail, LogEntry.Type.DONE); created++
            } catch (e: Exception) { entry(row.gameName, e.message ?: "Unknown error", LogEntry.Type.ERROR); failed++ }
        }
        for (row in toSaveLocally) {
            if (!isActive) break
            val alreadyLocal = com.bgg.combined.data.QrGenerator.isInGallery(getApplication(), com.bgg.combined.data.QrGenerator.fileName(row.gameName))
            if (alreadyLocal) { skipped++; continue }
            try {
                api.createSharedFolder(row.gameName)
                val qrBytes = api.downloadQrBytes()
                if (qrBytes != null) { com.bgg.combined.data.QrGenerator.saveToGallery(getApplication(), row.gameName, qrBytes); entry(row.gameName, "QR downloaded from Drive → Gallery", LogEntry.Type.UPDATED); downloaded++ }
                else { entry(row.gameName, "QR not found on Drive", LogEntry.Type.INFO); skipped++ }
            } catch (e: Exception) { entry(row.gameName, e.message ?: "Unknown error", LogEntry.Type.ERROR); failed++ }
        }
        val summary = buildString {
            if (created    > 0) append("✓ $created new  ")
            if (downloaded > 0) append("↓ $downloaded downloaded  ")
            if (skipped    > 0) append("· $skipped already local  ")
            if (failed     > 0) append("✗ $failed failed")
        }.trim()
        entry("Done", summary.ifBlank { "Nothing to do" }, if (failed > 0) LogEntry.Type.ERROR else LogEntry.Type.DONE)
    }

    fun syncBgg(account: GoogleSignInAccount, forceRefresh: Boolean) = runSync("BGG API Sync") {
        val cache = BggCache(getApplication())
        val collection = if (!forceRefresh && cache.exists()) {
            entry("BGG cache", "Loading from local cache", LogEntry.Type.INFO); cache.load()
        } else {
            if (forceRefresh) cache.delete()
            entry("BGG API", "Fetching collection…", LogEntry.Type.INFO)
            val games = BggApiClient().fetchCollection(SyncConfig.BGG_USERNAME)
            cache.save(games); entry("BGG API", "${games.size} games fetched and cached", LogEntry.Type.INFO); games
        }
        val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
        val headerMap = api.readHeaderMap(); val allRows = api.readAllColumns()
        val objectidCol = headerMap["objectid"] ?: throw IllegalStateException("No 'objectid' column in sheet header.")
        val sheetById = buildSheetById(allRows, objectidCol)
        var updated = 0; var appended = 0; var failed = 0
        for (game in collection) {
            if (!isActive) break
            try {
                val rowIdx = sheetById[game.objectid]
                if (rowIdx != null) {
                    api.writeBggRow(rowIdx, game, headerMap, if (rowIdx < allRows.size) allRows[rowIdx] else emptyList())
                    entry(game.objectname, "Updated  ·  row ${rowIdx + 1}", LogEntry.Type.UPDATED); updated++
                } else {
                    sheetById.replaceAll { _, v -> v + 1 }
                    val newRowIdx = api.insertRowAfterHeader()
                    api.writeBggRow(newRowIdx, game, headerMap, emptyList())
                    entry(game.objectname, "Added  ·  row ${newRowIdx + 1}", LogEntry.Type.INSERTED)
                    sheetById[game.objectid] = newRowIdx; appended++
                }
            } catch (e: Exception) { entry(game.objectname, e.message ?: "Unknown error", LogEntry.Type.ERROR); failed++ }
        }
        entry("Sync complete", "↻ $updated updated  +$appended new  ✗ $failed failed", if (!isActive) LogEntry.Type.ERROR else LogEntry.Type.DONE)
    }

    // ── Collection loading ────────────────────────────────────────────────────

    fun loadCollection(account: GoogleSignInAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            _collectionLoading.value = true; _collectionError.value = null
            try {
                val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
                val bgg = BggApiClient()
                val sheetGames = api.readCollectionRows()
                _collectionGames.value = sheetGames
                _collectionLoading.value = false
                try { bgg.loginIfNeeded(SyncConfig.BGG_USERNAME, SyncConfig.BGG_PASSWORD) } catch (_: Exception) {}
                val thumbDeferred = async { try { bgg.fetchOwnedThumbnails(SyncConfig.BGG_USERNAME) } catch (_: Exception) { emptyMap<String, String>() } }
                val wishlistDeferred = async { try { bgg.fetchWishlistGameItems(SyncConfig.BGG_USERNAME) } catch (_: Exception) { emptyList<GameItem>() } }
                val thumbnails = thumbDeferred.await(); val wishlistGames = wishlistDeferred.await()
                val wishlistIds = wishlistGames.map { it.objectId }.toSet()
                val enrichedSheet = sheetGames.map { g ->
                    g.copy(thumbnailUrl = g.thumbnailUrl?.takeIf { it.isNotBlank() } ?: thumbnails[g.objectId], isWishlisted = g.objectId in wishlistIds)
                }
                val sheetIds = sheetGames.map { it.objectId }.toSet()
                val enriched = enrichedSheet + wishlistGames.filter { it.objectId !in sheetIds }
                _collectionGames.value = enriched
                launch { BggImageCache.preloadAll(getApplication(), enriched) }
            } catch (e: Exception) {
                _collectionError.value = e.message ?: "Failed to load collection"
                _collectionLoading.value = false
            }
        }
    }

    // ── Log helpers ───────────────────────────────────────────────────────────

    fun appendLog(name: String, status: String = "", type: LogEntry.Type = LogEntry.Type.INFO) { entry(name, status, type) }
    fun clearLog() { _log.value = emptyList() }
    fun stopSync() { syncJob?.cancel(); entry("Stopped", "Sync was cancelled by user", LogEntry.Type.ERROR) }

    private fun entry(name: String, status: String, type: LogEntry.Type) {
        val current = _log.value.toMutableList(); current.add(LogEntry(name, status, type)); _log.value = current
    }

    private fun runSync(title: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            entry(title, "Starting…", LogEntry.Type.HEADER)
            try { block() } catch (e: Exception) { entry("Error", e.message ?: "Unknown error", LogEntry.Type.ERROR) }
            finally { _busy.value = false }
        }
    }

    private fun buildSheetById(allRows: List<List<Any>>, objectidCol: Int): MutableMap<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (i in SyncConfig.HEADER_ROW_INDEX + 1 until allRows.size) {
            val id = allRows[i].getOrNull(objectidCol)?.toString()?.trim() ?: ""
            if (id.isNotBlank()) map[id] = i
        }
        return map
    }

    private fun extractSheetId(input: String): String {
        val match = Regex("/spreadsheets(?:/u/\\d+)?/d/([a-zA-Z0-9_-]+)").find(input)
        return match?.groupValues?.get(1) ?: input
    }
}
