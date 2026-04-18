package com.bgg.combined

import android.accounts.Account
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
import com.bgg.combined.model.SpreadsheetDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncViewModel(app: Application) : AndroidViewModel(app) {

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    fun setAccount(account: Account?) {
        _account.value = account
    }

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var syncJob: Job? = null

    private val _spreadsheetId = MutableStateFlow("")
    val spreadsheetId: StateFlow<String> = _spreadsheetId.asStateFlow()

    private val _spreadsheetTitle = MutableStateFlow("")
    val spreadsheetTitle: StateFlow<String> = _spreadsheetTitle.asStateFlow()

    private val _sheetTabName = MutableStateFlow(SyncConfig.SHEET_TAB_NAME)
    val sheetTabName: StateFlow<String> = _sheetTabName.asStateFlow()

    fun setSpreadsheetId(id: String) {
        _spreadsheetId.value = extractSheetId(id)
    }

    fun setSheetTabName(name: String) {
        _sheetTabName.value = name.trim().ifBlank { SyncConfig.SHEET_TAB_NAME }
    }

    private val securePrefs = SecurePreferences(app)

    fun connectExistingSpreadsheet(account: Account, input: String) = runSync("Connect spreadsheet") {
        val resolvedId = extractSheetId(input).trim()
        require(resolvedId.isNotBlank()) { "Paste a Google Sheets URL or spreadsheet ID first." }
        entry("Google Sheets", "Checking spreadsheet access", LogEntry.Type.INFO)
        val api = GoogleApiClient(getApplication(), account, resolvedId)
        val details = api.getSpreadsheetDetails()
        val headerMap = api.readHeaderMap()
        if (headerMap.isEmpty()) {
            api.writeHeaderRow(SyncConfig.DEFAULT_SHEET_HEADERS)
            entry("First sheet", "Added starter columns to ${details.firstSheetTitle}", LogEntry.Type.INFO)
        } else {
            entry("First sheet", "Using ${details.firstSheetTitle}", LogEntry.Type.INFO)
        }
        applySpreadsheet(details)
        entry("Connected", details.title, LogEntry.Type.DONE)
        loadCollection(account)
    }

    fun createSpreadsheetFromBgg(account: Account) = runSync("Create spreadsheet from BGG") {
        val username = securePrefs.bggUsername.trim()
        require(username.isNotBlank()) { "Set your BGG username in Settings before creating a sheet." }
        val collection = loadBggCollection(forceRefresh = true)
        val details = GoogleApiClient.createSpreadsheet(
            context = getApplication(),
            account = account,
            title = "$username BGG Collection",
            sheetTitle = "Collection",
            headers = SyncConfig.DEFAULT_SHEET_HEADERS
        )
        applySpreadsheet(details)
        entry("Google Sheets", "Created ${details.title}", LogEntry.Type.INFO)
        val api = GoogleApiClient(getApplication(), account, details.id, details.firstSheetTitle)
        syncCollectionToSheet(api, collection)
        if (syncJob?.isActive == true) {
            loadCollection(account)
        }
    }

    private val _collectionGames = MutableStateFlow<List<GameItem>>(emptyList())
    val collectionGames: StateFlow<List<GameItem>> = _collectionGames.asStateFlow()

    private val _collectionLoading = MutableStateFlow(false)
    val collectionLoading: StateFlow<Boolean> = _collectionLoading.asStateFlow()

    private val _collectionError = MutableStateFlow<String?>(null)
    val collectionError: StateFlow<String?> = _collectionError.asStateFlow()

    fun syncCsv(account: Account, resolver: ContentResolver, csvUri: Uri) =
        runSync("CSV Sync  ·  tab: ${_sheetTabName.value}") {
            entry("Reading CSV file…", "", LogEntry.Type.INFO)
            val rows = CsvParser.parse(resolver, csvUri)
            entry("CSV loaded", "${rows.size} games found", LogEntry.Type.INFO)
            entry("Connecting to Google Sheets…", "", LogEntry.Type.INFO)
            val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
            val headerMap = api.readHeaderMap()
            val allRows = api.readAllColumns()
            val objectidCol = headerMap["objectid"] ?: throw IllegalStateException("No 'objectid' column in sheet header.")
            val sheetById = buildSheetById(allRows, objectidCol)
            var updated = 0
            var appended = 0
            var failed = 0
            for (csvRow in rows) {
                if (!isActive) break
                val objectid = csvRow["objectid"]?.trim() ?: ""
                val name = csvRow["objectname"]?.trim()?.ifBlank { objectid } ?: objectid
                try {
                    val rowIdx = if (objectid.isBlank()) null else sheetById[objectid]
                    if (rowIdx != null) {
                        val existing = if (rowIdx < allRows.size) allRows[rowIdx] else emptyList()
                        api.writeCsvRow(rowIdx, csvRow, headerMap, existing)
                        entry(name, "Updated  ·  row ${rowIdx + 1}", LogEntry.Type.UPDATED)
                        updated++
                    } else {
                        sheetById.replaceAll { _, v -> v + 1 }
                        val newRowIdx = api.insertRowAfterHeader()
                        api.writeCsvRow(newRowIdx, csvRow, headerMap, emptyList())
                        entry(name, "Added  ·  row ${newRowIdx + 1}", LogEntry.Type.INSERTED)
                        if (objectid.isNotBlank()) sheetById[objectid] = newRowIdx
                        appended++
                    }
                } catch (e: Exception) {
                    entry(name, e.message ?: "Unknown error", LogEntry.Type.ERROR)
                    failed++
                }
            }
            val stopped = !isActive
            val summary = buildString {
                if (stopped) append("Stopped early  ·  ")
                append("↻ $updated updated")
                if (appended > 0) append("  +$appended new")
                if (failed > 0) append("  ✗ $failed failed")
            }
            entry("Sync complete", summary, if (stopped) LogEntry.Type.ERROR else LogEntry.Type.DONE)
        }

    fun createFolders(account: Account, saveQrToGallery: Boolean) = runSync("Create Folders & QR Codes") {
        entry("Reading sheet…", "", LogEntry.Type.INFO)
        val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
        val rows = api.readGameRows()
        val toProcess = rows.filter { it.shareUrl.isBlank() }
        val toSaveLocally = if (saveQrToGallery) rows.filter { it.shareUrl.isNotBlank() } else emptyList()
        entry(
            "Sheet read",
            if (saveQrToGallery) "${toProcess.size} need folders  ·  ${toSaveLocally.size} can be saved locally"
            else "${toProcess.size} need folders",
            LogEntry.Type.INFO
        )
        var created = 0
        var downloaded = 0
        var skipped = 0
        var failed = 0
        for (row in toProcess) {
            if (syncJob?.isActive != true) break
            try {
                val shareUrl = api.createSharedFolder(row.gameName)
                val qrPng = com.bgg.combined.data.QrGenerator.generatePng(shareUrl, row.gameName)
                api.uploadQr(row.gameName, qrPng)
                val qrUrl = api.getLastQrFileUrl()
                api.writeResultToRow(row.rowIndex, shareUrl, qrUrl)
                val detail = if (saveQrToGallery) {
                    val saved = com.bgg.combined.data.QrGenerator.saveToGallery(getApplication(), row.gameName, qrPng)
                    if (saved) "Folder + QR created  ·  saved to Gallery" else "Folder + QR created  ·  QR already in Gallery"
                } else {
                    "Folder + QR created"
                }
                entry(row.gameName, detail, LogEntry.Type.DONE)
                created++
            } catch (e: Exception) {
                entry(row.gameName, e.message ?: "Unknown error", LogEntry.Type.ERROR)
                failed++
            }
        }
        for (row in toSaveLocally) {
            if (syncJob?.isActive != true) break
            val alreadyLocal = com.bgg.combined.data.QrGenerator.isInGallery(
                getApplication(),
                com.bgg.combined.data.QrGenerator.fileName(row.gameName)
            )
            if (alreadyLocal) {
                skipped++
                continue
            }
            try {
                api.createSharedFolder(row.gameName)
                val qrBytes = api.downloadQrBytes()
                if (qrBytes != null) {
                    com.bgg.combined.data.QrGenerator.saveToGallery(getApplication(), row.gameName, qrBytes)
                    entry(row.gameName, "QR downloaded from Drive → Gallery", LogEntry.Type.UPDATED)
                    downloaded++
                } else {
                    entry(row.gameName, "QR not found on Drive", LogEntry.Type.INFO)
                    skipped++
                }
            } catch (e: Exception) {
                entry(row.gameName, e.message ?: "Unknown error", LogEntry.Type.ERROR)
                failed++
            }
        }
        val summary = buildString {
            if (created > 0) append("✓ $created new  ")
            if (downloaded > 0) append("↓ $downloaded downloaded  ")
            if (skipped > 0) append("· $skipped already local  ")
            if (failed > 0) append("✗ $failed failed")
        }.trim()
        entry("Done", summary.ifBlank { "Nothing to do" }, if (failed > 0) LogEntry.Type.ERROR else LogEntry.Type.DONE)
    }

    fun syncBgg(account: Account, forceRefresh: Boolean) = runSync("BGG API Sync") {
        require(_spreadsheetId.value.isNotBlank()) { "Set a spreadsheet ID before syncing." }
        val collection = loadBggCollection(forceRefresh)
        val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
        syncCollectionToSheet(api, collection)
        val cancelled = syncJob?.isActive != true
        if (!cancelled) loadCollection(account)
    }

    fun loadCollection(account: Account, forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _collectionLoading.value = true
            _collectionError.value = null
            val cached = if (!forceRefresh && _spreadsheetId.value.isNotBlank()) {
                applyHistoryPlayCounts(securePrefs.getCollectionSnapshot(_spreadsheetId.value))
            } else {
                emptyList()
            }
            if (cached.isNotEmpty()) {
                _collectionGames.value = cached
                _collectionLoading.value = false
                return@launch
            }
            try {
                val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
                val bgg = BggApiClient()
                val sheetGames = api.readCollectionRows()
                val initial = applyHistoryPlayCounts(sheetGames)
                _collectionGames.value = initial
                _collectionLoading.value = false
                val username = securePrefs.bggUsername.trim()
                if (username.isBlank()) {
                    securePrefs.saveCollectionSnapshot(_spreadsheetId.value, initial)
                    return@launch
                }
                try {
                    bgg.loginIfNeeded(username, SyncConfig.BGG_PASSWORD)
                } catch (_: Exception) {
                }
                val thumbDeferred = async {
                    try {
                        bgg.fetchOwnedThumbnails(username)
                    } catch (_: Exception) {
                        emptyMap<String, String>()
                    }
                }
                val wishlistDeferred = async {
                    try {
                        bgg.fetchWishlistGameItems(username)
                    } catch (_: Exception) {
                        emptyList<GameItem>()
                    }
                }
                val thumbnails = thumbDeferred.await()
                val wishlistGames = wishlistDeferred.await()
                val wishlistIds = wishlistGames.map { it.objectId }.toSet()
                val enrichedSheet = sheetGames.map { game ->
                    game.copy(
                        media = game.media.copy(
                            thumbnailUrl = game.thumbnailUrl?.takeIf { it.isNotBlank() } ?: thumbnails[game.objectId]
                        ),
                        ownership = game.ownership.copy(
                            isWishlisted = game.objectId in wishlistIds
                        )
                    )
                }
                val sheetIds = sheetGames.map { it.objectId }.toSet()
                val enriched = applyHistoryPlayCounts(enrichedSheet + wishlistGames.filter { it.objectId !in sheetIds })
                _collectionGames.value = enriched
                securePrefs.saveCollectionSnapshot(_spreadsheetId.value, enriched)
                launch { BggImageCache.preloadAll(getApplication(), enriched) }
            } catch (e: Exception) {
                if (_collectionGames.value.isEmpty()) {
                    _collectionError.value = e.message ?: "Failed to load collection"
                    _collectionLoading.value = false
                }
            }
        }
    }

    fun appendLog(name: String, status: String = "", type: LogEntry.Type = LogEntry.Type.INFO) {
        entry(name, status, type)
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun stopSync() {
        syncJob?.cancel()
        entry("Stopped", "Sync was cancelled by user", LogEntry.Type.ERROR)
    }

    private fun entry(name: String, status: String, type: LogEntry.Type) {
        val current = _log.value.toMutableList()
        current.add(LogEntry(name, status, type))
        _log.value = current
    }

    private fun runSync(title: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            entry(title, "Starting…", LogEntry.Type.HEADER)
            try {
                block()
            } catch (e: Exception) {
                entry("Error", e.message ?: "Unknown error", LogEntry.Type.ERROR)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun applySpreadsheet(details: SpreadsheetDetails) {
        _spreadsheetId.value = details.id
        _spreadsheetTitle.value = details.title
        _sheetTabName.value = details.firstSheetTitle
        securePrefs.syncSpreadsheetId = details.id
        securePrefs.syncSheetTabName = details.firstSheetTitle
    }

    private fun loadBggCollection(forceRefresh: Boolean): List<BggApiClient.BggGame> {
        val username = securePrefs.bggUsername.trim()
        require(username.isNotBlank()) { "Set your BGG username in Settings before syncing." }
        val cache = BggCache(getApplication())
        return if (!forceRefresh && cache.exists()) {
            entry("BGG cache", "Loading from local cache", LogEntry.Type.INFO)
            cache.load()
        } else {
            if (forceRefresh) cache.delete()
            entry("BGG API", "Fetching collection…", LogEntry.Type.INFO)
            val games = BggApiClient().fetchCollection(username)
            cache.save(games)
            entry("BGG API", "${games.size} games fetched and cached", LogEntry.Type.INFO)
            games
        }
    }

    private fun syncCollectionToSheet(api: GoogleApiClient, collection: List<BggApiClient.BggGame>) {
        val headerMap = api.readHeaderMap()
        val allRows = api.readAllColumns()
        val objectidCol = headerMap["objectid"] ?: throw IllegalStateException("No 'objectid' column in the first sheet.")
        val sheetById = buildSheetById(allRows, objectidCol)
        var updated = 0
        var appended = 0
        var failed = 0
        for (game in collection) {
            if (syncJob?.isActive != true) break
            try {
                val rowIdx = sheetById[game.objectid]
                if (rowIdx != null) {
                    api.writeBggRow(rowIdx, game, headerMap, if (rowIdx < allRows.size) allRows[rowIdx] else emptyList())
                    entry(game.objectname, "Updated  ·  row ${rowIdx + 1}", LogEntry.Type.UPDATED)
                    updated++
                } else {
                    sheetById.replaceAll { _, v -> v + 1 }
                    val newRowIdx = api.insertRowAfterHeader()
                    api.writeBggRow(newRowIdx, game, headerMap, emptyList())
                    entry(game.objectname, "Added  ·  row ${newRowIdx + 1}", LogEntry.Type.INSERTED)
                    sheetById[game.objectid] = newRowIdx
                    appended++
                }
            } catch (e: Exception) {
                entry(game.objectname, e.message ?: "Unknown error", LogEntry.Type.ERROR)
                failed++
            }
        }
        val cancelled = syncJob?.isActive != true
        entry(
            "Sync complete",
            "↻ $updated updated  +$appended new  ✗ $failed failed",
            if (cancelled || failed > 0) LogEntry.Type.ERROR else LogEntry.Type.DONE
        )
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

    private fun applyHistoryPlayCounts(games: List<GameItem>): List<GameItem> {
        if (games.isEmpty()) return games
        val plays = securePrefs.getLoggedPlays()
        val byId = plays.groupingBy { it.gameId }.eachCount()
        val byName = plays.groupingBy { it.gameName.trim().lowercase() }.eachCount()
        return games.map { game ->
            val idCount = game.objectId.toIntOrNull()?.let { byId[it] } ?: 0
            val nameCount = byName[game.name.trim().lowercase()] ?: 0
            game.withHistoryPlayCount(maxOf(idCount, nameCount))
        }
    }
}
