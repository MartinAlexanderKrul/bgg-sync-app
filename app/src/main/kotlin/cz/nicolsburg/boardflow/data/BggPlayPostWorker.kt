package cz.nicolsburg.boardflow.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.nicolsburg.boardflow.model.PlayerResult
import java.time.LocalDate

class BggPlayPostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePreferences(applicationContext)
        val store = CanonicalCollectionStore.getInstance(applicationContext)
        val repository = BggRepository()

        val creds = prefs.getCredentials() ?: return Result.success()
        val unposted = store.getLoggedPlays().filter { !it.postedToBgg }
        if (unposted.isEmpty()) return Result.success()

        repository.login(creds).onFailure { return Result.retry() }

        val roster = store.getPlayers()

        var anyFailed = false
        for (play in unposted) {
            val players = play.players.map { pr ->
                val trimmed = pr.name.trim()
                val canonical = roster.firstOrNull { p ->
                    (listOf(p.displayName) + p.aliases).any { it.trim().equals(trimmed, ignoreCase = true) }
                }
                if (canonical != null) pr.copy(name = canonical.displayName) else pr.copy(name = trimmed)
            }
            val usernameMap = buildUsernameMap(players, roster)
            repository.logPlay(
                gameId = play.gameId,
                date = LocalDate.parse(play.date),
                players = players,
                playerBggUsernames = usernameMap,
                durationMinutes = play.durationMinutes,
                location = play.location,
                comments = play.comments,
                quantity = play.quantity,
                incomplete = play.incomplete,
                nowInStats = play.nowInStats
            ).onSuccess { savedPlayId ->
                val posted = play.copy(
                    id = savedPlayId ?: play.id,
                    players = players,
                    postedToBgg = true
                )
                store.saveLoggedPlay(posted)
                if (posted.id != play.id) store.deleteLoggedPlay(play.id)
                Log.i(TAG, "Posted play ${play.id} -> ${posted.id}")
            }.onFailure {
                Log.w(TAG, "Failed to post play ${play.id}: ${it.message}")
                anyFailed = true
            }
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    private fun buildUsernameMap(players: List<PlayerResult>, roster: List<cz.nicolsburg.boardflow.model.Player>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        players.forEachIndexed { index, pr ->
            val match = roster.firstOrNull { p ->
                (listOf(p.displayName) + p.aliases).any { it.trim().equals(pr.name.trim(), ignoreCase = true) }
            }
            if (match != null && match.bggUsername.isNotBlank()) result[index] = match.bggUsername
        }
        return result
    }

    companion object {
        private const val TAG = "BggPlayPostWorker"
    }
}
