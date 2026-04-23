package cz.nicolsburg.boardflow.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.withTransaction
import androidx.room.migration.Migration
import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.PlayerResult
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

class CanonicalCollectionStore private constructor(
    private val db: CanonicalCollectionDatabase
) {
    private val dao = db.collectionDao()

    suspend fun getAllGames(): List<GameItem> =
        dao.getAll().map { it.toModel() }

    suspend fun replaceAllGames(games: List<GameItem>) {
        db.withTransaction {
            dao.clearAll()
            dao.insertAll(games.map { CanonicalGameEntity.fromModel(it) })
        }
    }

    suspend fun clearAllGames() {
        dao.clearAll()
    }

    suspend fun countGames(): Int = dao.count()

    suspend fun getLightweightGames(): List<BggGame> {
        return dao.getAllLightweight().mapNotNull { item ->
            item.objectId.toIntOrNull()?.let { id ->
                BggGame(
                    id = id,
                    name = item.name,
                    yearPublished = item.yearPublished?.toString(),
                    thumbnailUrl = item.thumbnailUrl
                )
            }
        }
    }

    suspend fun getLoggedPlays(): List<LoggedPlay> =
        dao.getAllLoggedPlays().map { it.toModel() }

    suspend fun saveLoggedPlay(play: LoggedPlay) {
        dao.upsertLoggedPlay(LoggedPlayEntity.fromModel(play))
    }

    suspend fun replaceLoggedPlays(plays: List<LoggedPlay>) {
        db.withTransaction {
            dao.clearLoggedPlays()
            dao.insertAllLoggedPlays(plays.map { LoggedPlayEntity.fromModel(it) })
        }
    }

    suspend fun updateLoggedPlay(playId: String, transform: (LoggedPlay) -> LoggedPlay) {
        val existing = dao.getLoggedPlay(playId)?.toModel() ?: return
        dao.upsertLoggedPlay(LoggedPlayEntity.fromModel(transform(existing)))
    }

    suspend fun clearLoggedPlays() {
        dao.clearLoggedPlays()
    }

    suspend fun getBggPlaysCache(): List<LoggedPlay> =
        dao.getAllBggCachedPlays().map { it.toModel() }

    suspend fun saveBggPlaysCache(plays: List<LoggedPlay>) {
        db.withTransaction {
            dao.clearBggCachedPlays()
            dao.insertAllBggCachedPlays(plays.map { BggCachedPlayEntity.fromModel(it) })
            dao.upsertMetadata(StoreMetadataEntity(KEY_BGG_CACHE_UPDATED_AT, System.currentTimeMillis()))
        }
    }

    suspend fun getBggPlaysCacheAgeMinutes(): Long {
        val updatedAt = dao.getMetadataLong(KEY_BGG_CACHE_UPDATED_AT) ?: return Long.MAX_VALUE
        if (updatedAt == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - updatedAt) / 60_000
    }

    companion object {
        private const val KEY_BGG_CACHE_UPDATED_AT = "bgg_cache_updated_at"
        @Volatile private var INSTANCE: CanonicalCollectionStore? = null

        fun getInstance(context: Context): CanonicalCollectionStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CanonicalCollectionStore(
                    Room.databaseBuilder(
                        context.applicationContext,
                        CanonicalCollectionDatabase::class.java,
                        "boardflow_collection.db"
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                ).also { INSTANCE = it }
            }
        }
    }
}

@Dao
private interface CanonicalCollectionDao {
    @Query("SELECT * FROM canonical_games ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<CanonicalGameEntity>

    @Query("SELECT objectId, name, yearPublished, thumbnailUrl FROM canonical_games ORDER BY name COLLATE NOCASE")
    suspend fun getAllLightweight(): List<LightweightGameRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<CanonicalGameEntity>)

    @Query("DELETE FROM canonical_games")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM canonical_games")
    suspend fun count(): Int

    @Query("SELECT * FROM logged_plays ORDER BY date DESC")
    suspend fun getAllLoggedPlays(): List<LoggedPlayEntity>

