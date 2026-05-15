package cz.nicolsburg.boardflow.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import cz.nicolsburg.boardflow.R
import cz.nicolsburg.boardflow.data.CanonicalCollectionStore
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.PlayerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

class InsightsWidget : AppWidgetProvider() {

    // ── Size tiers ────────────────────────────────────────────────────────────
    // Driven by the live widget dimensions read from the options bundle.
    //
    //  MICRO    < 110dp wide  OR  < 60dp tall  →  camera icon only
    //  COMPACT  ≥ 110dp wide AND  60–100dp tall →  1-line text + icon
    //  STANDARD ≥ 110dp wide AND 100–170dp tall →  header + 2-line text (default 3×2)
    //  TALL     ≥ 110dp wide AND 170–250dp tall →  standard + players w/ scores
    //  LARGE    ≥ 110dp wide AND   ≥ 250dp tall →  tall + hero insight excerpt
    //
    //  WIDE upgrade: any of STANDARD/TALL/LARGE with width ≥ 290dp shows app logo.

    private enum class WidgetTier { MICRO, COMPACT, STANDARD, TALL, LARGE }

    private fun chooseTier(widthDp: Int, heightDp: Int): WidgetTier = when {
        widthDp < 110 || heightDp < 60 -> WidgetTier.MICRO
        heightDp < 100                  -> WidgetTier.COMPACT
        heightDp < 170                  -> WidgetTier.STANDARD
        heightDp < 250                  -> WidgetTier.TALL
        else                            -> WidgetTier.LARGE
    }

