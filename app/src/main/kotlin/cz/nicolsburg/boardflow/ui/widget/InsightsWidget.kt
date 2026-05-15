package cz.nicolsburg.boardflow.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.nicolsburg.boardflow.MainActivity
import cz.nicolsburg.boardflow.R
import cz.nicolsburg.boardflow.data.CanonicalCollectionStore
import cz.nicolsburg.boardflow.model.LoggedPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.abs

// ══════════════════════════════════════════════════════════════════════════════
// InsightsGlanceWidget — Jetpack Glance composable widget
//
// Three responsive tiers driven by SizeMode.Responsive:
//
//   SMALL    (≈2×2)  — game title + relative date + camera button.
//                       No branding header. Tight, immediately readable.
//
//   COMPACT  (≈3×1)  — PRIMARY/default layout. Cinzel header + game·day
//                       + camera button. Maximises info density horizontally.
//
//   EXPANDED (≈3×2+) — branding header, game title, date+player count,
//                       full player list with scores. Camera button top-right.
//
// Glance renders one RemoteViews per size entry and the launcher picks the
// largest that fits the available widget space — no manual dimension polling,
// no "Can't load widget" crashes from weight-based visibility toggling.
// ══════════════════════════════════════════════════════════════════════════════

class InsightsGlanceWidget : GlanceAppWidget() {

