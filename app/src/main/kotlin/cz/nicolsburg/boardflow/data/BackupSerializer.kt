package cz.nicolsburg.boardflow.data

import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.PlayerResult
import org.json.JSONArray
import org.json.JSONObject

data class ImportedBackupData(
    val collectionSnapshot: List<GameItem>,
    val loggedPlays: List<LoggedPlay>,
    val cachedBggPlays: List<LoggedPlay>
)

object BackupSerializer {
    private const val CANONICAL_SNAPSHOT_ID = "__canonical_collection__"

    fun export(
        includeSensitiveData: Boolean,
        bggUsername: String,
        bggPassword: String,
        bggXmlApiToken: String,
        geminiApiKey: String,
        geminiModelEndpoint: String,
        appTheme: String,
        sheetTabName: String,
        syncSpreadsheetId: String,
        syncSheetTabName: String,
        googleAuthorizedEmail: String,
        players: List<Player>,
        recentGames: List<BggGame>,
        availableModels: List<String>,
        collectionSnapshot: List<GameItem>,
        loggedPlays: List<LoggedPlay>,
        cachedBggPlays: List<LoggedPlay>
    ): String {
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
                put("bggXmlApiToken", bggXmlApiToken)
                put("geminiApiKey", geminiApiKey)
            })
        }
        root.put("players", JSONArray().also { arr ->
            players.forEach { p ->
                arr.put(JSONObject().apply {
                    put("id", p.id)
                    put("displayName", p.displayName)
                    put("bggUsername", p.bggUsername)
                    put("aliases", JSONArray().also { a -> p.aliases.forEach { a.put(it) } })
                })
            }
        })
        root.put("loggedPlays", JSONArray().also { arr ->
            loggedPlays.forEach { p -> arr.put(playToJson(p)) }
        })
        root.put("recentGames", JSONArray().also { arr ->
            recentGames.forEach { g ->
                arr.put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("year", g.yearPublished ?: "")
                })
            }
        })
        root.put("cachedCollection", JSONArray().also { arr ->
            collectionSnapshot.mapNotNull { game ->
                game.objectId.toIntOrNull()?.let { id ->
                    BggGame(
                        id = id,
                        name = game.name,
                        yearPublished = game.yearPublished?.toString(),
                        thumbnailUrl = game.thumbnailUrl
                    )
                }
            }.forEach { g ->
                arr.put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("year", g.yearPublished ?: "")
                })
            }
        })
        root.put("cachedCollectionTimestamp", System.currentTimeMillis())
        root.put("cachedBggPlays", JSONArray().also { arr ->
            cachedBggPlays.forEach { p -> arr.put(playToJson(p)) }
        })
        root.put("cachedBggPlaysTimestamp", System.currentTimeMillis())
        root.put("availableModels", JSONArray().also { arr ->
            availableModels.forEach { model -> arr.put(model) }
        })
        root.put("collectionSnapshots", JSONObject().also { snapshots ->
            snapshots.put(CANONICAL_SNAPSHOT_ID, JSONArray().also { arr ->
                collectionSnapshot.forEach { game -> arr.put(gameItemToJson(game)) }
            }.toString())
            snapshots.put("${CANONICAL_SNAPSHOT_ID}__ts", System.currentTimeMillis())
        })
        return root.toString(2)
    }

    fun import(
        json: String,
        onSettings: (JSONObject) -> Unit,
        onSecureSettings: (JSONObject) -> Unit,
        onPlayers: (List<Player>) -> Unit,
        onRecentGamesJson: (String) -> Unit,
        onAvailableModelsJson: (String) -> Unit,
        clearLegacyCachedCollection: () -> Unit
    ): ImportedBackupData {
        val root = JSONObject(json)
        root.optJSONObject("settings")?.let(onSettings)
        root.optJSONObject("secureSettings")?.let(onSecureSettings)
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
            onPlayers(players)
        }
        root.optJSONArray("recentGames")?.let { arr -> onRecentGamesJson(arr.toString()) }
        root.optJSONArray("cachedCollection")?.let { clearLegacyCachedCollection() }
        root.optJSONArray("availableModels")?.let { arr -> onAvailableModelsJson(arr.toString()) }

        val importedLoggedPlays = root.optJSONArray("loggedPlays")?.let { arr ->
            (0 until arr.length()).map { i -> jsonToPlay(arr.getJSONObject(i)) }
        } ?: emptyList()
        val importedBggPlays = root.optJSONArray("cachedBggPlays")?.let { arr ->
            (0 until arr.length()).map { i -> jsonToPlay(arr.getJSONObject(i)) }
        } ?: emptyList()
        val importedCollection = root.optJSONObject("collectionSnapshots")?.let { snapshots ->
            val value = snapshots.optString(CANONICAL_SNAPSHOT_ID, "[]")
            runCatching {
                val array = JSONArray(value)
                (0 until array.length()).map { index -> jsonToGameItem(array.getJSONObject(index)) }
            }.getOrDefault(emptyList())
        } ?: emptyList()

        return ImportedBackupData(
            collectionSnapshot = importedCollection,
            loggedPlays = importedLoggedPlays,
            cachedBggPlays = importedBggPlays
        )
    }

    fun playToJson(p: LoggedPlay): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("gameId", p.gameId)
        put("gameName", p.gameName)
        put("date", p.date)
        put("durationMinutes", p.durationMinutes)
        put("location", p.location)
        put("postedToBgg", p.postedToBgg)
        put("comments", p.comments)
        put("quantity", p.quantity)
        put("incomplete", p.incomplete)
        put("nowInStats", p.nowInStats)
        put("players", JSONArray().also { arr ->
            p.players.forEach { pl ->
                arr.put(JSONObject().apply {
                    put("name", pl.name)
                    put("score", pl.score)
                    put("isWinner", pl.isWinner)
                    put("color", pl.color)
                    put("rating", pl.rating)
                    put("isNew", pl.isNew)
                })
            }
        })
    }

    fun jsonToPlay(obj: JSONObject): LoggedPlay {
        val pa = obj.optJSONArray("players") ?: JSONArray()
        return LoggedPlay(
            id = obj.getString("id"),
            gameId = obj.getInt("gameId"),
            gameName = obj.getString("gameName"),
            date = obj.getString("date"),
            players = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                PlayerResult(
                    name = p.getString("name"),
                    score = p.getString("score"),
                    isWinner = p.getBoolean("isWinner"),
                    color = p.optString("color", ""),
                    rating = p.optString("rating", ""),
                    isNew = p.optBoolean("isNew", false)
                )
            },
            durationMinutes = obj.optInt("durationMinutes", 0),
            location = obj.optString("location", ""),
            postedToBgg = obj.optBoolean("postedToBgg", true),
            comments = obj.optString("comments", ""),
            quantity = obj.optInt("quantity", 1),
            incomplete = obj.optBoolean("incomplete", false),
            nowInStats = obj.optBoolean("nowInStats", true)
        )
    }

    fun gameItemToJson(game: GameItem): JSONObject = JSONObject().apply {
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
            put("bggPlayCount", game.ownership.bggPlayCount)
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

    fun jsonToGameItem(obj: JSONObject): GameItem {
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
                bggPlayCount = ownership.optNullableInt("bggPlayCount")
                    ?: ownership.optNullableInt("sheetPlayCount")
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
}
