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
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.PlaySession
import cz.nicolsburg.boardflow.model.PlayerResult
import cz.nicolsburg.boardflow.model.SessionMemory
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

    suspend fun getLoggedPlays(): List<LoggedPlay> {
        val memories = dao.getAllPlayMemories().associate { it.id to it.memoryJson }
        return dao.getAllLoggedPlays().map { entity ->
            val play = entity.toModel()
            val overlayJson = memories[entity.id]
            val memory = overlayJson?.toSessionMemoryOrNull() ?: play.memory ?: play.comments.parseMemoryFromNotes()
            play.copy(memory = memory)
        }
    }

    suspend fun saveLoggedPlay(play: LoggedPlay) {
        dao.upsertLoggedPlay(LoggedPlayEntity.fromModel(play))
    }

    suspend fun getRecentPlaySession(windowStartAt: Long): PlaySession? =
        dao.getRecentPlaySession(windowStartAt)?.toModel()

    suspend fun getPlaySession(sessionId: String): PlaySession? =
        dao.getPlaySession(sessionId)?.toModel()

    suspend fun savePlaySession(session: PlaySession) {
        dao.upsertPlaySession(PlaySessionEntity.fromModel(session))
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

    suspend fun deleteLoggedPlay(playId: String) {
        dao.deleteLoggedPlay(playId)
    }

    suspend fun clearLoggedPlays() {
        dao.clearLoggedPlays()
    }

    suspend fun getBggPlaysCache(): List<LoggedPlay> {
        val memories = dao.getAllPlayMemories().associate { it.id to it.memoryJson }
        return dao.getAllBggCachedPlays().map { entity ->
            val play = entity.toModel()
            val overlayJson = memories[entity.id]
            play.copy(memory = overlayJson?.toSessionMemoryOrNull() ?: play.comments.parseMemoryFromNotes())
        }
    }

    suspend fun savePlayMemory(playId: String, memory: SessionMemory) {
        dao.upsertPlayMemory(PlayMemoryEntity(id = playId, memoryJson = memory.toJsonString()))
    }

    suspend fun removeMoodFromAllMemories(mood: String) {
        val all = dao.getAllPlayMemories()
        val toUpsert = mutableListOf<PlayMemoryEntity>()
        val toDelete = mutableListOf<String>()
        for (entity in all) {
            val memory = entity.memoryJson.toSessionMemoryOrNull() ?: continue
            val newMoods = memory.moods.filter { !it.equals(mood, ignoreCase = true) }
            if (newMoods.size == memory.moods.size) continue
            val newMemory = memory.copy(moods = newMoods)
            if (newMoods.isEmpty() && newMemory.quote.isBlank() && newMemory.note.isBlank() && newMemory.chronicleLine.isBlank())
                toDelete.add(entity.id)
            else
                toUpsert.add(entity.copy(memoryJson = newMemory.toJsonString()))
        }
        if (toUpsert.isEmpty() && toDelete.isEmpty()) return
        db.withTransaction {
            toUpsert.forEach { dao.upsertPlayMemory(it) }
            toDelete.forEach { dao.deletePlayMemory(it) }
        }
    }

    suspend fun replaceAllPlayMemories(plays: List<LoggedPlay>) {
        val entities = plays.mapNotNull { play ->
            play.memory?.toJsonString()?.takeIf { it.isNotBlank() }?.let {
                PlayMemoryEntity(id = play.id, memoryJson = it)
            }
        }
        db.withTransaction {
            dao.clearAllPlayMemories()
            if (entities.isNotEmpty()) dao.insertAllPlayMemories(entities)
        }
    }

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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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

    @Query("SELECT * FROM play_sessions WHERE endedAt >= :windowStartAt ORDER BY endedAt DESC LIMIT 1")
    suspend fun getRecentPlaySession(windowStartAt: Long): PlaySessionEntity?

    @Query("SELECT * FROM play_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getPlaySession(sessionId: String): PlaySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaySession(session: PlaySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLoggedPlays(plays: List<LoggedPlayEntity>)

    @Query("DELETE FROM logged_plays WHERE id = :playId")
    suspend fun deleteLoggedPlay(playId: String)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayMemory(memory: PlayMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlayMemories(memories: List<PlayMemoryEntity>)

    @Query("SELECT * FROM play_memories")
    suspend fun getAllPlayMemories(): List<PlayMemoryEntity>

    @Query("DELETE FROM play_memories WHERE id = :playId")
    suspend fun deletePlayMemory(playId: String)

    @Query("DELETE FROM play_memories")
    suspend fun clearAllPlayMemories()
}

@Database(
    entities = [CanonicalGameEntity::class, LoggedPlayEntity::class, BggCachedPlayEntity::class, StoreMetadataEntity::class, PlayMemoryEntity::class, PlaySessionEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(CanonicalCollectionConverters::class)
private abstract class CanonicalCollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CanonicalCollectionDao
}

@Entity(tableName = "play_memories")
private data class PlayMemoryEntity(
    @PrimaryKey val id: String,
    val memoryJson: String
)

@Entity(tableName = "play_sessions", indices = [Index(value = ["endedAt"])])
private data class PlaySessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val sessionDate: String,
    val location: String,
    val title: String = ""
) {
    fun toModel(): PlaySession = PlaySession(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        sessionDate = sessionDate,
        location = location,
        title = title
    )

    companion object {
        fun fromModel(session: PlaySession): PlaySessionEntity = PlaySessionEntity(
            id = session.id,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            sessionDate = session.sessionDate,
            location = session.location,
            title = session.title
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `canonical_games` ADD COLUMN `notRecommendedPlayers` TEXT")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `play_sessions` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `logged_plays` ADD COLUMN `playedAt` INTEGER")
        db.execSQL("ALTER TABLE `logged_plays` ADD COLUMN `sessionId` TEXT")
        db.execSQL("ALTER TABLE `bgg_cached_plays` ADD COLUMN `playedAt` INTEGER")
        db.execSQL("ALTER TABLE `bgg_cached_plays` ADD COLUMN `sessionId` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `play_sessions` (
                `id` TEXT NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `endedAt` INTEGER NOT NULL,
                `sessionDate` TEXT NOT NULL,
                `location` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_sessions_endedAt` ON `play_sessions` (`endedAt`)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `play_memories` (`id` TEXT NOT NULL, `memoryJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
    }
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

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `logged_plays` ADD COLUMN `memory` TEXT NOT NULL DEFAULT ''")
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
    val notRecommendedPlayers: String?,
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
    fun toModel(): GameItem {
        val bestPlayersResolved = bestPlayers ?: bggValues["bggbestplayers"]?.takeIf { it.isNotBlank() }
        val recommendedPlayersResolved = recommendedPlayers ?: bggValues["bggrecplayers"]?.takeIf { it.isNotBlank() }
        val notRecommendedPlayersResolved = notRecommendedPlayers ?: bggValues["bggnotrecplayers"]?.takeIf { it.isNotBlank() }
        val recommendedAgeResolved = recommendedAge ?: bggValues["bggrecagerange"]?.takeIf { it.isNotBlank() }
        return GameItem(
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
            bestPlayers = bestPlayersResolved,
            recommendedPlayers = recommendedPlayersResolved,
            notRecommendedPlayers = notRecommendedPlayersResolved,
            recommendedAge = recommendedAgeResolved
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
    )}

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
            notRecommendedPlayers = game.notRecommendedPlayers,
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

@Entity(tableName = "logged_plays", indices = [Index(value = ["date"])])
private data class LoggedPlayEntity(
    @PrimaryKey val id: String,
    val gameId: Int,
    val gameName: String,
    val date: String,
    val playedAt: Long? = null,
    val sessionId: String? = null,
    val players: List<PlayerResult>,
    val durationMinutes: Int,
    val location: String,
    val postedToBgg: Boolean,
    val comments: String,
    val quantity: Int,
    val incomplete: Boolean,
    val nowInStats: Boolean,
    val memory: String = ""
) {
    fun toModel(): LoggedPlay = LoggedPlay(
        id = id,
        gameId = gameId,
        gameName = gameName,
        date = date,
        playedAt = playedAt,
        sessionId = sessionId,
        players = players,
        durationMinutes = durationMinutes,
        location = location,
        postedToBgg = postedToBgg,
        comments = comments,
        quantity = quantity,
        incomplete = incomplete,
        nowInStats = nowInStats,
        memory = memory.toSessionMemoryOrNull()
    )

    companion object {
        fun fromModel(play: LoggedPlay): LoggedPlayEntity = LoggedPlayEntity(
            id = play.id,
            gameId = play.gameId,
            gameName = play.gameName,
            date = play.date,
            playedAt = play.playedAt,
            sessionId = play.sessionId,
            players = play.players,
            durationMinutes = play.durationMinutes,
            location = play.location,
            postedToBgg = play.postedToBgg,
            comments = play.comments,
            quantity = play.quantity,
            incomplete = play.incomplete,
            nowInStats = play.nowInStats,
            memory = play.memory?.toJsonString() ?: ""
        )
    }
}

@Entity(tableName = "bgg_cached_plays", indices = [Index(value = ["date"])])
private data class BggCachedPlayEntity(
    @PrimaryKey val id: String,
    val gameId: Int,
    val gameName: String,
    val date: String,
    val playedAt: Long? = null,
    val sessionId: String? = null,
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
        playedAt = playedAt,
        sessionId = sessionId,
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
            playedAt = play.playedAt,
            sessionId = play.sessionId,
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