    // ── AppWidgetProvider ─────────────────────────────────────────────────────

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val (snapshot, candidates) = computeSnapshot(context)
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, snapshot, candidates)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val (snapshot, candidates) = computeSnapshot(context)
        updateWidget(context, appWidgetManager, appWidgetId, snapshot, candidates)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ROTATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, InsightsWidget::class.java))
            if (ids.isEmpty()) return
            val (snapshot, candidates) = computeSnapshot(context)
            for (id in ids) {
                updateWidget(context, appWidgetManager, id, snapshot, candidates)
            }
        }
    }

    // ── Core update ───────────────────────────────────────────────────────────

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: WidgetSnapshot,
        allCandidates: List<WidgetSnapshot>
    ) {
        val options   = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp   = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
        val heightDp  = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 100)
        val tier      = chooseTier(widthDp, heightDp)
        val showLogo  = widthDp >= 290 && tier != WidgetTier.MICRO && tier != WidgetTier.COMPACT

        val layoutId = when (tier) {
            WidgetTier.MICRO    -> R.layout.widget_insights_micro
            WidgetTier.COMPACT  -> R.layout.widget_insights_compact
            WidgetTier.STANDARD -> R.layout.widget_insights
            WidgetTier.TALL     -> R.layout.widget_insights_tall
            WidgetTier.LARGE    -> R.layout.widget_insights_large
        }

        val views = RemoteViews(context.packageName, layoutId)

        // ── Click intents ─────────────────────────────────────────────────────
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, cz.nicolsburg.boardflow.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val scanIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, cz.nicolsburg.boardflow.MainActivity::class.java).apply {
                action = QuickScanWidget.ACTION_QUICK_SCAN
                flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_insights_root, openAppIntent)
        runCatching { views.setOnClickPendingIntent(R.id.widget_insights_scan, scanIntent) }

        // ── Content ───────────────────────────────────────────────────────────
        when (tier) {
            WidgetTier.MICRO -> {
                // Icon only — scan tap opens quick scan
                views.setOnClickPendingIntent(R.id.widget_insights_scan, scanIntent)
            }

            WidgetTier.COMPACT -> {
                // 1-line: GameName  ·  DayLabel
                val line = snapshot.play?.let { play ->
                    val dayLabel = dayLabel(snapshot.playDate, LocalDate.now())
                    "${play.gameName}  ·  $dayLabel"
                } ?: snapshot.text.lines().firstOrNull() ?: snapshot.text
                views.setTextViewText(R.id.widget_insights_text, line)
            }

            WidgetTier.STANDARD -> {
                // Header bitmap + 2-line text
                runCatching {
                    views.setImageViewBitmap(
                        R.id.widget_insights_header,
                        renderSingleLine(context, snapshot.header, 11f, snapshot.accentColor)
                    )
                }
                views.setTextViewText(R.id.widget_insights_text, snapshot.text)
                if (showLogo) runCatching {
                    views.setViewVisibility(R.id.widget_insights_logo, View.VISIBLE)
                }
            }

            WidgetTier.TALL -> {
                // Header + game·day + players with scores
                runCatching {
                    views.setImageViewBitmap(
                        R.id.widget_insights_header,
                        renderSingleLine(context, snapshot.header, 11f, snapshot.accentColor)
                    )
                }
                val line1 = snapshot.text.lines().firstOrNull() ?: snapshot.text
                views.setTextViewText(R.id.widget_insights_text, line1)
                views.setTextViewText(
                    R.id.widget_insights_players,
                    formatPlayersTall(snapshot.play, snapshot.playDate)
                )
                if (showLogo) runCatching {
                    views.setViewVisibility(R.id.widget_insights_logo, View.VISIBLE)
                }
            }

            WidgetTier.LARGE -> {
                // Header + game·day + players + hero insight
                runCatching {
                    views.setImageViewBitmap(
                        R.id.widget_insights_header,
                        renderSingleLine(context, snapshot.header, 11f, snapshot.accentColor)
                    )
                }
                val line1 = snapshot.text.lines().firstOrNull() ?: snapshot.text
                views.setTextViewText(R.id.widget_insights_text, line1)
                views.setTextViewText(
                    R.id.widget_insights_players,
                    formatPlayersTall(snapshot.play, snapshot.playDate)
                )
                val hero = allCandidates.firstOrNull { it.id != snapshot.id }
                if (hero != null) {
                    views.setTextViewText(R.id.widget_insights_hero, hero.text)
                    runCatching {
                        views.setViewVisibility(R.id.widget_insights_hero_section, View.VISIBLE)
                    }
                }
                if (showLogo) runCatching {
                    views.setViewVisibility(R.id.widget_insights_logo, View.VISIBLE)
                }
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // ── Snapshot computation ──────────────────────────────────────────────────

    private fun computeSnapshot(context: Context): Pair<WidgetSnapshot, List<WidgetSnapshot>> {
        return runCatching {
            val plays = runBlocking(Dispatchers.IO) {
                CanonicalCollectionStore.getInstance(context).getLoggedPlays()
            }
            chooseDailySnapshot(context, plays)
        }.getOrElse {
            val fallback = WidgetSnapshot(header = "Board Flow", text = "Every collection starts somewhere.", accentColor = NEUTRAL)
            fallback to listOf(fallback)
        }
    }

    private fun chooseDailySnapshot(context: Context, plays: List<LoggedPlay>): Pair<WidgetSnapshot, List<WidgetSnapshot>> {
        if (plays.isEmpty()) {
            val fallback = WidgetSnapshot(header = "Board Flow", text = "Every collection starts somewhere.", accentColor = NEUTRAL)
            return fallback to listOf(fallback)
        }

        val today = LocalDate.now()
        val candidates = buildSnapshotCandidates(plays, today)
            .sortedWith(compareByDescending<WidgetSnapshot> { it.priority }.thenBy { it.id })

        val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDay  = prefs.getString(KEY_LAST_DAY, null)
        val lastText = prefs.getString(KEY_LAST_TEXT, null)
        val todayKey = today.toString()

        val preferred = candidates.firstOrNull()
            ?: WidgetSnapshot(header = "Board Flow", text = "Every play leaves a trace.", accentColor = NEUTRAL)
        val choice = if (lastDay != todayKey && preferred.text == lastText) {
            candidates.firstOrNull { it.text != lastText } ?: preferred
        } else {
            preferred
        }

        prefs.edit()
            .putString(KEY_LAST_DAY, todayKey)
            .putString(KEY_LAST_TEXT, choice.text)
            .apply()

        return choice to candidates
    }

    private fun buildSnapshotCandidates(plays: List<LoggedPlay>, today: LocalDate): List<WidgetSnapshot> {
        val datedPlays = plays.mapNotNull { play -> parseDate(play)?.let { it to play } }
        val byGame = plays.groupBy { it.gameId to it.gameName }
            .map { (game, gamePlays) ->
                GameCount(
                    id       = game.first,
                    name     = game.second,
                    plays    = gamePlays.sumOf { it.quantity.coerceAtLeast(1) },
                    lastDate = gamePlays.mapNotNull(::parseDate).maxOrNull()
                )
            }

        return listOfNotNull(
            approachingMilestone(byGame),
            recentLandmark(byGame, today),
            lastSession(datedPlays, today),
            rivalryPulse(plays, today),
            dormantGame(byGame, today),
            seasonStat(plays, today)
        )
    }

    // ── Snapshot builders ─────────────────────────────────────────────────────

    private fun approachingMilestone(games: List<GameCount>): WidgetSnapshot? {
        val targets = listOf(10, 25, 50, 100, 200)
        return games.mapNotNull { game ->
            val target    = targets.firstOrNull { it > game.plays } ?: return@mapNotNull null
            val remaining = target - game.plays
            if (remaining !in 1..2) return@mapNotNull null
            WidgetSnapshot(
                id          = "approaching_${game.id}_$target",
                header      = "Approaching Landmark",
                text        = "${game.name} is at ${game.plays} plays. ${if (remaining == 1) "One more." else "$remaining more."}",
                accentColor = AMBER,
                priority    = 120 - remaining
            )
        }.maxByOrNull { it.priority }
    }

    private fun recentLandmark(games: List<GameCount>, today: LocalDate): WidgetSnapshot? {
        val landmarkCounts = setOf(25, 50, 100, 200)
        return games
            .filter { it.plays in landmarkCounts && it.lastDate != null && !it.lastDate.isBefore(today.minusDays(2)) }
            .maxByOrNull { it.plays }
            ?.let { game ->
                val line = when (game.plays) {
                    25   -> "25 plays of ${game.name}. This one has history."
                    50   -> "50 plays of ${game.name}. That's a relationship."
                    100  -> "100 plays. ${game.name} stuck around."
                    else -> "${game.plays} plays of ${game.name}. The table remembers."
                }
                WidgetSnapshot("landmark_${game.id}_${game.plays}", "Landmark", line, BLUE, 110)
            }
    }

    private fun lastSession(datedPlays: List<Pair<LocalDate, LoggedPlay>>, today: LocalDate): WidgetSnapshot? {
        val (date, play) = datedPlays.maxByOrNull { it.first } ?: return null
        val dayLabel     = dayLabel(date, today)
        val players      = play.players.filter { it.name.isNotBlank() }
        val winner       = players.firstOrNull { it.isWinner }?.name?.trim()

        // Line 1: game · day
        val line1 = "${play.gameName}  ·  $dayLabel"

        // Line 2: brief player summary for STANDARD/COMPACT
        val line2 = when {
            players.isEmpty() -> null
            else -> {
                val names = if (players.size <= 4)
                    players.joinToString(", ") { it.name.trim() }
                else
                    players.take(3).joinToString(", ") { it.name.trim() } + " +${players.size - 3}"
                if (winner != null) "$names  ·  $winner won" else names
            }
        }

        return WidgetSnapshot(
            id          = "last_${play.id}",
            header      = "Board Flow · Last Session",
            text        = if (line2 != null) "$line1\n$line2" else line1,
            accentColor = NEUTRAL,
            priority    = 80,
            play        = play,
            playDate    = date
        )
    }

    private fun rivalryPulse(plays: List<LoggedPlay>, today: LocalDate): WidgetSnapshot? {
        val recentPairKeys = mutableSetOf<String>()
        plays.filter { play ->
            parseDate(play)?.let { !it.isBefore(today.minusDays(7)) } == true
        }.forEach { play ->
            playerPairs(play).forEach { recentPairKeys += it.key }
        }
        if (recentPairKeys.isEmpty()) return null

        val totals = mutableMapOf<String, RivalryCount>()
        plays.forEach { play ->
            playerPairs(play).forEach { pair ->
                val current = totals[pair.key] ?: RivalryCount(pair.a, pair.b, 0, 0, 0)
                totals[pair.key] = current.copy(
                    plays  = current.plays + 1,
                    aWins  = current.aWins + if (pair.aWon) 1 else 0,
                    bWins  = current.bWins + if (pair.bWon) 1 else 0
                )
            }
        }

        return totals
            .filterKeys { it in recentPairKeys }
            .values
            .filter { it.plays >= 3 && it.aWins != it.bWins }
            .maxWithOrNull(compareBy<RivalryCount> { abs(it.aWins - it.bWins) }.thenBy { it.plays })
            ?.let { rivalry ->
                val aLeads       = rivalry.aWins > rivalry.bWins
                val leader       = if (aLeads) rivalry.a else rivalry.b
                val leadWins     = if (aLeads) rivalry.aWins else rivalry.bWins
                val trailingWins = if (aLeads) rivalry.bWins else rivalry.aWins
                WidgetSnapshot(
                    id          = "rivalry_${rivalry.a}_${rivalry.b}_${rivalry.plays}",
                    header      = "Rivalry Pulse",
                    text        = "$leader leads $leadWins–$trailingWins. The table remembers.",
                    accentColor = TEAL,
                    priority    = 70
                )
            }
    }

    private fun dormantGame(games: List<GameCount>, today: LocalDate): WidgetSnapshot? {
        return games
            .filter { it.plays >= 2 && it.lastDate != null }
            .mapNotNull { game ->
                val days = today.toEpochDay() - game.lastDate!!.toEpochDay()
                if (days < 30) return@mapNotNull null
                game to days
            }
            .maxByOrNull { it.second }
            ?.let { (game, days) ->
                WidgetSnapshot(
                    id          = "dormant_${game.id}_$days",
                    header      = "Waiting",
                    text        = "${game.name} hasn't been played in ${formatAge(days)}.",
                    accentColor = BLUE,
                    priority    = 55
                )
            }
    }

    private fun seasonStat(plays: List<LoggedPlay>, today: LocalDate): WidgetSnapshot? {
        val thisYear   = plays.filter { parseDate(it)?.year == today.year }
        val monthCount = thisYear
            .filter { parseDate(it)?.monthValue == today.monthValue }
            .sumOf { it.quantity.coerceAtLeast(1) }
        if (monthCount == 0) return null

        val byMonth = thisYear.groupBy { parseDate(it)?.monthValue }
            .filterKeys { it != null }
            .mapValues { (_, monthPlays) -> monthPlays.sumOf { it.quantity.coerceAtLeast(1) } }
        val best   = byMonth.values.maxOrNull() ?: monthCount
        val month  = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val suffix = if (monthCount >= best) "Your best month this year." else "The month has shape."
        return WidgetSnapshot(
            id          = "season_${today.year}_${today.monthValue}_$monthCount",
            header      = "Season",
            text        = "$monthCount plays in $month. $suffix",
            accentColor = AMBER,
            priority    = 40
        )
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    /** One player per line: name  score  ★ for winner. Falls back to date string. */
    private fun formatPlayersTall(play: LoggedPlay?, date: LocalDate?): String {
        val players = play?.players?.filter { it.name.isNotBlank() } ?: emptyList()
        if (players.isEmpty()) {
            return date?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) ?: ""
        }
        return players.joinToString("\n") { pr ->
            buildString {
                append(pr.name.trim())
                if (pr.score.isNotBlank()) append("   ${pr.score}")
                if (pr.isWinner) append("  ★")
            }
        }
    }

    private fun dayLabel(date: LocalDate?, today: LocalDate): String = when (date) {
        today               -> "Today"
        today.minusDays(1)  -> "Yesterday"
        else                -> date?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: ""
    }

    private fun playerPairs(play: LoggedPlay): List<PlayerPair> {
        val players = play.players
            .mapNotNull { result ->
                result.name.trim().takeIf { it.isNotBlank() }?.let { it to result.isWinner }
            }
            .distinctBy { it.first.lowercase() }
        if (players.size < 2) return emptyList()

        val pairs = mutableListOf<PlayerPair>()
        for (i in players.indices) {
            for (j in i + 1 until players.size) {
                val (a, b) = listOf(players[i], players[j]).sortedBy { it.first.lowercase() }
                pairs += PlayerPair(a = a.first, b = b.first, aWon = a.second, bWon = b.second)
            }
        }
        return pairs
    }

    private fun parseDate(play: LoggedPlay): LocalDate? =
        runCatching { LocalDate.parse(play.date) }.getOrNull()

    private fun formatAge(days: Long): String = when {
        days < 60  -> "$days days"
        days < 365 -> "${(days / 30).coerceAtLeast(1).let { "$it month${if (it == 1L) "" else "s"}" }}"
        else       -> "${(days / 365).coerceAtLeast(1).let { "$it year${if (it == 1L) "" else "s"}" }}"
    }

    // ── Bitmap header renderer ────────────────────────────────────────────────

    private fun renderSingleLine(context: Context, text: String, textSizeSp: Float, color: Int): Bitmap {
        val density  = context.resources.displayMetrics.density
        val typeface = ResourcesCompat.getFont(context, R.font.cinzel_decorative_bold)
        val paint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize      = textSizeSp * density
            this.color    = color
            textAlign     = Paint.Align.LEFT
        }
        val fm = paint.fontMetrics
        val w  = paint.measureText(text).toInt().coerceAtLeast(1)
        val h  = (fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -fm.ascent, paint)
        return bitmap
    }

    // ── Alarm ─────────────────────────────────────────────────────────────────

    private fun scheduleRotation(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            rotationPendingIntent(context)
        )
    }

    private fun rotationPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 2,
            Intent(context, InsightsWidget::class.java).apply { action = ACTION_ROTATE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ── Data classes ──────────────────────────────────────────────────────────

    private data class PlayerPair(
        val a: String,
        val b: String,
        val aWon: Boolean,
        val bWon: Boolean
    ) {
        val key: String = "${a.lowercase()}|${b.lowercase()}"
    }

    private data class RivalryCount(
        val a: String,
        val b: String,
        val plays: Int,
        val aWins: Int,
        val bWins: Int
    )

    private data class WidgetSnapshot(
        val id: String = "default",
        val header: String,
        val text: String,
        val accentColor: Int,
        val priority: Int = 0,
        val play: LoggedPlay? = null,     // raw play; used in TALL/LARGE for rich player display
        val playDate: LocalDate? = null   // date of the play; used for day label in COMPACT
    )

    private data class GameCount(
        val id: Int,
        val name: String,
        val plays: Int,
        val lastDate: LocalDate?
    )

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val ACTION_ROTATE  = "cz.nicolsburg.boardflow.ACTION_ROTATE_INSIGHT"
        private const val PREFS_NAME     = "InsightsWidgetPrefs"
        private const val KEY_LAST_DAY   = "last_day"
        private const val KEY_LAST_TEXT  = "last_text"
        private const val INTERVAL_MS    = 5 * 60 * 1000L

        private val NEUTRAL = Color.parseColor("#FEB316")
        private val AMBER   = Color.parseColor("#FEB316")
        private val BLUE    = Color.parseColor("#7EA7FF")
        private val TEAL    = Color.parseColor("#80CBC4")
    }
}
