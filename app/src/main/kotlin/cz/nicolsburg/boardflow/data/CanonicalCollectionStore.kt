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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.migration.Migration
import cz.nicolsburg.boardflow.model.Challenge
import cz.nicolsburg.boardflow.model.ChallengeStatus
import cz.nicolsburg.boardflow.model.ChallengeType
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.GameRecognitionHint
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.PlaySession
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.PlayerRecognitionHint
import cz.nicolsburg.boardflow.model.PlayerResult
import cz.nicolsburg.boardflow.model.SleeveTrackingState
import cz.nicolsburg.boardflow.model.SessionMemory
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

class CanonicalCollectionStore private constructor(
    private val db: CanonicalCollectionDatabase
) {
    private val dao = db.collectionDao()

    suspend fun getAllGames(): List<GameItem> =
        overlaySleeveTracking(dao.getAll().map { it.toModel() })

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

    suspend fun saveSleeveTracking(gameObjectId: String, trackingState: SleeveTrackingState) {
        dao.upsertSleeveTracking(
            GameSleeveTrackingEntity(
                objectId = gameObjectId,
                trackingState = trackingState.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearSleeveTracking() {
        dao.clearSleeveTracking()
    }

    suspend fun getSleeveInventory(): Map<String, Int> =
        dao.getAllSleeveInventory().associate { it.genericName to it.ownedCount }

    suspend fun setSleeveInventoryCount(genericName: String, count: Int) {
        if (count <= 0) {
            dao.deleteSleeveInventoryEntry(genericName)
        } else {
            dao.upsertSleeveInventoryEntry(
                SleeveInventoryEntity(
                    genericName = genericName,
                    ownedCount = count,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun replaceSleeveTrackingFromGames(games: List<GameItem>) {
        val entries = games.mapNotNull { game ->
            val state = SleeveTrackingState.fromSheetValue(
                game.spreadsheetValues.entries.firstOrNull { (key, _) -> key.equals("sleeved", ignoreCase = true) }?.value
            )
            if (state == SleeveTrackingState.UNKNOWN || game.objectId.isBlank()) null
            else GameSleeveTrackingEntity(
                objectId = game.objectId,
                trackingState = state.name,
                updatedAt = System.currentTimeMillis()
            )
        }
        db.withTransaction {
            dao.clearSleeveTracking()
            if (entries.isNotEmpty()) dao.insertAllSleeveTracking(entries)
        }
    }

    suspend fun getLoggedPlays(): List<LoggedPlay> = withContext(Dispatchers.IO) {
        val memories = dao.getAllPlayMemories().associate { it.id to it.memoryJson }
        dao.getAllLoggedPlays().map { entity ->
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

    suspend fun getThumbnailCacheGameIds(): List<Int> = dao.getThumbnailCacheGameIds()

    suspend fun getThumbnailCache(): Map<Int, Pair<String, String?>> =
        dao.getAllThumbnailCache().associate { it.gameId to (it.gameName to it.thumbnailUrl) }

    suspend fun saveThumbnailCache(entries: Map<Int, Pair<String, String?>>) {
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertThumbnailCacheEntries(entries.map { (id, pair) ->
            GameThumbnailCacheEntity(gameId = id, gameName = pair.first, thumbnailUrl = pair.second, cachedAt = now)
        })
    }

    suspend fun getBggPlaysCacheAgeMinutes(): Long {
        val updatedAt = dao.getMetadataLong(KEY_BGG_CACHE_UPDATED_AT) ?: return Long.MAX_VALUE
        if (updatedAt == 0L) return Long.MAX_VALUE
        return (System.currentTimeMillis() - updatedAt) / 60_000
    }

    // --- Players ---

    suspend fun getPlayers(): List<Player> =
        dao.getAllPlayers().map { it.toModel() }

    suspend fun replacePlayers(players: List<Player>) {
        db.withTransaction {
            dao.clearPlayers()
            dao.insertAllPlayers(players.map { PlayerEntity.fromModel(it) })
        }
    }

    suspend fun upsertPlayer(player: Player) =
        dao.upsertPlayer(PlayerEntity.fromModel(player))

    suspend fun deletePlayer(playerId: String) =
        dao.deletePlayer(playerId)

    // --- Challenges ---

    suspend fun getChallenges(): List<Challenge> =
        dao.getAllChallenges().map { it.toModel() }

    suspend fun replaceChallenges(challenges: List<Challenge>) {
        db.withTransaction {
            dao.clearChallenges()
            dao.insertAllChallenges(challenges.map { ChallengeEntity.fromModel(it) })
        }
    }

    suspend fun upsertChallenge(challenge: Challenge) =
        dao.upsertChallenge(ChallengeEntity.fromModel(challenge))

    suspend fun deleteChallenge(challengeId: String) =
        dao.deleteChallenge(challengeId)

    // --- Game recognition hints ---

    suspend fun getGameRecognitionHints(): List<GameRecognitionHint> =
        dao.getAllGameRecognitionHints().map { it.toModel() }

    suspend fun replaceGameRecognitionHints(hints: List<GameRecognitionHint>) {
        db.withTransaction {
            dao.clearGameRecognitionHints()
            dao.insertAllGameRecognitionHints(hints.map { GameRecognitionHintEntity.fromModel(it) })
        }
    }

    suspend fun saveGameRecognitionHint(hint: GameRecognitionHint) {
        val existing = dao.getGameRecognitionHint(hint.gameObjectId)
        val entity = if (existing != null) {
            existing.copy(
                gameName = hint.gameName,
                normalizedTitles = (existing.normalizedTitles + hint.normalizedTitles).distinct(),
                normalizedCategories = (existing.normalizedCategories + hint.normalizedCategories).distinct(),
                confirmedAt = hint.confirmedAt,
                timesConfirmed = existing.timesConfirmed + 1
            )
        } else {
            GameRecognitionHintEntity.fromModel(hint)
        }
        dao.upsertGameRecognitionHint(entity)
    }

    suspend fun upsertGameRecognitionHint(hint: GameRecognitionHint) =
        dao.upsertGameRecognitionHint(GameRecognitionHintEntity.fromModel(hint))

    suspend fun deleteGameRecognitionHint(gameObjectId: String) =
        dao.deleteGameRecognitionHint(gameObjectId)

    suspend fun clearGameRecognitionHints() =
        dao.clearGameRecognitionHints()

    // --- Player recognition hints ---

    suspend fun getPlayerRecognitionHints(): List<PlayerRecognitionHint> =
        dao.getAllPlayerRecognitionHints().map { it.toModel() }

    suspend fun replacePlayerRecognitionHints(hints: List<PlayerRecognitionHint>) {
        db.withTransaction {
            dao.clearPlayerRecognitionHints()
            dao.insertAllPlayerRecognitionHints(hints.map { PlayerRecognitionHintEntity.fromModel(it) })
        }
    }

    suspend fun savePlayerRecognitionHint(hint: PlayerRecognitionHint) {
        val existing = dao.getPlayerRecognitionHint(hint.scannedNameNormalized, hint.confirmedRosterPlayerId)
        val entity = if (existing != null) {
            PlayerRecognitionHintEntity.fromModel(hint.copy(timesConfirmed = existing.timesConfirmed + 1))
        } else {
            PlayerRecognitionHintEntity.fromModel(hint)
        }
        dao.upsertPlayerRecognitionHint(entity)
    }

    suspend fun clearPlayerRecognitionHints() =
        dao.clearPlayerRecognitionHints()

    private suspend fun overlaySleeveTracking(games: List<GameItem>): List<GameItem> {
        val trackingById = dao.getAllSleeveTracking().associateBy { it.objectId }
        return games.map { game ->
            val tracking = trackingById[game.objectId] ?: return@map game
            val state = runCatching { SleeveTrackingState.valueOf(tracking.trackingState) }
                .getOrDefault(SleeveTrackingState.UNKNOWN)
            if (state == SleeveTrackingState.UNKNOWN) game else game.withSpreadsheetValue("sleeved", state.sheetValue)
        }
    }

    // --- Play stats aggregates ---

    suspend fun getPlaySummary(countAll: Boolean, afterDate: String): PlaySummaryRow =
        dao.getPlaySummary(if (countAll) 1 else 0, afterDate)

    suspend fun getTopGamesByPlays(countAll: Boolean, afterDate: String, limit: Int = 10): List<GamePlayRow> =
        dao.getTopGamesByPlays(if (countAll) 1 else 0, afterDate, limit)

    suspend fun getPlaysByDate(countAll: Boolean, afterDate: String): List<DatePlayRow> =
        dao.getPlaysByDate(if (countAll) 1 else 0, afterDate)

    suspend fun getGamePlayCounts(countAll: Boolean, afterDate: String): List<NamePlayRow> =
        dao.getGamePlayCounts(if (countAll) 1 else 0, afterDate)

    suspend fun getLongestSession(countAll: Boolean, afterDate: String): GamePlayRow? =
        dao.getLongestSession(if (countAll) 1 else 0, afterDate)

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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThumbnailCacheEntries(entries: List<GameThumbnailCacheEntity>)

    @Query("SELECT * FROM game_thumbnail_cache")
    suspend fun getAllThumbnailCache(): List<GameThumbnailCacheEntity>

    @Query("SELECT gameId FROM game_thumbnail_cache")
    suspend fun getThumbnailCacheGameIds(): List<Int>

    // Players
    @Query("SELECT * FROM players ORDER BY displayName COLLATE NOCASE")
    suspend fun getAllPlayers(): List<PlayerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayer(player: PlayerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlayers(players: List<PlayerEntity>)

    @Query("DELETE FROM players WHERE id = :playerId")
    suspend fun deletePlayer(playerId: String)

    @Query("DELETE FROM players")
    suspend fun clearPlayers()

    // Challenges
    @Query("SELECT * FROM challenges ORDER BY createdAt ASC")
    suspend fun getAllChallenges(): List<ChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(challenge: ChallengeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChallenges(challenges: List<ChallengeEntity>)

    @Query("DELETE FROM challenges WHERE id = :challengeId")
    suspend fun deleteChallenge(challengeId: String)

    @Query("DELETE FROM challenges")
    suspend fun clearChallenges()

    // Game recognition hints
    @Query("SELECT * FROM game_recognition_hints")
    suspend fun getAllGameRecognitionHints(): List<GameRecognitionHintEntity>

    @Query("SELECT * FROM game_recognition_hints WHERE gameObjectId = :gameObjectId LIMIT 1")
    suspend fun getGameRecognitionHint(gameObjectId: String): GameRecognitionHintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGameRecognitionHint(hint: GameRecognitionHintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGameRecognitionHints(hints: List<GameRecognitionHintEntity>)

    @Query("DELETE FROM game_recognition_hints WHERE gameObjectId = :gameObjectId")
    suspend fun deleteGameRecognitionHint(gameObjectId: String)

    @Query("DELETE FROM game_recognition_hints")
    suspend fun clearGameRecognitionHints()

    // Player recognition hints
    @Query("SELECT * FROM player_recognition_hints")
    suspend fun getAllPlayerRecognitionHints(): List<PlayerRecognitionHintEntity>

    @Query("SELECT * FROM player_recognition_hints WHERE scannedNameNormalized = :scannedName AND confirmedRosterPlayerId = :playerId LIMIT 1")
    suspend fun getPlayerRecognitionHint(scannedName: String, playerId: String): PlayerRecognitionHintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayerRecognitionHint(hint: PlayerRecognitionHintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlayerRecognitionHints(hints: List<PlayerRecognitionHintEntity>)

    @Query("DELETE FROM player_recognition_hints")
    suspend fun clearPlayerRecognitionHints()

    @Query("SELECT * FROM game_sleeve_tracking")
    suspend fun getAllSleeveTracking(): List<GameSleeveTrackingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleeveTracking(entry: GameSleeveTrackingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSleeveTracking(entries: List<GameSleeveTrackingEntity>)

    @Query("DELETE FROM game_sleeve_tracking")
    suspend fun clearSleeveTracking()

    // Sleeve inventory
    @Query("SELECT * FROM sleeve_inventory")
    suspend fun getAllSleeveInventory(): List<SleeveInventoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleeveInventoryEntry(entry: SleeveInventoryEntity)

    @Query("DELETE FROM sleeve_inventory WHERE genericName = :genericName")
    suspend fun deleteSleeveInventoryEntry(genericName: String)

    // Stats aggregates
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN quantity < 1 THEN 1 ELSE quantity END), 0) AS totalPlays,
            COUNT(DISTINCT gameName) AS uniqueGames,
            COALESCE(SUM(CASE WHEN durationMinutes > 0 THEN durationMinutes ELSE 0 END), 0) AS totalMinutes
        FROM logged_plays
        WHERE (:countAll = 1 OR nowInStats = 1) AND date >= :afterDate
    """)
    suspend fun getPlaySummary(countAll: Int, afterDate: String): PlaySummaryRow

    @Query("""
        SELECT gameId, gameName, SUM(CASE WHEN quantity < 1 THEN 1 ELSE quantity END) AS plays
        FROM logged_plays
        WHERE (:countAll = 1 OR nowInStats = 1) AND date >= :afterDate AND gameName != ''
        GROUP BY gameId
        ORDER BY plays DESC
        LIMIT :limit
    """)
    suspend fun getTopGamesByPlays(countAll: Int, afterDate: String, limit: Int): List<GamePlayRow>

    @Query("""
        SELECT date, SUM(CASE WHEN quantity < 1 THEN 1 ELSE quantity END) AS plays
        FROM logged_plays
        WHERE (:countAll = 1 OR nowInStats = 1) AND date >= :afterDate
        GROUP BY date
        ORDER BY date
    """)
    suspend fun getPlaysByDate(countAll: Int, afterDate: String): List<DatePlayRow>

    @Query("""
        SELECT gameName, SUM(CASE WHEN quantity < 1 THEN 1 ELSE quantity END) AS plays
        FROM logged_plays
        WHERE (:countAll = 1 OR nowInStats = 1) AND date >= :afterDate AND gameName != ''
        GROUP BY gameName
        ORDER BY plays DESC
    """)
    suspend fun getGamePlayCounts(countAll: Int, afterDate: String): List<NamePlayRow>

    @Query("""
        SELECT gameId, gameName, MAX(durationMinutes) AS plays
        FROM logged_plays
        WHERE durationMinutes > 0 AND (:countAll = 1 OR nowInStats = 1) AND date >= :afterDate
        LIMIT 1
    """)
    suspend fun getLongestSession(countAll: Int, afterDate: String): GamePlayRow?
}

@Database(
    entities = [
        CanonicalGameEntity::class,
        LoggedPlayEntity::class,
        BggCachedPlayEntity::class,
        StoreMetadataEntity::class,
        PlayMemoryEntity::class,
        PlaySessionEntity::class,
        GameThumbnailCacheEntity::class,
        PlayerEntity::class,
        ChallengeEntity::class,
        GameRecognitionHintEntity::class,
        PlayerRecognitionHintEntity::class,
        GameSleeveTrackingEntity::class,
        SleeveInventoryEntity::class
    ],
    version = 11,
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

@Entity(tableName = "game_thumbnail_cache")
private data class GameThumbnailCacheEntity(
    @PrimaryKey val gameId: Int,
    val gameName: String,
    val thumbnailUrl: String?,
    val cachedAt: Long
)

@Entity(tableName = "game_sleeve_tracking")
private data class GameSleeveTrackingEntity(
    @PrimaryKey val objectId: String,
    val trackingState: String,
    val updatedAt: Long
)

@Entity(tableName = "sleeve_inventory")
private data class SleeveInventoryEntity(
    @PrimaryKey val genericName: String,
    val ownedCount: Int,
    val updatedAt: Long
)

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_thumbnail_cache` (
                `gameId` INTEGER NOT NULL,
                `gameName` TEXT NOT NULL,
                `thumbnailUrl` TEXT,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`gameId`)
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `players` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `aliases` TEXT NOT NULL DEFAULT '[]',
                `bggUsername` TEXT NOT NULL DEFAULT '',
                `lastPlayedAt` INTEGER,
                `isHidden` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `challenges` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `targetCount` INTEGER NOT NULL,
                `gameId` INTEGER,
                `gameName` TEXT,
                `playerIds` TEXT NOT NULL DEFAULT '[]',
                `playerNames` TEXT NOT NULL DEFAULT '[]',
                `startDate` TEXT,
                `endDate` TEXT,
                `createdAt` INTEGER NOT NULL,
                `streakPeriod` TEXT,
                `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_recognition_hints` (
                `gameObjectId` TEXT NOT NULL,
                `gameName` TEXT NOT NULL,
                `normalizedTitles` TEXT NOT NULL DEFAULT '[]',
                `normalizedCategories` TEXT NOT NULL DEFAULT '[]',
                `confirmedAt` INTEGER NOT NULL,
                `timesConfirmed` INTEGER NOT NULL,
                PRIMARY KEY(`gameObjectId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `player_recognition_hints` (
                `scannedNameNormalized` TEXT NOT NULL,
                `confirmedRosterPlayerId` TEXT NOT NULL,
                `playerDisplayName` TEXT NOT NULL,
                `timesConfirmed` INTEGER NOT NULL,
                `lastConfirmedAt` INTEGER NOT NULL,
                PRIMARY KEY(`scannedNameNormalized`, `confirmedRosterPlayerId`)
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_sleeve_tracking` (
                `objectId` TEXT NOT NULL,
                `trackingState` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`objectId`)
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sleeve_inventory` (
                `genericName` TEXT NOT NULL,
                `ownedCount` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`genericName`)
            )
            """.trimIndent()
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

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        JSONArray().also { arr -> value.forEach(arr::put) }.toString()

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val arr = JSONArray(value)
        return buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
    }
}

@Entity(tableName = "players")
private data class PlayerEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val aliases: List<String>,
    val bggUsername: String,
    val lastPlayedAt: Long?,
    val isHidden: Boolean
) {
    fun toModel(): Player = Player(
        id = id,
        displayName = displayName,
        aliases = aliases,
        bggUsername = bggUsername,
        lastPlayedAt = lastPlayedAt,
        isHidden = isHidden
    )

    companion object {
        fun fromModel(p: Player) = PlayerEntity(
            id = p.id,
            displayName = p.displayName,
            aliases = p.aliases,
            bggUsername = p.bggUsername,
            lastPlayedAt = p.lastPlayedAt,
            isHidden = p.isHidden
        )
    }
}

@Entity(tableName = "challenges")
private data class ChallengeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val targetCount: Int,
    val gameId: Int?,
    val gameName: String?,
    val playerIds: List<String>,
    val playerNames: List<String>,
    val startDate: String?,
    val endDate: String?,
    val createdAt: Long,
    val streakPeriod: String?,
    val status: String
) {
    fun toModel(): Challenge = Challenge(
        id = id,
        title = title,
        type = runCatching { ChallengeType.valueOf(type) }.getOrDefault(ChallengeType.PLAY_N_TIMES),
        targetCount = targetCount,
        gameId = gameId,
        gameName = gameName,
        playerIds = playerIds,
        playerNames = playerNames,
        startDate = startDate,
        endDate = endDate,
        createdAt = createdAt,
        streakPeriod = streakPeriod,
        status = runCatching { ChallengeStatus.valueOf(status) }.getOrDefault(ChallengeStatus.ACTIVE)
    )

    companion object {
        fun fromModel(c: Challenge) = ChallengeEntity(
            id = c.id,
            title = c.title,
            type = c.type.name,
            targetCount = c.targetCount,
            gameId = c.gameId,
            gameName = c.gameName,
            playerIds = c.playerIds,
            playerNames = c.playerNames,
            startDate = c.startDate,
            endDate = c.endDate,
            createdAt = c.createdAt,
            streakPeriod = c.streakPeriod,
            status = c.status.name
        )
    }
}

@Entity(tableName = "game_recognition_hints")
private data class GameRecognitionHintEntity(
    @PrimaryKey val gameObjectId: String,
    val gameName: String,
    val normalizedTitles: List<String>,
    val normalizedCategories: List<String>,
    val confirmedAt: Long,
    val timesConfirmed: Int
) {
    fun toModel(): GameRecognitionHint = GameRecognitionHint(
        gameObjectId = gameObjectId,
        gameName = gameName,
        normalizedTitles = normalizedTitles,
        normalizedCategories = normalizedCategories,
        confirmedAt = confirmedAt,
        timesConfirmed = timesConfirmed
    )

    companion object {
        fun fromModel(h: GameRecognitionHint) = GameRecognitionHintEntity(
            gameObjectId = h.gameObjectId,
            gameName = h.gameName,
            normalizedTitles = h.normalizedTitles,
            normalizedCategories = h.normalizedCategories,
            confirmedAt = h.confirmedAt,
            timesConfirmed = h.timesConfirmed
        )
    }
}

@Entity(tableName = "player_recognition_hints", primaryKeys = ["scannedNameNormalized", "confirmedRosterPlayerId"])
private data class PlayerRecognitionHintEntity(
    val scannedNameNormalized: String,
    val confirmedRosterPlayerId: String,
    val playerDisplayName: String,
    val timesConfirmed: Int,
    val lastConfirmedAt: Long
) {
    fun toModel(): PlayerRecognitionHint = PlayerRecognitionHint(
        scannedNameNormalized = scannedNameNormalized,
        confirmedRosterPlayerId = confirmedRosterPlayerId,
        playerDisplayName = playerDisplayName,
        timesConfirmed = timesConfirmed,
        lastConfirmedAt = lastConfirmedAt
    )

    companion object {
        fun fromModel(h: PlayerRecognitionHint) = PlayerRecognitionHintEntity(
            scannedNameNormalized = h.scannedNameNormalized,
            confirmedRosterPlayerId = h.confirmedRosterPlayerId,
            playerDisplayName = h.playerDisplayName,
            timesConfirmed = h.timesConfirmed,
            lastConfirmedAt = h.lastConfirmedAt
        )
    }
}

// ── DAO result types ──────────────────────────────────────────────────────────

data class PlaySummaryRow(val totalPlays: Int, val uniqueGames: Int, val totalMinutes: Int)
data class GamePlayRow(val gameId: Int, val gameName: String, val plays: Int)
data class DatePlayRow(val date: String, val plays: Int)
data class NamePlayRow(val gameName: String, val plays: Int)