    companion object {
        // Responsive breakpoints.
        // Typical launcher cell ≈ 80dp wide × 110dp tall.
        val SMALL    = DpSize(100.dp, 100.dp)  // 2×2 — minimal quick-glance
        val COMPACT  = DpSize(200.dp,  58.dp)  // 3×1 / 4×1 — horizontal primary
        val EXPANDED = DpSize(200.dp, 140.dp)  // 3×2+ — full session detail

        const val ACTION_ROTATE  = "cz.nicolsburg.boardflow.ACTION_ROTATE_INSIGHT"
        internal const val INTERVAL_MS = 5 * 60 * 1000L

        private const val PREFS_NAME   = "InsightsWidgetPrefs"
        private const val KEY_LAST_DAY = "last_day"
        private const val KEY_LAST_TXT = "last_text"

        // Accent colours (ARGB hex parsed once at load time)
        internal val NEUTRAL = Color(android.graphics.Color.parseColor("#FEB316"))
        internal val AMBER   = Color(android.graphics.Color.parseColor("#FEB316"))
        internal val BLUE    = Color(android.graphics.Color.parseColor("#7EA7FF"))
        internal val TEAL    = Color(android.graphics.Color.parseColor("#80CBC4"))
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, COMPACT, EXPANDED))

    // ── Glance entry point ────────────────────────────────────────────────────

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (snapshot, _) = computeSnapshot(context)

        // Pre-render Cinzel Decorative header bitmaps off the main thread.
        // Null on font-load failure → layout omits the image gracefully.
        val (headerCompactBmp, headerExpandedBmp) = withContext(Dispatchers.Default) {
            val compact  = runCatching { renderHeader(context, snapshot.header,  9f, snapshot.accentColor) }.getOrNull()
            val expanded = runCatching { renderHeader(context, snapshot.header, 11f, snapshot.accentColor) }.getOrNull()
            compact to expanded
        }

        provideContent {
            WidgetRoot(snapshot, headerCompactBmp, headerExpandedBmp)
        }
    }

    // ── Root dispatcher ───────────────────────────────────────────────────────

    @Composable
    private fun WidgetRoot(
        snapshot:          WidgetSnapshot,
        headerCompactBmp:  Bitmap?,
        headerExpandedBmp: Bitmap?,
    ) {
        val size       = LocalSize.current
        val openAction: Action = actionStartActivity<MainActivity>()
        val scanAction: Action = actionRunCallback<QuickScanCallback>()

        when {
            size.height >= EXPANDED.height ->
                ExpandedLayout(snapshot, headerExpandedBmp, openAction, scanAction)
            size.width >= COMPACT.width    ->
                CompactLayout(snapshot, headerCompactBmp, openAction, scanAction)
            else                           ->
                SmallLayout(snapshot, openAction, scanAction)
        }
    }

    // ── SMALL layout (≈2×2) ───────────────────────────────────────────────────
    //
    //  ┌────────────────────┐
    //  │                    │
    //  │  Everdell    📷   │
    //  │  Today             │
    //  │                    │
    //  └────────────────────┘

    @Composable
    private fun SmallLayout(
        snapshot:   WidgetSnapshot,
        openAction: Action,
        scanAction: Action,
    ) {
        val gameName = snapshot.play?.gameName
            ?: snapshot.text.lines().firstOrNull()
            ?: snapshot.text
        val dayStr   = dayLabel(snapshot.playDate, LocalDate.now())

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .clickable(openAction)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier          = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text     = gameName,
                        maxLines = 2,
                        style    = TextStyle(
                            color      = ColorProvider(Color.White),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (dayStr.isNotBlank()) {
                        Spacer(GlanceModifier.height(3.dp))
                        Text(
                            text     = dayStr,
                            maxLines = 1,
                            style    = TextStyle(
                                color    = ColorProvider(Color.White.copy(alpha = 0.65f)),
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                Image(
                    provider           = ImageProvider(R.drawable.ic_widget_camera),
                    contentDescription = null,
                    modifier           = GlanceModifier.size(28.dp).clickable(scanAction),
                    contentScale       = ContentScale.Fit,
                )
            }
        }
    }

    // ── COMPACT layout (≈3×1 / 4×1) ──────────────────────────────────────────
    //
    //  ┌─────────────────────────────────────────┐
    //  │  BOARD FLOW · LAST SESSION              │
    //  │  Everdell  ·  Today               📷  │
    //  └─────────────────────────────────────────┘

    @Composable
    private fun CompactLayout(
        snapshot:   WidgetSnapshot,
        headerBmp:  Bitmap?,
        openAction: Action,
        scanAction: Action,
    ) {
        val today    = LocalDate.now()
        val gameLine = snapshot.play?.let { play ->
            "${play.gameName}  ·  ${dayLabel(snapshot.playDate, today)}"
        } ?: snapshot.text.lines().firstOrNull() ?: snapshot.text

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .clickable(openAction)
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier          = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier            = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    if (headerBmp != null) {
                        Image(
                            provider           = ImageProvider(headerBmp),
                            contentDescription = snapshot.header,
                            modifier           = GlanceModifier.wrapContentSize(),
                            contentScale       = ContentScale.Fit,
                        )
                        Spacer(GlanceModifier.height(2.dp))
                    }
                    Text(
                        text     = gameLine,
                        maxLines = 1,
                        style    = TextStyle(
                            color      = ColorProvider(Color.White),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
                Image(
                    provider           = ImageProvider(R.drawable.ic_widget_camera),
                    contentDescription = null,
                    modifier           = GlanceModifier.size(30.dp).clickable(scanAction),
                    contentScale       = ContentScale.Fit,
                )
            }
        }
    }

    // ── EXPANDED layout (≈3×2 / 4×2+) ───────────────────────────────────────
    //
    //  ┌─────────────────────────────────────────┐
    //  │  BOARD FLOW · LAST SESSION         📷  │
    //  │  Everdell                               │
    //  │  Today  ·  4 players                    │
    //  │  ──────────────────────────────────── │
    //  │  Martin  38  ★                          │
    //  │  Yotam   25                             │
    //  └─────────────────────────────────────────┘

    @Composable
    private fun ExpandedLayout(
        snapshot:   WidgetSnapshot,
        headerBmp:  Bitmap?,
        openAction: Action,
        scanAction: Action,
    ) {
        val today       = LocalDate.now()
        val gameName    = snapshot.play?.gameName
            ?: snapshot.text.lines().firstOrNull()
            ?: snapshot.text
        val dayStr      = dayLabel(snapshot.playDate, today)
        val players     = snapshot.play?.players?.filter { it.name.isNotBlank() } ?: emptyList()
        val subtitle    = if (players.isEmpty()) dayStr
                          else "$dayStr  ·  ${players.size} player${if (players.size == 1) "" else "s"}"
        val playersText = formatPlayersTall(snapshot.play, snapshot.playDate)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .clickable(openAction)
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {

                // Header row: Cinzel bitmap + camera icon
                Row(
                    modifier          = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = GlanceModifier.defaultWeight()) {
                        if (headerBmp != null) {
                            Image(
                                provider           = ImageProvider(headerBmp),
                                contentDescription = snapshot.header,
                                modifier           = GlanceModifier.wrapContentSize(),
                                contentScale       = ContentScale.Fit,
                            )
                        }
                    }
                    Image(
                        provider           = ImageProvider(R.drawable.ic_widget_camera),
                        contentDescription = null,
                        modifier           = GlanceModifier.size(32.dp).clickable(scanAction),
                        contentScale       = ContentScale.Fit,
                    )
                }

                Spacer(GlanceModifier.height(6.dp))

                // Game title — large and bold
                Text(
                    text     = gameName,
                    maxLines = 1,
                    style    = TextStyle(
                        color      = ColorProvider(Color.White),
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )

                Spacer(GlanceModifier.height(2.dp))

                // Date + player count subtitle
                Text(
                    text     = subtitle,
                    maxLines = 1,
                    style    = TextStyle(
                        color    = ColorProvider(Color.White.copy(alpha = 0.65f)),
                        fontSize = 12.sp,
                    ),
                )

                Spacer(GlanceModifier.height(8.dp))

                // Hairline divider
                Spacer(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorProvider(Color.White.copy(alpha = 0.20f)))
                )

                Spacer(GlanceModifier.height(7.dp))

                // Players with scores + winner star
                if (playersText.isNotBlank()) {
                    Text(
                        text     = playersText,
                        maxLines = 6,
                        style    = TextStyle(
                            color    = ColorProvider(Color.White.copy(alpha = 0.80f)),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }

    // ── Snapshot computation ──────────────────────────────────────────────────

    internal suspend fun computeSnapshot(context: Context): Pair<WidgetSnapshot, List<WidgetSnapshot>> {
        return runCatching {
            val plays = withContext(Dispatchers.IO) {
                CanonicalCollectionStore.getInstance(context).getLoggedPlays()
            }
            chooseDailySnapshot(context, plays)
        }.getOrElse {
            val fb = WidgetSnapshot(header = "Board Flow", text = "Every collection starts somewhere.", accentColor = NEUTRAL)
            fb to listOf(fb)
        }
    }

    private fun chooseDailySnapshot(context: Context, plays: List<LoggedPlay>): Pair<WidgetSnapshot, List<WidgetSnapshot>> {
        if (plays.isEmpty()) {
            val fb = WidgetSnapshot(header = "Board Flow", text = "Every collection starts somewhere.", accentColor = NEUTRAL)
            return fb to listOf(fb)
        }

        val today      = LocalDate.now()
        val candidates = buildSnapshotCandidates(plays, today)
            .sortedWith(compareByDescending<WidgetSnapshot> { it.priority }.thenBy { it.id })

        val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDay  = prefs.getString(KEY_LAST_DAY, null)
        val lastText = prefs.getString(KEY_LAST_TXT, null)
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
            .putString(KEY_LAST_TXT, choice.text)
            .apply()

        return choice to candidates
    }

    private fun buildSnapshotCandidates(plays: List<LoggedPlay>, today: LocalDate): List<WidgetSnapshot> {
        val datedPlays = plays.mapNotNull { play -> parseDate(play)?.let { it to play } }
        val byGame = plays.groupBy { it.gameId to it.gameName }
            .map { (game, gp) ->
                GameCount(
                    id       = game.first,
                    name     = game.second,
                    plays    = gp.sumOf { it.quantity.coerceAtLeast(1) },
                    lastDate = gp.mapNotNull(::parseDate).maxOrNull()
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
        val marks = setOf(25, 50, 100, 200)
        return games
            .filter { it.plays in marks && it.lastDate != null && !it.lastDate.isBefore(today.minusDays(2)) }
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
        val dayStr       = dayLabel(date, today)
        val players      = play.players.filter { it.name.isNotBlank() }
        val winner       = players.firstOrNull { it.isWinner }?.name?.trim()

        val line1 = "${play.gameName}  ·  $dayStr"
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
        val recentKeys = mutableSetOf<String>()
        plays.filter { parseDate(it)?.let { d -> !d.isBefore(today.minusDays(7)) } == true }
            .forEach { play -> playerPairs(play).forEach { recentKeys += it.key } }
        if (recentKeys.isEmpty()) return null

        val totals = mutableMapOf<String, RivalryCount>()
        plays.forEach { play ->
            playerPairs(play).forEach { pair ->
                val c = totals[pair.key] ?: RivalryCount(pair.a, pair.b, 0, 0, 0)
                totals[pair.key] = c.copy(
                    plays = c.plays + 1,
                    aWins = c.aWins + if (pair.aWon) 1 else 0,
                    bWins = c.bWins + if (pair.bWon) 1 else 0
                )
            }
        }

        return totals.filterKeys { it in recentKeys }.values
            .filter { it.plays >= 3 && it.aWins != it.bWins }
            .maxWithOrNull(compareBy<RivalryCount> { abs(it.aWins - it.bWins) }.thenBy { it.plays })
            ?.let { r ->
                val aLeads = r.aWins > r.bWins
                val leader = if (aLeads) r.a else r.b
                val leadW  = if (aLeads) r.aWins else r.bWins
                val trailW = if (aLeads) r.bWins else r.aWins
                WidgetSnapshot(
                    id          = "rivalry_${r.a}_${r.b}_${r.plays}",
                    header      = "Rivalry Pulse",
                    text        = "$leader leads $leadW–$trailW. The table remembers.",
                    accentColor = TEAL,
                    priority    = 70
                )
            }
    }

    private fun dormantGame(games: List<GameCount>, today: LocalDate): WidgetSnapshot? {
        return games
            .filter { it.plays >= 2 && it.lastDate != null }
            .mapNotNull { g ->
                val days = today.toEpochDay() - g.lastDate!!.toEpochDay()
                if (days < 30) null else g to days
            }
            .maxByOrNull { it.second }
            ?.let { (g, days) ->
                WidgetSnapshot(
                    id          = "dormant_${g.id}_$days",
                    header      = "Waiting",
                    text        = "${g.name} hasn't been played in ${formatAge(days)}.",
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
            .mapValues { (_, mp) -> mp.sumOf { it.quantity.coerceAtLeast(1) } }
        val best   = byMonth.values.maxOrNull() ?: monthCount
        val month  = today.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())
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

    /** One line per player: name   score   ★ for winner. Falls back to date string. */
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
        today              -> "Today"
        today.minusDays(1) -> "Yesterday"
        else               -> date?.dayOfWeek?.getDisplayName(JTextStyle.FULL, Locale.getDefault()) ?: ""
    }

    private fun playerPairs(play: LoggedPlay): List<PlayerPair> {
        val ps = play.players
            .mapNotNull { r -> r.name.trim().takeIf { it.isNotBlank() }?.let { it to r.isWinner } }
            .distinctBy { it.first.lowercase() }
        if (ps.size < 2) return emptyList()
        return buildList {
            for (i in ps.indices) for (j in i + 1 until ps.size) {
                val (a, b) = listOf(ps[i], ps[j]).sortedBy { it.first.lowercase() }
                add(PlayerPair(a.first, b.first, a.second, b.second))
            }
        }
    }

    private fun parseDate(play: LoggedPlay): LocalDate? =
        runCatching { LocalDate.parse(play.date) }.getOrNull()

    private fun formatAge(days: Long): String = when {
        days < 60  -> "$days days"
        days < 365 -> "${(days / 30).coerceAtLeast(1).let { "$it month${if (it == 1L) "" else "s"}" }}"
        else       -> "${(days / 365).coerceAtLeast(1).let { "$it year${if (it == 1L) "" else "s"}" }}"
    }

    // ── Cinzel Decorative header renderer ─────────────────────────────────────

    private fun renderHeader(context: Context, text: String, textSizeSp: Float, color: Color): Bitmap {
        val density  = context.resources.displayMetrics.density
        val typeface = ResourcesCompat.getFont(context, R.font.cinzel_decorative_bold)
        val argb     = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red   * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue  * 255).toInt()
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize      = textSizeSp * density
            this.color    = argb
            textAlign     = Paint.Align.LEFT
        }
        val fm  = paint.fontMetrics
        val w   = paint.measureText(text).toInt().coerceAtLeast(1)
        val h   = (fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 0f, -fm.ascent, paint)
        return bmp
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    internal data class WidgetSnapshot(
        val id:          String      = "default",
        val header:      String,
        val text:        String,
        val accentColor: Color,
        val priority:    Int         = 0,
        val play:        LoggedPlay? = null,
        val playDate:    LocalDate?  = null
    )

    private data class GameCount(val id: Int, val name: String, val plays: Int, val lastDate: LocalDate?)

    private data class PlayerPair(val a: String, val b: String, val aWon: Boolean, val bWon: Boolean) {
        val key = "${a.lowercase()}|${b.lowercase()}"
    }

    private data class RivalryCount(val a: String, val b: String, val plays: Int, val aWins: Int, val bWins: Int)
}

// ══════════════════════════════════════════════════════════════════════════════
// QuickScanCallback — Glance ActionCallback that starts MainActivity with the
// Quick Scan action. Required because Glance 1.1 has no actionStartActivity(Intent).
// ══════════════════════════════════════════════════════════════════════════════

class QuickScanCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                action = QuickScanWidget.ACTION_QUICK_SCAN
                flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// InsightsWidget — BroadcastReceiver registered in AndroidManifest.xml.
//
// Class name kept as "InsightsWidget" (same as the old AppWidgetProvider) so
// existing home-screen widget instances survive the Glance migration without
// needing to be re-added by the user.
// ══════════════════════════════════════════════════════════════════════════════

class InsightsWidget : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = InsightsGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)          // lets Glance handle APPWIDGET_UPDATE etc.
        when (intent.action) {
            InsightsGlanceWidget.ACTION_ROTATE -> {
                // Rotate the daily snapshot and redraw every widget instance.
                MainScope().launch { InsightsGlanceWidget().updateAll(context) }
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> {
                // Ensure the rotation alarm is running whenever the widget is present.
                scheduleRotation(context)
            }
        }
    }

    private fun scheduleRotation(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + InsightsGlanceWidget.INTERVAL_MS,
            InsightsGlanceWidget.INTERVAL_MS,
            rotationIntent(context)
        )
    }

    private fun rotationIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 2,
            Intent(context, InsightsWidget::class.java).apply {
                action = InsightsGlanceWidget.ACTION_ROTATE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
