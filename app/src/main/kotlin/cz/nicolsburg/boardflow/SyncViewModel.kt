package cz.nicolsburg.boardflow

import android.accounts.Account
import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.nicolsburg.boardflow.data.BggApiClient
import cz.nicolsburg.boardflow.data.BggCache
import cz.nicolsburg.boardflow.data.BggImageCache
import cz.nicolsburg.boardflow.data.BggRepository
import cz.nicolsburg.boardflow.data.refreshBggPlayCache
import cz.nicolsburg.boardflow.data.CsvParser
import cz.nicolsburg.boardflow.data.GoogleApiClient
import cz.nicolsburg.boardflow.data.SecurePreferences
import cz.nicolsburg.boardflow.model.BggCredentials
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.LogEntry
import cz.nicolsburg.boardflow.model.SpreadsheetDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val CANONICAL_SNAPSHOT_ID = "__canonical_collection__"
    }

    // Local extension for string-to-bool conversion (matches GoogleApiClient)
    private fun String?.toBoolFlag(): Boolean {
        if (this == null) return false
        val s = this.trim().lowercase()
        return s == "1" || s == "1.0" || s == "true" || s == "yes"
    }
    private val securePrefs = SecurePreferences(app)
    private val bggRepository = BggRepository()

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _hasBggCredentials = MutableStateFlow(securePrefs.hasCredentials())
    val hasBggCredentials: StateFlow<Boolean> = _hasBggCredentials.asStateFlow()

    private var syncJob: Job? = null

    private val _spreadsheetId = MutableStateFlow("")
    val spreadsheetId: StateFlow<String> = _spreadsheetId.asStateFlow()

    private val _spreadsheetTitle = MutableStateFlow("")
    val spreadsheetTitle: StateFlow<String> = _spreadsheetTitle.asStateFlow()

    private val _sheetTabName = MutableStateFlow(SyncConfig.SHEET_TAB_NAME)
    val sheetTabName: StateFlow<String> = _sheetTabName.asStateFlow()

    private val _collectionGames = MutableStateFlow<List<GameItem>>(emptyList())
    val collectionGames: StateFlow<List<GameItem>> = _collectionGames.asStateFlow()

    private val _collectionLoading = MutableStateFlow(false)
    val collectionLoading: StateFlow<Boolean> = _collectionLoading.asStateFlow()

    private val _collectionError = MutableStateFlow<String?>(null)
    val collectionError: StateFlow<String?> = _collectionError.asStateFlow()

    private val collectionMutex = Mutex()
    private val refreshMutex = Mutex()
    @Volatile private var suppressLog = false

    private enum class CollectionUpdateSource {
        BGG,
        SHEET,
        SLEEVES,
        FULL
    }

    fun setAccount(account: Account?) {
        _account.value = account
    }

    fun setSpreadsheetId(id: String) {
        _spreadsheetId.value = extractSheetId(id)
    }

    fun setSheetTabName(name: String) {
        _sheetTabName.value = name.trim().ifBlank { SyncConfig.SHEET_TAB_NAME }
    }

    fun refreshCredentialState() {
        _hasBggCredentials.value = securePrefs.hasCredentials()
    }

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
        val username = requireBggCredentials().username
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
        if (isActive) {
            loadCollection(account, forceRefresh = true)
        }
    }

    fun syncCsv(account: Account, resolver: ContentResolver, csvUri: Uri) =
        runSync("CSV Sync - tab: ${_sheetTabName.value}") {
            entry("Reading CSV file...", "", LogEntry.Type.INFO)
            val rows = CsvParser.parse(resolver, csvUri)
            entry("CSV loaded", "${rows.size} games found", LogEntry.Type.INFO)
            entry("Connecting to Google Sheets...", "", LogEntry.Type.INFO)
            val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
            val headerMap = api.readHeaderMap()
            val allRows = api.readAllColumns()
            val objectidCol = headerMap["objectid"]
                ?: throw IllegalStateException("No 'objectid' column in sheet header.")
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
                        entry(name, "Updated - row ${rowIdx + 1}", LogEntry.Type.UPDATED)
                        updated++
                    } else {
                        sheetById.replaceAll { _, v -> v + 1 }
                        val newRowIdx = api.insertRowAfterHeader()
                        api.writeCsvRow(newRowIdx, csvRow, headerMap, emptyList())
                        entry(name, "Added - row ${newRowIdx + 1}", LogEntry.Type.INSERTED)
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
                if (stopped) append("Stopped early - ")
                append("$updated updated")
                if (appended > 0) append("  +$appended new")
                if (failed > 0) append("  x $failed failed")
            }
            entry("Sync complete", summary, if (stopped) LogEntry.Type.ERROR else LogEntry.Type.DONE)
        }

    fun createFolders(account: Account, saveQrToGallery: Boolean) = runSync("Create Folders & QR Codes") {
        entry("Reading sheet...", "", LogEntry.Type.INFO)
        val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
        val rows = api.readGameRows()
        val toProcess = rows.filter { it.shareUrl.isBlank() }
        val toSaveLocally = if (saveQrToGallery) rows.filter { it.shareUrl.isNotBlank() } else emptyList()
        entry(
            "Sheet read",
            if (saveQrToGallery) {
                "${toProcess.size} need folders - ${toSaveLocally.size} can be saved locally"
            } else {
                "${toProcess.size} need folders"
            },
            LogEntry.Type.INFO
        )
        var created = 0
        var downloaded = 0
        var skipped = 0
        var failed = 0
        for (row in toProcess) {
            if (!isActive) break
            try {
                val shareUrl = api.createSharedFolder(row.gameName)
                val qrPng = cz.nicolsburg.boardflow.data.QrGenerator.generatePng(shareUrl, row.gameName)
                api.uploadQr(row.gameName, qrPng)
                val qrUrl = api.getLastQrFileUrl()
                api.writeResultToRow(row.rowIndex, shareUrl, qrUrl)
                val detail = if (saveQrToGallery) {
                    val saved = cz.nicolsburg.boardflow.data.QrGenerator.saveToGallery(getApplication(), row.gameName, qrPng)
                    if (saved) "Folder + QR created - saved to Gallery" else "Folder + QR created - QR already in Gallery"
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
            if (!isActive) break
            val alreadyLocal = cz.nicolsburg.boardflow.data.QrGenerator.isInGallery(
                getApplication(),
                cz.nicolsburg.boardflow.data.QrGenerator.fileName(row.gameName)
            )
            if (alreadyLocal) {
                skipped++
                continue
            }
            try {
                api.createSharedFolder(row.gameName)
                val qrBytes = api.downloadQrBytes()
                if (qrBytes != null) {
                    cz.nicolsburg.boardflow.data.QrGenerator.saveToGallery(getApplication(), row.gameName, qrBytes)
                    entry(row.gameName, "QR downloaded from Drive to Gallery", LogEntry.Type.UPDATED)
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
            if (created > 0) append("$created new  ")
            if (downloaded > 0) append("$downloaded downloaded  ")
            if (skipped > 0) append("$skipped already local  ")
            if (failed > 0) append("$failed failed")
        }.trim()
        entry("Done", summary.ifBlank { "Nothing to do" }, if (failed > 0) LogEntry.Type.ERROR else LogEntry.Type.DONE)
    }

    fun syncBgg(account: Account, forceRefresh: Boolean) = runSync("BGG API Sync") {
        require(_spreadsheetId.value.isNotBlank()) { "Set a spreadsheet ID before syncing." }
        val collection = loadBggCollection(forceRefresh)
        val api = GoogleApiClient(getApplication(), account, _spreadsheetId.value, _sheetTabName.value)
        syncCollectionToSheet(api, collection)
        if (isActive) {
            loadCollection(account, forceRefresh = true)
        }
    }

    fun refreshCollection(forceRefresh: Boolean = true) = runSync("Refresh Collection") {
        refreshMutex.withLock {
            _collectionLoading.value = true
            _collectionError.value = null
            try {
                refreshBggPlayHistory()
                val merged = buildCanonicalCollectionSnapshot(forceRefresh = forceRefresh, refreshSleeves = true)
                replaceCollectionSnapshot(merged)
                _collectionGames.value = merged
                saveSleevesToSheetIfAvailable(merged)
                entry("Collection cached", "${merged.size} games ready in the app", LogEntry.Type.DONE)
                launch { BggImageCache.preloadAll(getApplication(), merged) }
            } catch (e: Exception) {
                _collectionError.value = e.message ?: "Failed to refresh collection"
                throw e
            } finally {
                _collectionLoading.value = false
            }
        }
    }

    fun refreshCollectionSilentlyOnStartup(forceRefresh: Boolean = true) {
        if (!refreshMutex.tryLock()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                refreshCredentialState()
                val account = _account.value ?: return@launch
                val spreadsheetId = _spreadsheetId.value.ifBlank { securePrefs.syncSpreadsheetId }.trim()
                if (spreadsheetId.isBlank() || !securePrefs.hasCredentials()) return@launch

                withSuppressedLogging {
                    refreshBggPlayHistory()
                    val merged = buildCanonicalCollectionSnapshot(
                        forceRefresh = forceRefresh,
                        refreshSleeves = true,
                        preferredAccount = account
                    )
                    replaceCollectionSnapshot(merged)
                    _collectionGames.value = merged
                    saveSleevesToSheetIfAvailable(merged)
                    launch { BggImageCache.preloadAll(getApplication(), merged) }
                }
            } catch (_: Exception) {
                // Silent startup refresh should fail quietly and leave cached data in place.
            } finally {
                refreshMutex.unlock()
            }
        }
    }

    fun refreshSleeveDataFromBgg(forceRefresh: Boolean = true) = runSync("BGG Sleeve Refresh") {
        refreshMutex.withLock {
            val existingGames = currentOrCachedCollection()
            require(existingGames.isNotEmpty()) { "Refresh your collection from BGG first." }
            val refreshed = fetchSleeveUpdates(existingGames, forceRefresh)
            val merged = mergeGameItems(existingGames, refreshed, CollectionUpdateSource.SLEEVES)
            replaceCollectionSnapshot(merged)
            _collectionGames.value = merged
            saveSleevesToSheetIfAvailable(merged)
        }
    }

    fun loadCollection(account: Account, forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _collectionLoading.value = true
            _collectionError.value = null
            _account.value = account
            val cached = if (!forceRefresh) {
                readCanonicalSnapshot()
            } else {
                emptyList()
            }
            if (cached.isNotEmpty()) {
                _collectionGames.value = cached
                _collectionLoading.value = false
                return@launch
            }
            try {
                refreshMutex.withLock {
                    val merged = buildCanonicalCollectionSnapshot(
                        forceRefresh = forceRefresh,
                        refreshSleeves = false,
                        preferredAccount = account
                    )
                    replaceCollectionSnapshot(merged)
                    _collectionGames.value = merged
                    launch { BggImageCache.preloadAll(getApplication(), merged) }
                }
            } catch (e: Exception) {
                if (_collectionGames.value.isEmpty()) {
                    _collectionError.value = e.message ?: "Failed to load collection"
                }
                _collectionLoading.value = false
                return@launch
            }
            _collectionLoading.value = false
        }
    }

    fun loadCachedCollection() {
        viewModelScope.launch(Dispatchers.IO) {
            _collectionLoading.value = true
            _collectionError.value = null
            val cached = readCanonicalSnapshot()
            _collectionGames.value = cached
            _collectionLoading.value = false
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
        if (suppressLog) return
        val current = _log.value.toMutableList()
        current.add(LogEntry(name, status, type))
        _log.value = current
    }

    private suspend fun <T> withSuppressedLogging(block: suspend () -> T): T {
        suppressLog = true
        return try {
            block()
        } finally {
            suppressLog = false
        }
    }

    private fun runSync(title: String, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            refreshCredentialState()
            entry(title, "Starting...", LogEntry.Type.HEADER)
            try {
                block()
            } catch (e: Exception) {
                entry("Error", e.message ?: "Unknown error", LogEntry.Type.ERROR)
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun replaceCollectionSnapshot(games: List<GameItem>) {
        collectionMutex.withLock {
            writeCanonicalSnapshotLocked(games)
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
        val credentials = requireBggCredentials()
        val cache = BggCache(getApplication())
        return if (!forceRefresh && cache.exists(credentials.username)) {
            entry("BGG cache", "Loading from local cache", LogEntry.Type.INFO)
            cache.load(credentials.username)
        } else {
            if (forceRefresh) cache.delete(credentials.username)
            entry("BGG API", "Fetching collection...", LogEntry.Type.INFO)
            val games = BggApiClient().fetchCollection(credentials.username, credentials.password)
            cache.save(credentials.username, games)
            entry("BGG API", "${games.size} games fetched and cached", LogEntry.Type.INFO)
            games
        }
    }

    private suspend fun syncCollectionToSheet(api: GoogleApiClient, collection: List<BggApiClient.BggGame>) {
        val headerMap = api.readHeaderMap()
        val allRows = api.readAllColumns()
        val objectidCol = headerMap["objectid"]
            ?: throw IllegalStateException("No 'objectid' column in the first sheet.")
        val sheetById = buildSheetById(allRows, objectidCol)
        var updated = 0
        var appended = 0
        var failed = 0
        for (game in collection) {
            if (!currentCoroutineContext().isActive) break
            try {
                val rowIdx = sheetById[game.objectid]
                if (rowIdx != null) {
                    api.writeBggRow(
                        rowIdx,
                        game,
                        headerMap,
                        if (rowIdx < allRows.size) allRows[rowIdx] else emptyList()
                    )
                    entry(game.objectname, "Updated - row ${rowIdx + 1}", LogEntry.Type.UPDATED)
                    updated++
                } else {
                    sheetById.replaceAll { _, v -> v + 1 }
                    val newRowIdx = api.insertRowAfterHeader()
                    api.writeBggRow(newRowIdx, game, headerMap, emptyList())
                    entry(game.objectname, "Added - row ${newRowIdx + 1}", LogEntry.Type.INSERTED)
                    sheetById[game.objectid] = newRowIdx
                    appended++
                }
            } catch (e: Exception) {
                entry(game.objectname, e.message ?: "Unknown error", LogEntry.Type.ERROR)
                failed++
            }
        }
        entry(
            "Sync complete",
            "$updated updated  +$appended new  x $failed failed",
            if (failed > 0) LogEntry.Type.ERROR else LogEntry.Type.DONE
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

    private fun String?.meaningfulOr(fallback: String?): String? {
        return this?.trim()?.ifBlank { null } ?: fallback
    }

    private fun activeSnapshotId(): String {
        return CANONICAL_SNAPSHOT_ID
    }

    private fun mergeGameItem(
        old: GameItem,
        update: GameItem,
        source: CollectionUpdateSource
    ): GameItem {
        return when (source) {
            CollectionUpdateSource.BGG -> old.copy(
                identity = update.identity,
                stats = GameItem.Stats(
                    rank = update.stats.rank ?: old.stats.rank,
                    averageRating = update.stats.averageRating ?: old.stats.averageRating,
                    bayesAverage = update.stats.bayesAverage ?: old.stats.bayesAverage,
                    weight = update.stats.weight ?: old.stats.weight,
                    yearPublished = update.stats.yearPublished ?: old.stats.yearPublished,
                    playingTime = update.stats.playingTime ?: old.stats.playingTime,
                    minPlayTime = update.stats.minPlayTime ?: old.stats.minPlayTime,
                    maxPlayTime = update.stats.maxPlayTime ?: old.stats.maxPlayTime,
                    numOwned = update.stats.numOwned ?: old.stats.numOwned,
                    languageDependence = update.stats.languageDependence.meaningfulOr(old.stats.languageDependence),
                    language = update.stats.language.meaningfulOr(old.stats.language)
                ),
                players = GameItem.Players(
                    minPlayers = update.players.minPlayers ?: old.players.minPlayers,
                    maxPlayers = update.players.maxPlayers ?: old.players.maxPlayers,
                    bestPlayers = update.players.bestPlayers.meaningfulOr(old.players.bestPlayers),
                    recommendedPlayers = update.players.recommendedPlayers.meaningfulOr(old.players.recommendedPlayers),
                    recommendedAge = update.players.recommendedAge.meaningfulOr(old.players.recommendedAge)
                ),
                ownership = old.ownership.copy(
                    isOwned = update.ownership.isOwned,
                    isWishlisted = update.ownership.isWishlisted,
                    bggPlayCount = update.ownership.bggPlayCount ?: old.ownership.bggPlayCount
                ),
                media = old.media.copy(
                    thumbnailUrl = update.media.thumbnailUrl.meaningfulOr(old.media.thumbnailUrl)
                ),
                links = old.links.copy(
                    bggUrl = update.links.bggUrl.meaningfulOr(old.links.bggUrl)
                ),
                sources = old.sources.copy(
                    bggValues = if (update.sources.bggValues.isNotEmpty()) update.sources.bggValues else old.sources.bggValues
                ),
                lastCachedAt = System.currentTimeMillis()
            )

            CollectionUpdateSource.SHEET -> old.copy(
                identity = old.identity.copy(
                    objectId = old.objectId.ifBlank { update.objectId },
                    name = old.name.ifBlank { update.name }
                ),
                links = old.links.copy(
                    driveUrl = update.links.driveUrl ?: old.links.driveUrl,
                    qrImageUrl = update.links.qrImageUrl ?: old.links.qrImageUrl
                ),
                sources = old.sources.copy(
                    spreadsheetValues = update.sources.spreadsheetValues
                ),
                lastCachedAt = System.currentTimeMillis()
            )

            CollectionUpdateSource.SLEEVES -> old.copy(
                sleeves = update.sleeves,
                lastCachedAt = System.currentTimeMillis()
            )

            CollectionUpdateSource.FULL -> update.copy(
                lastCachedAt = System.currentTimeMillis()
            )
        }
    }

    private fun mergeGameItems(
        existing: List<GameItem>,
        updates: List<GameItem>,
        source: CollectionUpdateSource
    ): List<GameItem> {
        if (updates.isEmpty()) return existing
        if (source == CollectionUpdateSource.FULL) {
            return updates.map { it.copy(lastCachedAt = System.currentTimeMillis()) }
        }

        val merged = existing.toMutableList()
        val indexById = existing.withIndex()
            .filter { it.value.objectId.isNotBlank() }
            .associate { it.value.objectId to it.index }
            .toMutableMap()
        val indexByName = existing.withIndex()
            .associate { it.value.name.trim().lowercase() to it.index }
            .toMutableMap()

        updates.forEach { update ->
            val existingIndex = indexById[update.objectId]
                ?: indexByName[update.name.trim().lowercase()]
            val existingItem = existingIndex?.let { merged[it] }
            if (existingItem == null) {
                merged += update.copy(lastCachedAt = System.currentTimeMillis())
                val newIndex = merged.lastIndex
                if (update.objectId.isNotBlank()) indexById[update.objectId] = newIndex
                indexByName[update.name.trim().lowercase()] = newIndex
            } else {
                val mergedItem = mergeGameItem(existingItem, update, source)
                merged[existingIndex] = mergedItem
                if (mergedItem.objectId.isNotBlank()) indexById[mergedItem.objectId] = existingIndex
                indexByName[mergedItem.name.trim().lowercase()] = existingIndex
            }
        }

        return merged
    }

    private fun requireBggCredentials(): BggCredentials {
        refreshCredentialState()
        return securePrefs.getCredentials()
            ?: throw IllegalStateException("Set your BGG username and password in Settings first.")
    }

    private fun currentOrCachedCollection(): List<GameItem> {
        val current = _collectionGames.value
        if (current.isNotEmpty()) return current
        return readCanonicalSnapshot()
    }

    private fun mergeSleeveData(games: List<GameItem>, cachedGames: List<GameItem>): List<GameItem> {
        if (games.isEmpty() || cachedGames.isEmpty()) return games
        val byObjectId = cachedGames.associateBy { it.objectId }
        val byName = cachedGames.associateBy { it.name.trim().lowercase() }
        return games.map { game ->
            val cached = byObjectId[game.objectId] ?: byName[game.name.trim().lowercase()]
            if (cached == null || cached.sleeveStatus == GameItem.SleeveStatus.UNKNOWN) {
                game
            } else {
                game.withSleeves(cached.sleeves)
            }
        }
    }

    private fun buildBggCollection(forceRefresh: Boolean): List<GameItem> {
        val bggGames = loadBggCollection(forceRefresh)
        val ownedGames = bggGames.map { bggGame ->
            GameItem(
                identity = GameItem.Identity(
                    objectId = bggGame.objectid,
                    name = bggGame.objectname
                ),
                stats = GameItem.Stats(
                    rank = bggGame.rank.toIntOrNull(),
                    averageRating = bggGame.average.toDoubleOrNull(),
                    bayesAverage = bggGame.baverage.toDoubleOrNull(),
                    weight = bggGame.avgweight.toDoubleOrNull(),
                    yearPublished = bggGame.yearpublished.toIntOrNull(),
                    playingTime = bggGame.playingtime.toIntOrNull(),
                    minPlayTime = bggGame.minplaytime.toIntOrNull(),
                    maxPlayTime = bggGame.maxplaytime.toIntOrNull(),
                    numOwned = bggGame.numowned.toIntOrNull(),
                    languageDependence = bggGame.bgglanguagedependence,
                    language = null
                ),
                players = GameItem.Players(
                    minPlayers = bggGame.minplayers.toIntOrNull(),
                    maxPlayers = bggGame.maxplayers.toIntOrNull(),
                    bestPlayers = bggGame.bggbestplayers,
                    recommendedPlayers = bggGame.bggrecplayers,
                    recommendedAge = bggGame.bggrecagerange
                ),
                ownership = GameItem.Ownership(
                    isOwned = bggGame.own.toBoolFlag(),
                    isWishlisted = bggGame.wishlist.toBoolFlag(),
                    bggPlayCount = bggGame.numplays.toIntOrNull()
                ),
                sleeves = GameItem.Sleeves(),
                media = GameItem.Media(
                    thumbnailUrl = bggGame.thumbnailUrl
                ),
                links = GameItem.Links(
                    bggUrl = bggGame.bggurl,
                    driveUrl = null,
                    qrImageUrl = null
                ),
                sources = GameItem.Sources(
                    spreadsheetValues = emptyMap(),
                    bggValues = bggGame.asMap()
                )
            )
        }
        val credentials = requireBggCredentials()
        val wishlistGames = try {
            BggApiClient().fetchWishlistGameItems(credentials.username, credentials.password)
        } catch (e: Exception) {
            entry("BGG wishlist", e.message ?: "Could not load wishlist", LogEntry.Type.ERROR)
            emptyList()
        }
        val ownedIds = ownedGames.map { it.objectId }.toSet()
        return ownedGames + wishlistGames.filter { it.objectId !in ownedIds }
    }

    private suspend fun buildCanonicalCollectionSnapshot(
        forceRefresh: Boolean,
        refreshSleeves: Boolean,
        preferredAccount: Account? = _account.value
    ): List<GameItem> {
        val mergeBase = readCanonicalSnapshot()
        var merged = emptyList<GameItem>()
        var sheetLoaded = false

        val account = preferredAccount
        val spreadsheetId = _spreadsheetId.value.ifBlank { securePrefs.syncSpreadsheetId }.trim()
        if (account != null && spreadsheetId.isNotBlank()) {
            entry("Google Sheets", "Fetching collection rows", LogEntry.Type.INFO)
            val api = GoogleApiClient(getApplication(), account, spreadsheetId, _sheetTabName.value)
            val sheetGames = api.readCollectionRows()
            merged = mergeGameItems(merged, sheetGames, CollectionUpdateSource.SHEET)
            sheetLoaded = true
            entry("Google Sheets", "${sheetGames.size} rows loaded", LogEntry.Type.INFO)
        } else {
            entry("Google Sheets", "Skipping sheet refresh", LogEntry.Type.INFO)
        }

        val bggGames = buildBggCollection(forceRefresh)
        merged = mergeGameItems(merged, bggGames, CollectionUpdateSource.BGG)
        entry("BGG", "${bggGames.size} games merged", LogEntry.Type.INFO)

        if (!sheetLoaded) {
            merged = mergeGameItems(merged, mergeBase, CollectionUpdateSource.SHEET)
        }
        merged = mergeGameItems(merged, mergeBase, CollectionUpdateSource.SLEEVES)

        if (refreshSleeves && merged.isNotEmpty()) {
            val sleeveUpdates = fetchSleeveUpdates(merged, forceRefresh = false)
            merged = mergeGameItems(merged, sleeveUpdates, CollectionUpdateSource.SLEEVES)
        }

        val (withHistoryCounts, backfilledCount) = backfillMissingBggPlayCountsFromHistory(merged)
        if (backfilledCount > 0) {
            entry("BGG history", "Filled missing play counts for $backfilledCount games", LogEntry.Type.INFO)
        }

        return withHistoryCounts
    }

    private fun backfillMissingBggPlayCountsFromHistory(games: List<GameItem>): Pair<List<GameItem>, Int> {
        if (games.isEmpty()) return games to 0

        val cachedPlays = securePrefs.getBggPlaysCache()
        if (cachedPlays.isEmpty()) return games to 0

        val countsByGameId = cachedPlays
            .groupBy { it.gameId }
            .mapValues { (_, plays) -> plays.sumOf { it.quantity.coerceAtLeast(1) } }

        val countsByName = cachedPlays
            .groupBy { it.gameName.trim().lowercase() }
            .mapValues { (_, plays) -> plays.sumOf { it.quantity.coerceAtLeast(1) } }

        var backfilledCount = 0
        val updatedGames = games.map { game ->
            if (game.ownership.bggPlayCount != null) {
                game
            } else {
                val historyCount = game.objectId.toIntOrNull()
                    ?.let { countsByGameId[it] }
                    ?: countsByName[game.name.trim().lowercase()]

                if (historyCount == null) {
                    game
                } else {
                    backfilledCount++
                    game.copy(
                        ownership = game.ownership.copy(bggPlayCount = historyCount),
                        lastCachedAt = System.currentTimeMillis()
                    )
                }
            }
        }

        return updatedGames to backfilledCount
    }

    private suspend fun fetchSleeveUpdates(
        games: List<GameItem>,
        forceRefresh: Boolean
    ): List<GameItem> {
        val credentials = requireBggCredentials()
        val client = BggApiClient()
        client.loginIfNeeded(credentials.username, credentials.password)
        entry("BGG sleeves", "Checking ${games.size} games", LogEntry.Type.INFO)

        var updated = 0
        var missing = 0
        var failed = 0
        val refreshed = mutableListOf<GameItem>()
        for (game in games) {
            if (!currentCoroutineContext().isActive || game.objectId.isBlank()) {
                refreshed += game
                continue
            }
            val hasCachedSleeves = game.sleeveStatus == GameItem.SleeveStatus.FOUND ||
                game.sleeveStatus == GameItem.SleeveStatus.MISSING
            if (!forceRefresh && hasCachedSleeves) {
                refreshed += game
                continue
            }
            try {
                val info = client.fetchSleeveInfo(
                    gameId = game.objectId,
                    gameName = game.name,
                    bggUrl = game.bggUrl,
                    objectType = game.spreadsheetValues["objecttype"] ?: game.bggValues["objecttype"]
                )
                val sleeves = GameItem.Sleeves(
                    status = info.status,
                    cardSets = info.cardSets,
                    sourceUrl = info.sourceUrl,
                    note = info.note,
                    lastFetchedAt = System.currentTimeMillis()
                )
                when (info.status) {
                    GameItem.SleeveStatus.FOUND -> {
                        updated++
                        entry(game.name, "Sleeve data updated", LogEntry.Type.UPDATED)
                    }
                    GameItem.SleeveStatus.MISSING -> {
                        missing++
                        entry(game.name, "No BGG sleeve data yet", LogEntry.Type.INFO)
                    }
                    GameItem.SleeveStatus.ERROR,
                    GameItem.SleeveStatus.UNKNOWN -> {
                        failed++
                        entry(game.name, info.note ?: "Could not parse sleeve data", LogEntry.Type.ERROR)
                    }
                }
                refreshed += game.withSleeves(sleeves)
            } catch (e: Exception) {
                failed++
                entry(game.name, e.message ?: "Sleeve refresh failed", LogEntry.Type.ERROR)
                refreshed += game
            }
        }

        entry(
            "Sleeve refresh complete",
            "$updated updated  $missing missing  $failed failed",
            if (failed > 0) LogEntry.Type.ERROR else LogEntry.Type.DONE
        )
        return refreshed
    }

    private suspend fun refreshBggPlayHistory() {
        entry("BGG history", "Refreshing play history", LogEntry.Type.INFO)
        refreshBggPlayCache(securePrefs, bggRepository)
            .onSuccess { plays ->
                entry("BGG history", "${plays.size} plays cached", LogEntry.Type.DONE)
            }
            .onFailure {
                entry("BGG history", it.message ?: "Failed to refresh play history", LogEntry.Type.ERROR)
            }
    }

    private fun saveSleevesToSheetIfAvailable(games: List<GameItem>) {
        val account = _account.value ?: return
        val spreadsheetId = _spreadsheetId.value
        val sheetTabName = _sheetTabName.value
        if (spreadsheetId.isBlank()) return
        val gamesWithSleeves = games.filter { it.sleeveStatus != GameItem.SleeveStatus.UNKNOWN }
        if (gamesWithSleeves.isEmpty()) return
        try {
            val api = GoogleApiClient(getApplication(), account, spreadsheetId, sheetTabName)
            val changedRows = api.writeSleevesJsonByObjectId(gamesWithSleeves)
            if (changedRows > 0) {
                entry("Sleeves", "Saved sleeves for $changedRows games to sheet", LogEntry.Type.DONE)
            }
        } catch (e: Exception) {
            entry("Sleeves", e.message ?: "Could not save sleeves to sheet", LogEntry.Type.ERROR)
        }
    }

    private fun readCanonicalSnapshot(): List<GameItem> {
        return securePrefs.getCollectionSnapshot(activeSnapshotId()).ifEmpty {
            migrateLegacySnapshotIfNeeded()
        }
    }

    private fun readCanonicalSnapshotLocked(): List<GameItem> {
        return securePrefs.getCollectionSnapshot(activeSnapshotId()).ifEmpty {
            migrateLegacySnapshotIfNeeded()
        }
    }

    private fun writeCanonicalSnapshotLocked(games: List<GameItem>) {
        securePrefs.saveCollectionSnapshot(activeSnapshotId(), games)
    }

    private fun migrateLegacySnapshotIfNeeded(): List<GameItem> {
        val legacyIds = listOfNotNull(
            _spreadsheetId.value.trim().takeIf { it.isNotBlank() },
            securePrefs.syncSpreadsheetId.trim().takeIf { it.isNotBlank() }
        ).distinct()

        val legacyId = legacyIds.firstOrNull { id ->
            securePrefs.getCollectionSnapshot(id).isNotEmpty()
        } ?: return emptyList()
        val legacySnapshot = securePrefs.getCollectionSnapshot(legacyId)

        securePrefs.saveCollectionSnapshot(activeSnapshotId(), legacySnapshot)
        securePrefs.clearCollectionSnapshot(legacyId)
        return legacySnapshot
    }
}