    @Query("SELECT * FROM logged_plays WHERE id = :playId LIMIT 1")
    suspend fun getLoggedPlay(playId: String): LoggedPlayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLoggedPlay(play: LoggedPlayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLoggedPlays(plays: List<LoggedPlayEntity>)

    @Query("DELETE FROM logged_plays")
    suspend fun clearLoggedPlays()

    @Query("SELECT COUNT(*) FROM logged_plays")
    suspend fun countLoggedPlays(): Int

    @Query("SELECT * FROM bgg_cached_plays ORDER BY date DESC")
    suspend fun getAllBggCachedPlays(): List<BggCachedPlayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBggCachedPlays(plays: List<BggCachedPlayEntity>)

    @Query("DELETE FROM bgg_cached_plays")
    suspend fun clearBggCachedPlays()

    @Query("SELECT COUNT(*) FROM bgg_cached_plays")
    suspend fun countBggCachedPlays(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(entry: StoreMetadataEntity)

    @Query("SELECT longValue FROM store_metadata WHERE `key` = :key LIMIT 1")
    suspend fun getMetadataLong(key: String): Long?
}

@Database(
    entities = [CanonicalGameEntity::class, LoggedPlayEntity::class, BggCachedPlayEntity::class, StoreMetadataEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(CanonicalCollectionConverters::class)
private abstract class CanonicalCollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CanonicalCollectionDao
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `logged_plays` (
                `id` TEXT NOT NULL,
                `gameId` INTEGER NOT NULL,
                `gameName` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `players` TEXT NOT NULL,
                `durationMinutes` INTEGER NOT NULL,
                `location` TEXT NOT NULL,
                `postedToBgg` INTEGER NOT NULL,
                `comments` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `incomplete` INTEGER NOT NULL,
                `nowInStats` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_logged_plays_date` ON `logged_plays` (`date`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bgg_cached_plays` (
                `id` TEXT NOT NULL,
                `gameId` INTEGER NOT NULL,
                `gameName` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `players` TEXT NOT NULL,
                `durationMinutes` INTEGER NOT NULL,
                `location` TEXT NOT NULL,
                `postedToBgg` INTEGER NOT NULL,
                `comments` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `incomplete` INTEGER NOT NULL,
                `nowInStats` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bgg_cached_plays_date` ON `bgg_cached_plays` (`date`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `store_metadata` (
                `key` TEXT NOT NULL,
                `longValue` INTEGER,
                PRIMARY KEY(`key`)
            )
            """.trimIndent()
        )
    }
}

@Entity(tableName = "canonical_games")
private data class CanonicalGameEntity(
    @PrimaryKey val objectId: String,
    val name: String,
    val rank: Int?,
    val averageRating: Double?,
    val bayesAverage: Double?,
    val weight: Double?,
    val yearPublished: Int?,
    val playingTime: Int?,
    val minPlayTime: Int?,
    val maxPlayTime: Int?,
    val numOwned: Int?,
    val languageDependence: String?,
    val language: String?,
    val minPlayers: Int?,
    val maxPlayers: Int?,
    val bestPlayers: String?,
    val recommendedPlayers: String?,
    val recommendedAge: String?,
    val isOwned: Boolean,
    val isWishlisted: Boolean,
    val bggPlayCount: Int?,
    val sleeveStatus: String,
    val sleeveCardSets: List<GameItem.Sleeves.CardSet>,
    val sleeveSourceUrl: String?,
    val sleeveNote: String?,
    val sleevesLastFetchedAt: Long?,
    val thumbnailUrl: String?,
    val bggUrl: String?,
    val driveUrl: String?,
    val qrImageUrl: String?,
    val spreadsheetValues: Map<String, String>,
    val bggValues: Map<String, String>,
    val lastCachedAt: Long
) {
    fun toModel(): GameItem = GameItem(
        identity = GameItem.Identity(
            objectId = objectId,
            name = name
        ),
        stats = GameItem.Stats(
            rank = rank,
            averageRating = averageRating,
            bayesAverage = bayesAverage,
            weight = weight,
            yearPublished = yearPublished,
            playingTime = playingTime,
            minPlayTime = minPlayTime,
            maxPlayTime = maxPlayTime,
            numOwned = numOwned,
            languageDependence = languageDependence,
            language = language
        ),
        players = GameItem.Players(
            minPlayers = minPlayers,
            maxPlayers = maxPlayers,
            bestPlayers = bestPlayers,
            recommendedPlayers = recommendedPlayers,
            recommendedAge = recommendedAge
        ),
        ownership = GameItem.Ownership(
            isOwned = isOwned,
            isWishlisted = isWishlisted,
            bggPlayCount = bggPlayCount
        ),
        sleeves = GameItem.Sleeves(
            status = runCatching { GameItem.SleeveStatus.valueOf(sleeveStatus) }
                .getOrDefault(GameItem.SleeveStatus.UNKNOWN),
            cardSets = sleeveCardSets,
            sourceUrl = sleeveSourceUrl,
            note = sleeveNote,
            lastFetchedAt = sleevesLastFetchedAt
        ),
        media = GameItem.Media(
            thumbnailUrl = thumbnailUrl
        ),
        links = GameItem.Links(
            bggUrl = bggUrl,
            driveUrl = driveUrl,
            qrImageUrl = qrImageUrl
        ),
        sources = GameItem.Sources(
            spreadsheetValues = spreadsheetValues,
            bggValues = bggValues
        ),
        lastCachedAt = lastCachedAt
    )

    companion object {
        fun fromModel(game: GameItem): CanonicalGameEntity = CanonicalGameEntity(
            objectId = game.objectId,
            name = game.name,
            rank = game.rank,
            averageRating = game.rating,
            bayesAverage = game.bayesAverage,
            weight = game.weight,
            yearPublished = game.yearPublished,
            playingTime = game.playingTime,
            minPlayTime = game.minPlayTime,
            maxPlayTime = game.maxPlayTime,
            numOwned = game.numOwned,
            languageDependence = game.languageDependence,
            language = game.language,
            minPlayers = game.minPlayers,
            maxPlayers = game.maxPlayers,
            bestPlayers = game.bestPlayers,
            recommendedPlayers = game.recommendedPlayers,
            recommendedAge = game.recommendedAge,
            isOwned = game.isOwned,
            isWishlisted = game.isWishlisted,
            bggPlayCount = game.numPlays,
            sleeveStatus = game.sleeveStatus.name,
            sleeveCardSets = game.sleeveCardSets,
            sleeveSourceUrl = game.sleeveSourceUrl,
            sleeveNote = game.sleeveNote,
            sleevesLastFetchedAt = game.sleevesLastFetchedAt,
            thumbnailUrl = game.thumbnailUrl,
            bggUrl = game.bggUrl,
            driveUrl = game.shareUrl,
            qrImageUrl = game.qrImageUrl,
            spreadsheetValues = game.spreadsheetValues,
            bggValues = game.bggValues,
            lastCachedAt = game.lastCachedAt
        )
    }
}

private data class LightweightGameRow(
    val objectId: String,
    val name: String,
    val yearPublished: Int?,
    val thumbnailUrl: String?
)

@Entity(tableName = "logged_plays", indices = [Index(value = ["date"])])
private data class LoggedPlayEntity(
    @PrimaryKey val id: String,
    val gameId: Int,
    val gameName: String,
    val date: String,
    val players: List<PlayerResult>,
    val durationMinutes: Int,
    val location: String,
    val postedToBgg: Boolean,
    val comments: String,
    val quantity: Int,
    val incomplete: Boolean,
    val nowInStats: Boolean
) {
    fun toModel(): LoggedPlay = LoggedPlay(
        id = id,
        gameId = gameId,
        gameName = gameName,
        date = date,
        players = players,
        durationMinutes = durationMinutes,
        location = location,
        postedToBgg = postedToBgg,
        comments = comments,
        quantity = quantity,
        incomplete = incomplete,
        nowInStats = nowInStats
    )

    companion object {
        fun fromModel(play: LoggedPlay): LoggedPlayEntity = LoggedPlayEntity(
            id = play.id,
            gameId = play.gameId,
            gameName = play.gameName,
            date = play.date,
            players = play.players,
            durationMinutes = play.durationMinutes,
            location = play.location,
            postedToBgg = play.postedToBgg,
            comments = play.comments,
            quantity = play.quantity,
            incomplete = play.incomplete,
            nowInStats = play.nowInStats
        )
    }
}

@Entity(tableName = "bgg_cached_plays", indices = [Index(value = ["date"])])
private data class BggCachedPlayEntity(
    @PrimaryKey val id: String,
    val gameId: Int,
    val gameName: String,
    val date: String,
    val players: List<PlayerResult>,
    val durationMinutes: Int,
    val location: String,
    val postedToBgg: Boolean,
    val comments: String,
    val quantity: Int,
    val incomplete: Boolean,
    val nowInStats: Boolean
) {
    fun toModel(): LoggedPlay = LoggedPlay(
        id = id,
        gameId = gameId,
        gameName = gameName,
        date = date,
        players = players,
        durationMinutes = durationMinutes,
        location = location,
        postedToBgg = postedToBgg,
        comments = comments,
        quantity = quantity,
        incomplete = incomplete,
        nowInStats = nowInStats
    )

    companion object {
        fun fromModel(play: LoggedPlay): BggCachedPlayEntity = BggCachedPlayEntity(
            id = play.id,
            gameId = play.gameId,
            gameName = play.gameName,
            date = play.date,
            players = play.players,
            durationMinutes = play.durationMinutes,
            location = play.location,
            postedToBgg = play.postedToBgg,
            comments = play.comments,
            quantity = play.quantity,
            incomplete = play.incomplete,
            nowInStats = play.nowInStats
        )
    }
}

@Entity(tableName = "store_metadata")
private data class StoreMetadataEntity(
    @PrimaryKey val key: String,
    val longValue: Long?
)

private class CanonicalCollectionConverters {
    @TypeConverter
    fun fromMap(value: Map<String, String>): String {
        val json = JSONObject()
        value.forEach { (key, item) -> json.put(key, item) }
        return json.toString()
    }

    @TypeConverter
    fun toMap(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        val json = JSONObject(value)
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optString(key, ""))
            }
        }
    }

    @TypeConverter
    fun fromCardSets(value: List<GameItem.Sleeves.CardSet>): String {
        val array = JSONArray()
        value.forEach { cardSet ->
            array.put(
                JSONObject().apply {
                    put("label", cardSet.label)
                    put("count", cardSet.count)
                    put("size", cardSet.size)
                    put("notes", cardSet.notes)
                }
            )
        }
        return array.toString()
    }

    @TypeConverter
    fun toCardSets(value: String): List<GameItem.Sleeves.CardSet> {
        if (value.isBlank()) return emptyList()
        val array = JSONArray(value)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    GameItem.Sleeves.CardSet(
                        label = item.optString("label", ""),
                        count = item.opt("count")?.toString()?.toIntOrNull(),
                        size = item.optString("size").ifBlank { null },
                        notes = item.optString("notes").ifBlank { null }
                    )
                )
            }
        }
    }

    @TypeConverter
    fun fromPlayerResults(value: List<PlayerResult>): String {
        val array = JSONArray()
        value.forEach { player ->
            array.put(
                JSONObject().apply {
                    put("name", player.name)
                    put("score", player.score)
                    put("isWinner", player.isWinner)
                    put("color", player.color)
                    put("rating", player.rating)
                    put("isNew", player.isNew)
                }
            )
        }
        return array.toString()
    }

    @TypeConverter
    fun toPlayerResults(value: String): List<PlayerResult> {
        if (value.isBlank()) return emptyList()
        val array = JSONArray(value)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PlayerResult(
                        name = item.optString("name", ""),
                        score = item.optString("score", ""),
                        isWinner = item.optBoolean("isWinner", false),
                        color = item.optString("color", ""),
                        rating = item.optString("rating", ""),
                        isNew = item.optBoolean("isNew", false)
                    )
                )
            }
        }
    }
}
