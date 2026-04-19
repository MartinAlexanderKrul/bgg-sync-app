package com.bgg.combined

/**
 * Central configuration constants for the Google Sheets sync functionality.
 * Edit BGG_USERNAME and sheet column indices to match your setup.
 */
object SyncConfig {
    const val APP_NAME        = "BoardFlow"
    const val BGG_USERNAME    = "Nicolsburg"
    /** BGG password — injected at build time from the BGG_PASSWORD environment variable. */
    val BGG_PASSWORD: String get() = BuildConfig.BGG_PASSWORD

    const val SHEET_TAB_NAME   = "GAMES"
    const val HEADER_ROW_INDEX = 0
    const val COL_GAME_NAME    = 0   // column A
    const val COL_SHARE_URL    = 24  // column Y
    const val COL_QR_IMAGE     = 25  // column Z

    val OAUTH_SCOPES = listOf(
        "https://www.googleapis.com/auth/drive",
        "https://www.googleapis.com/auth/spreadsheets"
    )

    val PROTECTED_COLUMNS = setOf("language")

    val NUMERIC_COLUMNS = setOf(
        "objectid", "collid", "imageid", "publisherid",
        "own", "fortrade", "want", "wanttobuy", "wanttoplay",
        "prevowned", "preordered", "wishlist", "wishlistpriority",
        "numplays", "quantity",
        "minplayers", "maxplayers",
        "playingtime", "minplaytime", "maxplaytime",
        "yearpublished", "year",
        "rank", "numowned",
        "rating", "baverage", "average", "score", "communityrating",
        "avgweight", "weight"
    )

    val DEFAULT_SHEET_HEADERS = listOf(
        "GAME",
        "sleeved",
        "objectid",
        "collid",
        "baverage",
        "score",
        "avgweight",
        "rank",
        "numowned",
        "objecttype",
        "originalname",
        "minplayers",
        "maxplayers",
        "playingtime",
        "maxplaytime",
        "minplaytime",
        "yearpublished",
        "bggrecplayers",
        "bggbestplayers",
        "bggrecagerange",
        "bgglanguagedependence",
        "language",
        "price",
        "orig price",
        "sleeves",
        "drive",
        "qr"
    )

    data class SheetStyleConfig(
        val frozenRowCount: Int,
        val headerBackgroundHex: String,
        val headerTextHex: String,
        val columnWidths: Map<String, Int>,
        val conditionalFormats: List<ConditionalFormatConfig>
    )

    data class ConditionalFormatConfig(
        val column: String,
        val type: ConditionalFormatType,
        val values: List<String> = emptyList(),
        val backgroundHex: String? = null,
        val textHex: String? = null,
        val bold: Boolean = false
    )

    enum class ConditionalFormatType {
        TRUE_FLAG,
        NOT_BLANK,
        NUMBER_GREATER_THAN,
        NUMBER_LESS_THAN,
        NUMBER_BETWEEN,
        TEXT_CONTAINS,
        TEXT_DOES_NOT_CONTAIN,
        TEXT_EQUAL,
        CUSTOM_FORMULA
    }

