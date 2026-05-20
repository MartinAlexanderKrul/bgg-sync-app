package cz.nicolsburg.boardflow.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import cz.nicolsburg.boardflow.data.CanonicalCollectionStore
import cz.nicolsburg.boardflow.model.Challenge
import cz.nicolsburg.boardflow.model.ChallengeStatus
import cz.nicolsburg.boardflow.model.ChallengeType
import cz.nicolsburg.boardflow.model.LoggedPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StatsGlanceWidget : SessionGlanceWidget() {

    override suspend fun computeSnapshot(context: Context): Pair<WidgetSnapshot, List<WidgetSnapshot>> {
        return runCatching {
            val store = CanonicalCollectionStore.getInstance(context)
            val plays = withContext(Dispatchers.IO) { store.getLoggedPlays() }
            val challenges = withContext(Dispatchers.IO) { store.getChallenges() }
            val today = LocalDate.now()
            val monthStart = today.withDayOfMonth(1).toString()

            val monthPlays = plays.filter { it.date >= monthStart }
            val totalPlays = monthPlays.sumOf { it.quantity.coerceAtLeast(1) }
            val uniqueGames = monthPlays.map { it.gameId }.distinct().size
            val topGames = monthPlays
                .groupBy { it.gameName }
                .mapValues { (_, v) -> v.sumOf { it.quantity.coerceAtLeast(1) } }
                .entries.sortedByDescending { it.value }
                .take(3)

            val primaryText = when (totalPlays) {
                0 -> "No plays this month"
                1 -> "1 play this month"
                else -> "$totalPlays plays this month"
            }
            val subtitleText = when {
                totalPlays == 0 -> ""
                uniqueGames == 1 -> topGames.firstOrNull()?.key ?: "1 game"
                topGames.isNotEmpty() -> "$uniqueGames games  ·  ${topGames.first().key}"
                else -> "$uniqueGames games"
            }

            val urgentChallenge = challenges
                .filter { it.status == ChallengeStatus.ACTIVE }
                .mapNotNull { c ->
                    val progress = computeProgress(c, plays)
                    if (progress >= c.targetCount) null else c to progress
                }
                .minByOrNull { (c, _) ->
                    c.endDate?.let {
                        runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }
                            .getOrDefault(Long.MAX_VALUE)
                    } ?: Long.MAX_VALUE
                }

            val detailText = buildString {
                urgentChallenge?.let { (c, progress) ->
                    val daysLeft = c.endDate?.let {
                        runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }.getOrNull()
                    }
                    val deadlineStr = when {
                        daysLeft == null -> ""
                        daysLeft <= 0 -> "  ·  Today!"
                        daysLeft == 1L -> "  ·  1 day left"
                        else -> "  ·  $daysLeft days left"
                    }
                    append("Challenge  ›  ${c.title}\n$progress / ${c.targetCount}$deadlineStr")
                }
                if (topGames.size > 1) {
                    if (isNotEmpty()) append("\n\n")
                    topGames.forEachIndexed { i, (name, count) ->
                        if (i > 0) append("\n")
                        append("${i + 1}.  $name  ×$count")
                    }
                }
            }

            val daysUntilDeadline = urgentChallenge?.first?.endDate?.let {
                runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }.getOrNull()
            }
            val accentColor = when {
                daysUntilDeadline != null && daysUntilDeadline <= 3 -> AMBER
                urgentChallenge != null -> TEAL
                else -> BLUE
            }

            val snapshot = WidgetSnapshot(
                id = "stats_$monthStart",
                header = "This Month",
                primaryText = primaryText,
                subtitleText = subtitleText,
                detailText = detailText,
                accentColor = accentColor,
                priority = 50,
            )
            snapshot to listOf(snapshot)
        }.getOrElse {
            val fb = WidgetSnapshot(
                header = "This Month",
                primaryText = "Log your first play!",
                accentColor = BLUE,
            )
            fb to listOf(fb)
        }
    }

    private fun computeProgress(challenge: Challenge, plays: List<LoggedPlay>): Int {
        val filtered = plays.filter { play ->
            (challenge.startDate == null || play.date >= challenge.startDate) &&
                    (challenge.endDate == null || play.date <= challenge.endDate)
        }
        return when (challenge.type) {
            ChallengeType.PLAY_N_TIMES ->
                filtered.sumOf { it.quantity.coerceAtLeast(1) }
            ChallengeType.PLAY_SPECIFIC_GAME ->
                filtered.filter { it.gameId == challenge.gameId }.sumOf { it.quantity.coerceAtLeast(1) }
            ChallengeType.PLAY_N_DISTINCT ->
                filtered.map { it.gameId }.distinct().size
            ChallengeType.PLAY_WITH_GROUP_N_TIMES ->
                filtered.count { play ->
                    val playNames = play.players.map { it.name.trim().lowercase() }.toSet()
                    challenge.playerNames.all { it.trim().lowercase() in playNames }
                }
            else -> 0
        }
    }
}

class StatsWidget : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = StatsGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> MainScope().launch { StatsGlanceWidget().updateAll(context) }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> scheduleRefresh(context)
        }
    }

    private fun scheduleRefresh(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + SessionGlanceWidget.INTERVAL_MS,
            SessionGlanceWidget.INTERVAL_MS,
            PendingIntent.getBroadcast(
                context, 4,
                Intent(context, StatsWidget::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    companion object {
        const val ACTION_REFRESH = "cz.nicolsburg.boardflow.ACTION_REFRESH_STATS"
    }
}
