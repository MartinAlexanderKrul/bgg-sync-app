package cz.nicolsburg.boardflow.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.nicolsburg.boardflow.MainActivity
import cz.nicolsburg.boardflow.R
import cz.nicolsburg.boardflow.model.ChallengeStatus
import cz.nicolsburg.boardflow.model.ChallengeType
import cz.nicolsburg.boardflow.model.LoggedPlay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

class ChallengeNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "challenge_events"
        const val WORK_NAME = "challenge_notifications"
    }

    override suspend fun doWork(): Result {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val store = CanonicalCollectionStore.getInstance(applicationContext)
        val challenges = store.getChallenges()
        val plays = store.getLoggedPlays()
        val prefs = applicationContext.getSharedPreferences("challenge_notif", Context.MODE_PRIVATE)
        val today = LocalDate.now()

        challenges.filter { it.status == ChallengeStatus.ACTIVE }.forEach { challenge ->
            val filteredPlays = plays.filter { play ->
                val afterStart = challenge.startDate == null || play.date >= challenge.startDate
                val beforeEnd = challenge.endDate == null || play.date <= challenge.endDate
                afterStart && beforeEnd
            }
            val current = computeProgress(challenge, filteredPlays)

            // Deadline in 3 days
            challenge.endDate?.let { endStr ->
                val endDate = runCatching { LocalDate.parse(endStr) }.getOrNull() ?: return@let
                val daysLeft = ChronoUnit.DAYS.between(today, endDate)
                val key = "deadline_${challenge.id}"
                if (daysLeft == 3L && !prefs.getBoolean(key, false) && current < challenge.targetCount) {
                    notify(
                        id = (challenge.id + "_deadline").hashCode(),
                        title = "Challenge ending soon",
                        text = "\"${challenge.title}\" — 3 days left ($current/${challenge.targetCount})"
                    )
                    prefs.edit().putBoolean(key, true).apply()
                }
            }

            // Challenge completed
            val completeKey = "complete_${challenge.id}"
            if (!prefs.getBoolean(completeKey, false) && current >= challenge.targetCount) {
                notify(
                    id = (challenge.id + "_complete").hashCode(),
                    title = "Challenge complete!",
                    text = "\"${challenge.title}\" — well done!"
                )
                prefs.edit().putBoolean(completeKey, true).apply()
            }

            // Streak broken (PLAY_STREAK challenges only)
            if (challenge.type == ChallengeType.PLAY_STREAK) {
                val period = challenge.streakPeriod ?: "WEEKLY"
                val lastKey = completedPeriodKey(today, period, 1)
                val prevKey = completedPeriodKey(today, period, 2)
                val lastHasPlay = filteredPlays.any { periodKey(it.date, period) == lastKey }
                val prevHasPlay = filteredPlays.any { periodKey(it.date, period) == prevKey }
                val brokenKey = "streak_broken_${challenge.id}_$lastKey"
                if (!lastHasPlay && prevHasPlay && !prefs.getBoolean(brokenKey, false)) {
                    val periodLabel = when (period) {
                        "DAILY" -> "daily"; "MONTHLY" -> "monthly"; else -> "weekly"
                    }
                    notify(
                        id = (challenge.id + "_streak").hashCode(),
                        title = "Streak broken",
                        text = "Your $periodLabel streak for \"${challenge.title}\" was broken"
                    )
                    prefs.edit().putBoolean(brokenKey, true).apply()
                }
            }
        }

        return Result.success()
    }

    private fun computeProgress(challenge: cz.nicolsburg.boardflow.model.Challenge, plays: List<LoggedPlay>): Int =
        when (challenge.type) {
            ChallengeType.PLAY_N_TIMES ->
                plays.sumOf { it.quantity.coerceAtLeast(1) }
            ChallengeType.PLAY_SPECIFIC_GAME ->
                plays.filter { it.gameId == challenge.gameId }.sumOf { it.quantity.coerceAtLeast(1) }
            ChallengeType.PLAY_N_DISTINCT ->
                plays.map { it.gameId }.distinct().size
            ChallengeType.PLAY_WITH_GROUP_N_TIMES ->
                plays.count { play ->
                    val playNames = play.players.map { it.name.trim().lowercase() }.toSet()
                    challenge.playerNames.all { it.trim().lowercase() in playNames }
                }
            ChallengeType.PLAY_STREAK ->
                plays.bestPlayStreak(challenge.streakPeriod ?: "WEEKLY")
            ChallengeType.PLAYER_WIN_STREAK ->
                plays.bestWinStreak(challenge.playerNames.map { it.trim().lowercase() }.toSet())
            ChallengeType.PLAY_N_UNPLAYED ->
                Int.MAX_VALUE // needs full collection data; skip completion notification
        }

    private fun List<LoggedPlay>.bestPlayStreak(period: String): Int {
        if (isEmpty()) return 0
        val keys = mapNotNull { periodKey(it.date, period) }.distinct().sorted()
        if (keys.isEmpty()) return 0
        var best = 1; var current = 1
        for (i in 1 until keys.size) {
            if (areConsecutivePeriods(keys[i - 1], keys[i], period)) {
                current++; if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }

    private fun List<LoggedPlay>.bestWinStreak(playerNames: Set<String>): Int {
        if (playerNames.isEmpty()) return 0
        val relevant = filter { play ->
            play.players.any { it.name.trim().lowercase() in playerNames }
        }.sortedWith(compareBy({ it.date }, { it.playedAt ?: 0L }, { it.id }))
        var best = 0; var current = 0
        relevant.forEach { play ->
            if (play.players.any { it.name.trim().lowercase() in playerNames && it.isWinner }) {
                current++; if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    private fun periodKey(dateStr: String, period: String): String? {
        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return null
        return when (period) {
            "DAILY" -> dateStr
            "MONTHLY" -> "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
            else -> {
                val y = date.get(WeekFields.ISO.weekBasedYear())
                val w = date.get(WeekFields.ISO.weekOfWeekBasedYear())
                "$y-${w.toString().padStart(2, '0')}"
            }
        }
    }

    private fun completedPeriodKey(today: LocalDate, period: String, periodsAgo: Int): String? {
        val date = when (period) {
            "DAILY" -> today.minusDays(periodsAgo.toLong())
            "MONTHLY" -> today.minusMonths(periodsAgo.toLong())
            else -> today.minusWeeks(periodsAgo.toLong())
        }
        return periodKey(date.toString(), period)
    }

    private fun areConsecutivePeriods(a: String, b: String, period: String): Boolean {
        if (period == "DAILY") {
            val da = runCatching { LocalDate.parse(a) }.getOrNull() ?: return false
            val db = runCatching { LocalDate.parse(b) }.getOrNull() ?: return false
            return ChronoUnit.DAYS.between(da, db) == 1L
        }
        val partsA = a.split("-"); val partsB = b.split("-")
        if (partsA.size < 2 || partsB.size < 2) return false
        val ay = partsA[0].toIntOrNull() ?: return false
        val an = partsA[1].toIntOrNull() ?: return false
        val by = partsB[0].toIntOrNull() ?: return false
        val bn = partsB[1].toIntOrNull() ?: return false
        return if (period == "MONTHLY") {
            (by * 12 + bn) - (ay * 12 + an) == 1
        } else {
            when {
                ay == by -> bn - an == 1
                by == ay + 1 && bn == 1 -> {
                    val lastWeek = LocalDate.of(ay, 12, 28).get(WeekFields.ISO.weekOfWeekBasedYear())
                    an == lastWeek
                }
                else -> false
            }
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_challenge)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}