    val DEFAULT_SHEET_STYLE = SheetStyleConfig(
        frozenRowCount = 1,
        headerBackgroundHex = "#FFFFFF",
        headerTextHex = "#202124",
        columnWidths = mapOf(
            "game" to 360,
            "sleeved" to 72,
            "objectid" to 100,
            "collid" to 80,
            "baverage" to 82,
            "score" to 82,
            "avgweight" to 88,
            "rank" to 80,
            "numowned" to 90,
            "objecttype" to 110,
            "originalname" to 180,
            "minplayers" to 86,
            "maxplayers" to 86,
            "playingtime" to 96,
            "maxplaytime" to 96,
            "minplaytime" to 96,
            "yearpublished" to 92,
            "bggrecplayers" to 96,
            "bggbestplayers" to 96,
            "bggrecagerange" to 88,
            "bgglanguagedependence" to 320,
            "language" to 68,
            "price" to 92,
            "orig price" to 96,
            "sleeves" to 220,
            "drive" to 170,
            "qr" to 72
        ),
        conditionalFormats = listOf(
            ConditionalFormatConfig(
                column = "score",
                type = ConditionalFormatType.NUMBER_GREATER_THAN,
                values = listOf("8"),
                backgroundHex = "#415D2A",
                textHex = "#FFFFFF"
            ),
            ConditionalFormatConfig(
                column = "score",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("7.51", "8"),
                backgroundHex = "#5F8F39",
                textHex = "#FFFFFF"
            ),
            ConditionalFormatConfig(
                column = "score",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("6.89", "7.51"),
                backgroundHex = "#A8D08D"
            ),
            ConditionalFormatConfig(
                column = "score",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("6", "6.9"),
                backgroundHex = "#DDEBD3"
            ),
            ConditionalFormatConfig(
                column = "avgweight",
                type = ConditionalFormatType.NUMBER_LESS_THAN,
                values = listOf("1.5"),
                backgroundHex = "#DDEBF7"
            ),
            ConditionalFormatConfig(
                column = "avgweight",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("1.5", "1.9999"),
                backgroundHex = "#BDD7EE"
            ),
            ConditionalFormatConfig(
                column = "avgweight",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("2", "2.2999"),
                backgroundHex = "#9DC3E6"
            ),
            ConditionalFormatConfig(
                column = "avgweight",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("2.3", "2.95"),
                backgroundHex = "#5B9BD5",
                textHex = "#FFFFFF"
            ),
            ConditionalFormatConfig(
                column = "avgweight",
                type = ConditionalFormatType.NUMBER_GREATER_THAN,
                values = listOf("2.95"),
                backgroundHex = "#1F4E79",
                textHex = "#FFFFFF"
            ),
            ConditionalFormatConfig(
                column = "minplayers",
                type = ConditionalFormatType.NUMBER_LESS_THAN,
                values = listOf("3"),
                backgroundHex = "#E7E6E6"
            ),
            ConditionalFormatConfig(
                column = "minplayers",
                type = ConditionalFormatType.NUMBER_GREATER_THAN,
                values = listOf("2"),
                textHex = "#FF0000"
            ),
            ConditionalFormatConfig(
                column = "playingtime",
                type = ConditionalFormatType.NUMBER_GREATER_THAN,
                values = listOf("90"),
                backgroundHex = "#8C4A08",
                textHex = "#FFFFFF"
            ),
            ConditionalFormatConfig(
                column = "playingtime",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("61", "90"),
                backgroundHex = "#D46A0E"
            ),
            ConditionalFormatConfig(
                column = "playingtime",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("31", "60"),
                backgroundHex = "#F4B183"
            ),
            ConditionalFormatConfig(
                column = "playingtime",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("0", "30"),
                backgroundHex = "#F8CBAD"
            ),
            ConditionalFormatConfig(
                column = "bggrecplayers",
                type = ConditionalFormatType.TEXT_CONTAINS,
                values = listOf("2"),
                backgroundHex = "#92D050"
            ),
            ConditionalFormatConfig(
                column = "bggbestplayers",
                type = ConditionalFormatType.TEXT_CONTAINS,
                values = listOf("2"),
                backgroundHex = "#FFFF00"
            ),
            ConditionalFormatConfig(
                column = "bggbestplayers",
                type = ConditionalFormatType.CUSTOM_FORMULA,
                values = listOf("""=AND(LEN(TO_TEXT(INDIRECT(ADDRESS(ROW(),COLUMN()))))>0,NOT(REGEXMATCH(TO_TEXT(INDIRECT(ADDRESS(ROW(),COLUMN()))),"2")))"""),
                backgroundHex = "#FF0000",
                textHex = "#000000"
            ),
            ConditionalFormatConfig(
                column = "price",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("0", "350"),
                backgroundHex = "#FFF2CC"
            ),
            ConditionalFormatConfig(
                column = "price",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("351", "650"),
                backgroundHex = "#FFE699"
            ),
            ConditionalFormatConfig(
                column = "price",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("651", "999"),
                backgroundHex = "#FFD966"
            ),
            ConditionalFormatConfig(
                column = "price",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("1000", "1450"),
                backgroundHex = "#F1C232"
            ),
            ConditionalFormatConfig(
                column = "price",
                type = ConditionalFormatType.NUMBER_BETWEEN,
                values = listOf("1451", "1999"),
                backgroundHex = "#D6AE01"
            ),
            ConditionalFormatConfig(
                column = "price",
                type = ConditionalFormatType.NUMBER_GREATER_THAN,
                values = listOf("1999"),
                backgroundHex = "#9E7D00",
                textHex = "#FFFFFF"
            ),
            ConditionalFormatConfig(
                column = "sleeved",
                type = ConditionalFormatType.TEXT_EQUAL,
                values = listOf("TRUE"),
                textHex = "#6AA84F",
                bold = true
            ),
            ConditionalFormatConfig(
                column = "sleeved",
                type = ConditionalFormatType.TEXT_EQUAL,
                values = listOf("!"),
                backgroundHex = "#FF0000",
                textHex = "#FFFFFF",
                bold = true
            ),
            ConditionalFormatConfig(
                column = "sleeved",
                type = ConditionalFormatType.TEXT_EQUAL,
                values = listOf("?"),
                textHex = "#FF0000",
                bold = true
            ),
            ConditionalFormatConfig(
                column = "language",
                type = ConditionalFormatType.TEXT_EQUAL,
                values = listOf("CZ"),
                textHex = "#FF0000",
                bold = true
            ),
            ConditionalFormatConfig(
                column = "drive",
                type = ConditionalFormatType.NOT_BLANK,
                textHex = "#1155CC"
            ),
            ConditionalFormatConfig(
                column = "qr",
                type = ConditionalFormatType.NOT_BLANK,
                backgroundHex = "#F3F3F3"
            )
        )
    )
}
