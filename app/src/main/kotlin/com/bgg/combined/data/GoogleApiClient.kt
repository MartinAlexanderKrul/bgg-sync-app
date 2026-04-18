package com.bgg.combined.data

import android.accounts.Account
import android.content.Context
import com.bgg.combined.SyncConfig
import com.bgg.combined.model.GameItem
import com.bgg.combined.model.SpreadsheetDetails
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.InsertDimensionRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.ValueRange

private fun String?.toBoolFlag(): Boolean {
    if (this == null) return false
    val s = this.trim().lowercase()
    return s == "1" || s == "1.0" || s == "true" || s == "yes"
}

class GoogleApiClient(
    private val context: Context,
    account: Account,
    private val spreadsheetId: String,
    private val sheetTabName: String = SyncConfig.SHEET_TAB_NAME
) {
    private val credential = GoogleAccountCredential
        .usingOAuth2(context, SyncConfig.OAUTH_SCOPES)
        .also { it.selectedAccount = account }

    private val transport   = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private val drive: Drive = Drive.Builder(transport, jsonFactory, credential)
        .setApplicationName(SyncConfig.APP_NAME).build()

    private val sheets: Sheets = Sheets.Builder(transport, jsonFactory, credential)
        .setApplicationName(SyncConfig.APP_NAME).build()

    private var rootFolderId: String? = null
    private var lastGameFolderId: String? = null
    private var lastQrFileId: String? = null
    private val spreadsheetMetadata by lazy(LazyThreadSafetyMode.NONE) {
        retry { sheets.spreadsheets().get(spreadsheetId).execute() }
    }
    private val resolvedSheetTabName by lazy(LazyThreadSafetyMode.NONE) {
        sheetTabName.takeIf { it.isNotBlank() }
            ?: spreadsheetMetadata.sheets.firstOrNull()?.properties?.title
            ?: SyncConfig.SHEET_TAB_NAME
    }

    fun getSpreadsheetDetails(): SpreadsheetDetails {
        val firstSheetTitle = spreadsheetMetadata.sheets.firstOrNull()?.properties?.title
            ?: throw IllegalStateException("Spreadsheet has no sheets.")
        return SpreadsheetDetails(
            id = spreadsheetId,
            title = spreadsheetMetadata.properties?.title ?: spreadsheetId,
            firstSheetTitle = firstSheetTitle,
            webViewUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit"
        )
    }

    fun writeHeaderRow(headers: List<String>) {
        batchWrite(
            listOf(
                ValueRange()
                    .setRange("${activeSheetName()}!1:1")
                    .setValues(listOf(headers))
            )
        )
    }

    fun readHeaderMap(): Map<String, Int> {
        val range = "${activeSheetName()}!1:1"
        val response = retry { sheets.spreadsheets().values().get(spreadsheetId, range).execute() }
        val map = mutableMapOf<String, Int>()
        val headers = response.getValues()?.firstOrNull() ?: return map
        headers.forEachIndexed { i, h -> if (h != null) map[h.toString().trim().lowercase()] = i }
        return map
    }

    fun readAllColumns(): List<List<Any>> {
        val range = "${activeSheetName()}!A1:ZZ"
        val response = retry { sheets.spreadsheets().values().get(spreadsheetId, range).execute() }
        @Suppress("UNCHECKED_CAST")
        return (response.getValues() as? List<List<Any>>) ?: emptyList()
    }

    fun readCollectionRows(): List<GameItem> {
        val headerMap = readHeaderMap()
        val allRows   = readAllColumns()

        fun colVal(row: List<Any>, vararg names: String): String? {
            for (n in names) {
                val idx = headerMap[n.lowercase()] ?: continue
                val v   = row.getOrNull(idx)?.toString()?.trim() ?: continue
                if (v.isNotBlank()) return v
            }
            return null
        }

        fun rowMap(row: List<Any>): Map<String, String> {
            val mapped = linkedMapOf<String, String>()
            headerMap.forEach { (header, index) ->
                mapped[header] = row.getOrNull(index)?.toString()?.trim().orEmpty()
            }
            return mapped
        }

        return allRows.drop(SyncConfig.HEADER_ROW_INDEX + 1).mapNotNull { row ->
            val name = colVal(row, "objectname", "game", "name", "title") ?: return@mapNotNull null
            val sourceValues = rowMap(row)
            val bggValues = sourceValues.filterKeys {
                it.startsWith("bgg") || it in setOf(
                    "objectid",
                    "objectname",
                    "yearpublished",
                    "minplayers",
                    "maxplayers",
                    "playingtime",
                    "minplaytime",
                    "maxplaytime",
                    "rank",
                    "average",
                    "baverage",
                    "numowned",
                    "avgweight",
                    "thumbnail"
                )
            }
            GameItem(
                identity = GameItem.Identity(
                    objectId = colVal(row, "objectid") ?: "",
                    name = name
                ),
                stats = GameItem.Stats(
                    rank = colVal(row, "rank")?.toIntOrNull(),
                    averageRating = colVal(row, "average", "score", "communityrating")?.toDoubleOrNull(),
                    bayesAverage = colVal(row, "baverage", "bayesaverage")?.toDoubleOrNull(),
                    weight = colVal(row, "avgweight", "weight")?.toDoubleOrNull(),
                    yearPublished = colVal(row, "yearpublished", "year")?.toIntOrNull(),
                    playingTime = colVal(row, "playingtime", "maxplaytime")?.toIntOrNull(),
                    minPlayTime = colVal(row, "minplaytime")?.toIntOrNull(),
                    maxPlayTime = colVal(row, "maxplaytime")?.toIntOrNull(),
                    numOwned = colVal(row, "numowned")?.toIntOrNull(),
                    languageDependence = colVal(row, "bgglanguagedependence", "languagedependence"),
                    language = colVal(row, "language")
                ),
                players = GameItem.Players(
                    minPlayers = colVal(row, "minplayers")?.toIntOrNull(),
                    maxPlayers = colVal(row, "maxplayers")?.toIntOrNull(),
                    bestPlayers = colVal(row, "bggbestplayers"),
                    recommendedPlayers = colVal(row, "bggrecplayers"),
                    recommendedAge = colVal(row, "bggrecagerange")
                ),
                ownership = GameItem.Ownership(
                    isOwned = colVal(row, "own")?.trim().toBoolFlag(),
                    isWishlisted = colVal(row, "wishlist")?.trim().toBoolFlag(),
                    sheetPlayCount = colVal(row, "numplays")?.toIntOrNull()
                ),
                media = GameItem.Media(
                    thumbnailUrl = colVal(row, "thumbnail")?.let { if (it.startsWith("//")) "https:$it" else it }
                ),
                links = GameItem.Links(
                    bggUrl = colVal(row, "bggurl") ?: (colVal(row, "objectid")?.takeIf { it.isNotBlank() }?.let { "https://boardgamegeek.com/boardgame/$it" }),
                    driveUrl = colVal(row, "shareurl", "share_url", "share url")
                        ?: row.getOrNull(SyncConfig.COL_SHARE_URL)?.toString()?.trim()?.ifBlank { null },
                    qrImageUrl = colVal(row, "qrimage", "qr_image", "qr image")
                ),
                sources = GameItem.Sources(
                    spreadsheetValues = sourceValues,
                    bggValues = bggValues
                )
            )
        }
    }

    fun readGameRows(): List<GameRow> {
        val headerMap = readHeaderMap()
        val values = readAllColumns()
        val nameIndex = headerMap["objectname"]
            ?: headerMap["game"]
            ?: headerMap["name"]
            ?: headerMap["title"]
            ?: SyncConfig.COL_GAME_NAME
        val shareUrlIndex = headerMap["shareurl"]
            ?: headerMap["share_url"]
            ?: headerMap["share url"]
            ?: SyncConfig.COL_SHARE_URL
        return values.mapIndexedNotNull { i, row ->
            if (i <= SyncConfig.HEADER_ROW_INDEX) return@mapIndexedNotNull null
            val name = row.getOrNull(nameIndex)?.toString()?.trim() ?: ""
            if (name.isBlank()) return@mapIndexedNotNull null
            val url  = row.getOrNull(shareUrlIndex)?.toString() ?: ""
            GameRow(i, name, url)
        }
    }

    fun writeCsvRow(rowIndex: Int, csvRow: Map<String, String>, headerMap: Map<String, Int>, existingRow: List<Any>) {
        val updates = mutableListOf<ValueRange>(); val sheetsRow = rowIndex + 1
        for ((csvCol, value) in csvRow) {
            if (value.isBlank()) continue
            val candidates = mutableListOf(csvCol.lowercase())
            FIELD_ALIASES[csvCol.lowercase()]?.let { candidates.addAll(it) }
            for (candidate in candidates) {
                val colIdx = headerMap[candidate] ?: continue
                if (isProtected(candidate) && hasValue(existingRow, colIdx)) break
                updates.add(ValueRange().setRange("${activeSheetName()}!${colLetter(colIdx)}$sheetsRow").setValues(listOf(listOf(toSheetValue(candidate, value)))))
                break
            }
        }
        batchWrite(updates)
    }

    fun writeBggRow(rowIndex: Int, game: BggApiClient.BggGame, headerMap: Map<String, Int>, existingRow: List<Any>) {
        val updates = mutableListOf<ValueRange>(); val sheetsRow = rowIndex + 1
        for ((field, value) in game.asMap()) {
            if (value.isBlank()) continue
            val candidates = mutableListOf(field.lowercase())
            FIELD_ALIASES[field.lowercase()]?.let { candidates.addAll(it) }
            for (candidate in candidates) {
                val colIdx = headerMap[candidate] ?: continue
                if (isProtected(candidate) && hasValue(existingRow, colIdx)) break
                updates.add(ValueRange().setRange("${activeSheetName()}!${colLetter(colIdx)}$sheetsRow").setValues(listOf(listOf(toSheetValue(candidate, value)))))
                break
            }
        }
        batchWrite(updates)
    }

    fun writeResultToRow(rowIndex: Int, shareUrl: String, qrFileUrl: String) {
        val sheetsRow = rowIndex + 1
        val headerMap = readHeaderMap()
        val shareUrlColumn = headerMap["shareurl"] ?: headerMap["share_url"] ?: headerMap["share url"] ?: SyncConfig.COL_SHARE_URL
        val qrImageColumn = headerMap["qrimage"] ?: headerMap["qr_image"] ?: headerMap["qr image"] ?: SyncConfig.COL_QR_IMAGE
        batchWrite(listOf(ValueRange().setRange("${activeSheetName()}!${colLetter(shareUrlColumn)}$sheetsRow").setValues(listOf(listOf(shareUrl)))))
        batchWriteUserEntered(listOf(ValueRange().setRange("${activeSheetName()}!${colLetter(qrImageColumn)}$sheetsRow").setValues(listOf(listOf("=IMAGE(\"$qrFileUrl\")")))))
    }

    fun insertRowAfterHeader(): Int {
        val insertAt = SyncConfig.HEADER_ROW_INDEX + 1
        val insert = InsertDimensionRequest()
            .setRange(DimensionRange().setSheetId(getSheetId()).setDimension("ROWS").setStartIndex(insertAt).setEndIndex(insertAt + 1))
            .setInheritFromBefore(false)
        retry { sheets.spreadsheets().batchUpdate(spreadsheetId, BatchUpdateSpreadsheetRequest().setRequests(listOf(Request().setInsertDimension(insert)))).execute() }
        return insertAt
    }

    fun createSharedFolder(title: String): String {
        val rootId = getRootFolderId()
        val escaped = title.replace("\\", "\\\\").replace("'", "\\'")
        val query = "mimeType='${FOLDER_MIME}' and name='$escaped' and '$rootId' in parents and trashed=false"
        val existing = retry { drive.files().list().setQ(query).setFields("files(id,webViewLink)").setPageSize(1).execute() }
        if (existing.files.isNotEmpty()) { lastGameFolderId = existing.files[0].id; lastQrFileId = findQrFileId(lastGameFolderId!!); return existing.files[0].webViewLink }
        val meta = File().setName(title).setMimeType(FOLDER_MIME).setParents(listOf(rootId))
        val folder = retry { drive.files().create(meta).setFields("id,webViewLink").execute() }
        retry { drive.permissions().create(folder.id, Permission().setType("anyone").setRole("reader")).execute() }
        lastGameFolderId = folder.id; lastQrFileId = null
        return folder.webViewLink
    }

    fun uploadQr(gameName: String, qrPng: ByteArray) {
        val folderId = lastGameFolderId ?: throw IllegalStateException("No game folder — call createSharedFolder first.")
        if (lastQrFileId != null) return
        val existing = findQrFileId(folderId)
        if (existing != null) { lastQrFileId = existing; return }
        val qrMeta = File().setName("qr.png").setParents(listOf(folderId))
        val uploaded = retry { drive.files().create(qrMeta, ByteArrayContent("image/png", qrPng)).setFields("id").execute() }
        lastQrFileId = uploaded.id
    }

    fun getLastQrFileUrl(): String {
        val id = lastQrFileId ?: throw IllegalStateException("No QR uploaded yet.")
        return "https://drive.google.com/uc?export=view&id=$id"
    }

    fun downloadQrBytes(): ByteArray? {
        val id = lastQrFileId ?: return null
        val baos = java.io.ByteArrayOutputStream()
        retry { drive.files().get(id).executeMediaAndDownloadTo(baos) }
        return baos.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun getRootFolderId(): String {
        rootFolderId?.let { return it }
        val query = "mimeType='${FOLDER_MIME}' and name='BoardGames' and trashed=false"
        val result = retry { drive.files().list().setQ(query).setFields("files(id)").setPageSize(1).execute() }
        if (result.files.isNotEmpty()) { rootFolderId = result.files[0].id; return rootFolderId!! }
        val meta = File().setName("BoardGames").setMimeType(FOLDER_MIME)
        val created = retry { drive.files().create(meta).setFields("id").execute() }
        retry { drive.permissions().create(created.id, Permission().setType("anyone").setRole("reader")).execute() }
        rootFolderId = created.id; return rootFolderId!!
    }

    private fun findQrFileId(folderId: String): String? {
        val q = "name='qr.png' and '$folderId' in parents and trashed=false"
        val fl = retry { drive.files().list().setQ(q).setFields("files(id)").setPageSize(1).execute() }
        return fl.files.firstOrNull()?.id
    }

    private fun getSheetId(): Int {
        return spreadsheetMetadata.sheets
            .first { it.properties.title.equals(activeSheetName(), ignoreCase = true) }
            .properties.sheetId
    }

    private fun activeSheetName(): String = resolvedSheetTabName

    private fun batchWrite(updates: List<ValueRange>) {
        if (updates.isEmpty()) return
        val req = BatchUpdateValuesRequest().setValueInputOption("RAW").setData(updates)
        retryWrite { sheets.spreadsheets().values().batchUpdate(spreadsheetId, req).execute() }
    }

    private fun batchWriteUserEntered(updates: List<ValueRange>) {
        if (updates.isEmpty()) return
        val req = BatchUpdateValuesRequest().setValueInputOption("USER_ENTERED").setData(updates)
        retryWrite { sheets.spreadsheets().values().batchUpdate(spreadsheetId, req).execute() }
    }

    private fun <T> retry(block: () -> T): T {
        var attempt = 0; var waitMs = RETRY_INITIAL_WAIT_MS
        while (true) {
            try { return block() } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 429 && attempt < MAX_RETRIES) { attempt++; Thread.sleep(waitMs); waitMs *= 2 } else throw e
            }
        }
    }

    private fun retryWrite(block: () -> Unit) { retry<Unit> { block() } }

    private fun colLetter(col: Int): String {
        var n = col + 1; val sb = StringBuilder()
        while (n > 0) { n--; sb.insert(0, ('A' + n % 26)); n /= 26 }
        return sb.toString()
    }

    private fun isProtected(col: String) = SyncConfig.PROTECTED_COLUMNS.contains(col.lowercase())
    private fun hasValue(row: List<Any>, idx: Int) = idx < row.size && row[idx].toString().isNotBlank()

    private fun toSheetValue(col: String, value: String): Any {
        if (!SyncConfig.NUMERIC_COLUMNS.contains(col.lowercase())) return value
        return try { val d = value.toDouble(); if (d == Math.floor(d) && !d.isInfinite()) d.toLong() else d }
        catch (_: NumberFormatException) { value }
    }

    companion object {
        private const val FOLDER_MIME           = "application/vnd.google-apps.folder"
        private const val MAX_RETRIES           = 5
        private const val RETRY_INITIAL_WAIT_MS = 15_000L
        private val FIELD_ALIASES = mapOf(
            "objectname"            to listOf("game", "name", "title"),
            "average"               to listOf("score", "communityrating"),
            "baverage"              to listOf("bayesaverage"),
            "bgglanguagedependence" to listOf("language", "languagedependence", "language dependence"),
            "weight"                to listOf("avgweight"),
            "avgweight"             to listOf("weight")
        )

        fun createSpreadsheet(
            context: Context,
            account: Account,
            title: String,
            sheetTitle: String,
            headers: List<String>
        ): SpreadsheetDetails {
            val credential = GoogleAccountCredential
                .usingOAuth2(context, SyncConfig.OAUTH_SCOPES)
                .also { it.selectedAccount = account }
            val transport = NetHttpTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val sheets = Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName(SyncConfig.APP_NAME)
                .build()

            val spreadsheet = Spreadsheet().apply {
                properties = com.google.api.services.sheets.v4.model.SpreadsheetProperties().setTitle(title)
                this.sheets = listOf(
                    Sheet().setProperties(
                        SheetProperties().setTitle(sheetTitle)
                    )
                )
            }
            val created = sheets.spreadsheets().create(spreadsheet).execute()
            val id = created.spreadsheetId ?: throw IllegalStateException("Google Sheets did not return a spreadsheet ID.")
            val firstSheetTitle = created.sheets.firstOrNull()?.properties?.title ?: sheetTitle
            GoogleApiClient(context, account, id, firstSheetTitle).writeHeaderRow(headers)
            return SpreadsheetDetails(
                id = id,
                title = created.properties?.title ?: title,
                firstSheetTitle = firstSheetTitle,
                webViewUrl = created.spreadsheetUrl
            )
        }
    }

    data class GameRow(val rowIndex: Int, val gameName: String, val shareUrl: String)
}
