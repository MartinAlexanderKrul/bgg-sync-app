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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val BGG_ONLY_SNAPSHOT_ID = "__bgg_only__"
    }

    private val securePrefs = SecurePreferences(app)

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
        if (syncJob?.isActive == true) {
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
            if (syncJob?.isActive != true) break
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
            if (syncJob?.isActive != true) break
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
        if (syncJob?.isActive == true) {
            loadCollection(account, forceRefresh = true)
        }
    }

    fun refreshCollectionFromBgg(forceRefresh: Boolean = true) = runSync("BGG Collection Refresh") {
        val games = buildBggOnlyCollection(forceRefresh)
        _collectionGames.value = games
        securePrefs.saveCollectionSnapshot(BGG_ONLY_SNAPSHOT_ID, games)
        entry("Collection cached", "${games.size} games ready in the app", LogEntry.Type.DONE)
        launch { BggImageCache.preloadAll(getApplication(), games) }
    }

    fun refreshSleeveDataFromBgg(forceRefresh: Boolean = true) = runSync("BGG Sleeve Refresh") {
        val credentials = requireBggCredentials()
        val existingGames = currentOrCachedCollection()
        require(existingGames.isNotEmpty()) { "Refresh your collection from BGG first." }

        val client = BggApiClient()
        client.loginIfNeeded(credentials.username, credentials.password)
        entry("BGG sleeves", "Checking ${existingGames.size} games", LogEntry.Type.INFO)

        var updated = 0
        var missing = 0
        var failed = 0
        val refreshed = existingGames.map { game ->
            if (!isActive || game.objectId.isBlank()) return@map game
            val hasCachedSleeves = game.sleeveStatus == GameItem.SleeveStatus.FOUND || game.sleeveStatus == GameItem.SleeveStatus.MISSING
            if (!forceRefresh && hasCachedSleeves) return@map game
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
                game.withSleeves(sleeves)
            } catch (e: Exception) {
                failed++
                entry(game.name, e.message ?: "Sleeve refresh failed", LogEntry.Type.ERROR)
                game.withSleeves(
                    game.sleeves.copy(
                        status = GameItem.SleeveStatus.ERROR,
                        note = e.message ?: "Sleeve refresh failed",
                        lastFetchedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        _collectionGames.value = refreshed
        saveCollectionSnapshot(refreshed)
        saveSleevesToSheetIfAvailable(refreshed)
        entry(
            "Sleeve refresh complete",
            "$updated updated  $missing missing  $failed failed",
            if (failed > 0) LogEntry.Type.ERROR else LogEntry.Type.DONE
        )
    }

    fun loadCollection(account: Account, forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _collectionLoading.value = true
            _collectionError.value = null
            val snapshotId = activeSnapshotId()
            val cached = if (!forceRefresh && snapshotId.isNotBlank()) {
                applyHistoryPlayCounts(securePrefs.getCollectionSnapshot(snapshotId))
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
                val credentials = securePrefs.getCredentials()
                val snapshotForMerge = securePrefs.getCollectionSnapshot(snapshotId)
                val bgg = BggApiClient()
                val sheetGames = api.readCollectionRows()
                val initial = mergeSleeveData(applyHistoryPlayCounts(sheetGames), snapshotForMerge)
                _collectionGames.value = initial
                _collectionLoading.value = false
                if (credentials == null) {
                    securePrefs.saveCollectionSnapshot(snapshotId, initial)
                    return@launch
                }

                try {
                    bgg.loginIfNeeded(credentials.username, credentials.password)
                } catch (_: Exception) {
                }
                val thumbDeferred = async {
                    try {
                        bgg.fetchOwnedThumbnails(credentials.username, credentials.password)
                    } catch (_: Exception) {
                        emptyMap<String, String>()
                    }
                }
                val wishlistDeferred = async {
                    try {
                        bgg.fetchWishlistGameItems(credentials.username, credentials.password)
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
                val merged = enrichedSheet + wishlistGames.filter { it.objectId !in sheetIds }
                val enriched = mergeSleeveData(applyHistoryPlayCounts(merged), snapshotForMerge)
                _collectionGames.value = enriched
                securePrefs.saveCollectionSnapshot(snapshotId, enriched)
                launch { BggImageCache.preloadAll(getApplication(), enriched) }
            } catch (e: Exception) {
                if (_collectionGames.value.isEmpty()) {
                    _collectionError.value = e.message ?: "Failed to load collection"
                    _collectionLoading.value = false
                }
            }
        }
    }

    fun loadCachedCollection() {
        viewModelScope.launch(Dispatchers.IO) {
            _collectionLoading.value = true
            _collectionError.value = null
            val snapshotId = activeSnapshotId()
            val cached = if (snapshotId.isNotBlank()) {
                applyHistoryPlayCounts(securePrefs.getCollectionSnapshot(snapshotId))
            } else {
                emptyList()
            }
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
        val current = _log.value.toMutableList()
        current.add(LogEntry(name, status, type))
        _log.value = current
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
        return if (!forceRefresh && cache.exists()) {
            entry("BGG cache", "Loading from local cache", LogEntry.Type.INFO)
            cache.load()
        } else {
            if (forceRefresh) cache.delete()
            entry("BGG API", "Fetching collection...", LogEntry.Type.INFO)
            val games = BggApiClient().fetchCollection(credentials.username, credentials.password)
            cache.save(games)
            entry("BGG API", "${games.size} games fetched and cached", LogEntry.Type.INFO)
            games
        }
    }

    private fun syncCollectionToSheet(api: GoogleApiClient, collection: List<BggApiClient.BggGame>) {
        val headerMap = api.readHeaderMap()
        val allRows = api.readAllColumns()
        val objectidCol = headerMap["objectid"]
            ?: throw IllegalStateException("No 'objectid' column in the first sheet.")
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

    private fun activeSnapshotId(): String {
        val configuredSheetId = _spreadsheetId.value.ifBlank { securePrefs.syncSpreadsheetId }.trim()
        return if (configuredSheetId.isNotBlank()) configuredSheetId else BGG_ONLY_SNAPSHOT_ID
    }

    private fun buildBggOnlyCollection(forceRefresh: Boolean): List<GameItem> {
        val credentials = requireBggCredentials()
        val snapshotForMerge = securePrefs.getCollectionSnapshot(BGG_ONLY_SNAPSHOT_ID)
        val bgg = BggApiClient()
        val baseCollection = loadBggCollection(forceRefresh)
        val thumbnails = try {
            bgg.fetchOwnedThumbnails(credentials.username, credentials.password)
        } catch (_: Exception) {
            emptyMap()
        }
        val wishlistGames = try {
            bgg.fetchWishlistGameItems(credentials.username, credentials.password)
        } catch (_: Exception) {
            emptyList()
        }
        val ownedGames = baseCollection.map { game ->
            GameItem(
                identity = GameItem.Identity(
                    objectId = game.objectid,
                    name = game.objectname
                ),
                stats = GameItem.Stats(
                    rank = game.rank.toIntOrNull(),
                    averageRating = game.average.toDoubleOrNull(),
                    bayesAverage = game.baverage.toDoubleOrNull(),
                    weight = game.avgweight.toDoubleOrNull(),
                    yearPublished = game.yearpublished.toIntOrNull(),
                    playingTime = game.playingtime.toIntOrNull(),
                    minPlayTime = game.minplaytime.toIntOrNull(),
                    maxPlayTime = game.maxplaytime.toIntOrNull(),
                    numOwned = game.numowned.toIntOrNull(),
                    languageDependence = game.bgglanguagedependence.ifBlank { null },
                    language = null
                ),
                players = GameItem.Players(
                    minPlayers = game.minplayers.toIntOrNull(),
                    maxPlayers = game.maxplayers.toIntOrNull(),
                    bestPlayers = game.bggbestplayers.ifBlank { null },
                    recommendedPlayers = game.bggrecplayers.ifBlank { null },
                    recommendedAge = game.bggrecagerange.ifBlank { null }
                ),
                ownership = GameItem.Ownership(
                    isOwned = game.own == "1" || game.own.equals("true", ignoreCase = true),
                    isWishlisted = game.wishlist == "1" || game.wishlist.equals("true", ignoreCase = true),
                    sheetPlayCount = null
                ),
                sleeves = GameItem.Sleeves(),
                media = GameItem.Media(
                    thumbnailUrl = thumbnails[game.objectid]
                ),
                links = GameItem.Links(
                    bggUrl = game.bggurl.ifBlank { null },
                    driveUrl = null,
                    qrImageUrl = null
                ),
                sources = GameItem.Sources(
                    spreadsheetValues = emptyMap(),
                    bggValues = game.asMap()
                )
            )
        }
        val ownedIds = ownedGames.map { it.objectId }.toSet()
        return mergeSleeveData(
            applyHistoryPlayCounts(ownedGames + wishlistGames.filter { it.objectId !in ownedIds }),
            snapshotForMerge
        )
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

    private fun requireBggCredentials(): BggCredentials {
        refreshCredentialState()
        return securePrefs.getCredentials()
            ?: throw IllegalStateException("Set your BGG username and password in Settings first.")
    }

    private fun currentOrCachedCollection(): List<GameItem> {
        val current = _collectionGames.value
        if (current.isNotEmpty()) return current
        val cached = securePrefs.getCollectionSnapshot(activeSnapshotId())
        if (cached.isNotEmpty()) return cached
        return securePrefs.getCollectionSnapshot(BGG_ONLY_SNAPSHOT_ID)
    }

    private fun saveCollectionSnapshot(games: List<GameItem>) {
        val activeId = activeSnapshotId()
        securePrefs.saveCollectionSnapshot(activeId, games)
        if (activeId != BGG_ONLY_SNAPSHOT_ID) {
            securePrefs.saveCollectionSnapshot(BGG_ONLY_SNAPSHOT_ID, games)
        }
    }

    private fun saveSleevesToSheetIfAvailable(games: List<GameItem>) {
        val account = _account.value ?: return
        val spreadsheetId = _spreadsheetId.value.ifBlank { securePrefs.syncSpreadsheetId }.trim()
        if (spreadsheetId.isBlank()) return
        try {
            val api = GoogleApiClient(getApplication(), account, spreadsheetId, _sheetTabName.value)
            api.writeSleevesJsonByObjectId(games)
            entry("Sheet updated", "Sleeves JSON saved to connected sheet", LogEntry.Type.INFO)
        } catch (e: Exception) {
            entry("Sheet update", e.message ?: "Could not save sleeves to sheet", LogEntry.Type.ERROR)
        }
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
}
