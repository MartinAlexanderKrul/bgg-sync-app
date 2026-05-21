package cz.nicolsburg.boardflow.ui.collection

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cz.nicolsburg.boardflow.data.SecurePreferences
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.SleeveDatabase
import cz.nicolsburg.boardflow.model.SleeveManufacturer
import cz.nicolsburg.boardflow.model.SleeveTrackingState
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.GameBackdrop
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers
import cz.nicolsburg.boardflow.ui.history.ContextualInsightStrip
import cz.nicolsburg.boardflow.ui.history.GameHistoryStats
import cz.nicolsburg.boardflow.ui.history.gameContextualInsight
import cz.nicolsburg.boardflow.ui.history.gameHistoryStats
import cz.nicolsburg.boardflow.ui.history.resolveCurrentPlayerName

private data class InfoSection(
    val title: String,
    val stats: List<SectionStat>,
    val secondaryLabels: Set<String> = emptySet(),
    val columns: Int = 2
)

private object GameDetailTokens {
    val CardPadding = 14.dp
    val CardCorner = RoundedCornerShape(16.dp)
    const val CardBorderAlpha = 0.12f
    const val NeutralCardAlpha = 0.04f
    const val FeaturedCardAlpha = 0.045f
}

@Composable
fun GameDetailsDialog(
    game: GameItem,
    onDismiss: () -> Unit,
    historyPlays: List<LoggedPlay> = emptyList(),
    players: List<Player> = emptyList(),
    personalRating: Int? = null,
    onRateGame: (rating: Int) -> Unit = {},
    onClearRating: () -> Unit = {},
    onLogPlay: () -> Unit = {},
    onViewHistory: (Int) -> Unit = {},
    onViewHistoryPlayer: (gameId: Int, playerName: String) -> Unit = { _, _ -> },
    onViewPlayers: (playerName: String) -> Unit = {},
    canEditSleeveTracking: Boolean = false,
    onOpenSleeveTrackingActions: (GameItem) -> Unit = {},
    onNavigateToSleeve: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val securePreferences = remember(context) { SecurePreferences(context.applicationContext) }
    val preferredManufacturer = remember(securePreferences) {
        try { SleeveManufacturer.valueOf(securePreferences.sleevePreferredManufacturer) }
        catch (_: Exception) { SleeveManufacturer.AUTO }
    }
    val bggUrl = bggSleevesUrl(game)
    val driveUrl = game.shareUrl?.takeIf { it.isNotBlank() }
    val hasExternalButtons = bggUrl != null || driveUrl != null
    val gameObjectId = remember(game) { game.objectId.toIntOrNull()?.takeIf { it > 0 } }
    val overviewStats = remember(game) { overviewStats(game) }
    val ratingStats = remember(game) { ratingStats(game) }
    val playerStats = remember(game) { playerPreferenceStats(game) }
    val customRows = remember(game) { customDetailRows(game) }
    val myStats = remember(game, historyPlays, players) {
        if (gameObjectId != null) historyPlays.gameHistoryStats(gameObjectId, players)
        else historyPlays.gameHistoryStats(game.identity.name, players)
    }
    val contextualInsight = remember(game, historyPlays, players) {
        gameObjectId?.let {
            val currentPlayerName = resolveCurrentPlayerName(securePreferences.bggUsername, players)
            historyPlays.gameContextualInsight(it, players, currentPlayerName = currentPlayerName, prefs = securePreferences)
        }
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val headerChips = remember(game, primaryColor, secondaryColor) {
        headerStatusChips(game, primaryColor, secondaryColor)
    }
    val compactChips = headerChips.size > 2 || LocalConfiguration.current.screenWidthDp < 380
    val infoSections = remember(overviewStats, ratingStats, game) {
        buildList {
            if (overviewStats.isNotEmpty()) {
                add(InfoSection("Overview", overviewStats.map { stat ->
                    when (stat.label) {
                        "Difficulty" -> stat.copy(icon = game.weight?.let { gameWeightIcon(it) })
                        "Play time"  -> stat.copy(icon = gamePlayTimeIcon(game))
                        else         -> stat
                    }
                }))
            }
            if (ratingStats.isNotEmpty()) {
                add(InfoSection("Ratings", ratingStats.map { stat ->
                    when (stat.label) {
                        "BGG rating"   -> stat.copy(icon = game.rating?.let { ratingIcon(it) })
                        "Bayes rating" -> stat.copy(icon = game.bayesAverage?.let { ratingIcon(it) })
                        else           -> stat
                    }
                }, setOf("Rank")))
            }
        }
    }
    val hasPlayerSection = playerStats.isNotEmpty() || playerLabel(game) != null
        || !game.bestPlayers.isNullOrBlank() || !game.recommendedPlayers.isNullOrBlank()
        || !game.notRecommendedPlayers.isNullOrBlank()
    val hasSleeves = game.sleeveStatus != GameItem.SleeveStatus.UNKNOWN || game.sleeveCardSets.isNotEmpty()
    val listState = rememberLazyListState()
    val headerCollapse by remember {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / 280f).coerceIn(0f, 1f)
            }
        }
    }
    val compactHeaderAlpha by remember {
        derivedStateOf { ((headerCollapse - 0.12f) / 0.88f).coerceIn(0f, 1f) }
    }

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        backdrop = {
            GameBackdrop(
                imageUrl = game.thumbnailUrl,
                height = 200.dp,
                titleFadeAlpha = 0.34f,
                contentFadeAlpha = 0.82f,
                bottomSurfaceBlendStart = 0.78f,
                collapseFraction = headerCollapse
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    HeaderSection(
                        game = game,
                        headerChips = headerChips,
                        compactChips = compactChips,
                        collapse = headerCollapse,
                        gameObjectId = gameObjectId,
                        hasHistory = myStats != null,
                        onLogPlay = onLogPlay,
                        onViewHistory = onViewHistory
                    )
                }

                if (myStats != null && contextualInsight != null) {
                    item {
                        ContextualInsightStrip(
                            insight = contextualInsight,
                            ambient = true,
                            modifier = Modifier.alpha(1f - 0.22f * headerCollapse)
                        )
                    }
                }

                if (myStats != null) {
                    item {
                        YourStatsCard(
                            stats = myStats,
                            onViewHistoryPlayer = { playerName ->
                                gameObjectId?.let { onViewHistoryPlayer(it, playerName) }
                            },
                            onViewPlayers = onViewPlayers
                        )
                    }
                }

                if (gameObjectId != null) {
                    item {
                        PersonalRatingRow(
                            rating = personalRating,
                            onRateGame = onRateGame,
                            onClearRating = onClearRating
                        )
                    }
                }

                if (hasPlayerSection) {
                    item { PlayerPreferenceBlock(game) }
                }

                if (infoSections.isNotEmpty()) {
                    item { InfoGroupBlock(infoSections) }
                }

                if (hasSleeves) {
                    item {
                        SleevesBlock(
                            game = game,
                            preferredManufacturer = preferredManufacturer,
                            canEditSleeveTracking = canEditSleeveTracking,
                            onOpenSleeveTrackingActions = onOpenSleeveTrackingActions,
                            onNavigateToSleeve = onNavigateToSleeve
                        )
                    }
                }

                if (customRows.isNotEmpty()) {
                    item { InfoGroupBlock(listOf(InfoSection("More", customRows))) }
                }

                if (bggUrl != null || driveUrl != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (bggUrl != null) {
                                DialogUtilityActionButton(
                                    onClick = { open(bggUrl) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp).alpha(0.65f))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Open BGG")
                                }
                            }
                            if (driveUrl != null) {
                                DialogUtilityActionButton(
                                    onClick = { open(driveUrl) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp).alpha(0.65f))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Drive")
                                }
                            }
                        }
                    }
                }
            }

            CompactStickyHeader(
                game = game,
                alpha = compactHeaderAlpha,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HeaderSection(
    game: GameItem,
    headerChips: List<HeaderChip>,
    compactChips: Boolean,
    collapse: Float = 0f,
    gameObjectId: Int? = null,
    hasHistory: Boolean = false,
    onLogPlay: () -> Unit = {},
    onViewHistory: (Int) -> Unit = {}
) {
    val hasBackdrop = !game.thumbnailUrl.isNullOrBlank()
    val thumbSize = 80.dp
    val thumbScale = 1f - 0.08f * collapse
    val thumbAlpha = (1f - 0.15f * collapse).coerceAtLeast(0.85f)
    val chipAlpha = (1f - collapse * 0.28f).coerceAtLeast(0.72f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (hasBackdrop) {
            AsyncImage(
                model = game.thumbnailUrl,
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(thumbSize)
                    .clip(MaterialTheme.shapes.medium)
                    .scale(thumbScale)
                    .alpha(thumbAlpha)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .scale(thumbScale)
                    .alpha(thumbAlpha)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .offset(y = (-4).dp * collapse)
                .alpha((1f - 0.06f * collapse).coerceAtLeast(0.94f)),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (hasBackdrop) Color.White.copy(alpha = 0.95f)
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 20.dp)
            )
            Row(
                modifier = Modifier.alpha(chipAlpha),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                game.rating?.let {
                    InlineStat(
                        icon = Icons.Default.Star,
                        label = formatDecimal(it),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                    )
                }
                headerChips.forEach { chip ->
                    StatusChip(
                        label = chip.label,
                        icon = chip.icon,
                        tint = chip.tint,
                        iconOnly = compactChips
                    )
                }
            }
            if (gameObjectId != null) {
                Row(
                    modifier = Modifier
                        .alpha(chipAlpha)
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogPrimaryActionButton(onClick = onLogPlay) {
                        Icon(
                            Icons.Default.Casino,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).alpha(0.70f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Log Play", style = MaterialTheme.typography.labelMedium)
                    }
                    if (hasHistory) {
                        DialogSecondaryActionButton(onClick = { onViewHistory(gameObjectId) }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).alpha(0.70f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("History", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// Your Stats card

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YourStatsCard(
    stats: GameHistoryStats,
    onViewHistoryPlayer: (playerName: String) -> Unit = {},
    onViewPlayers: (playerName: String) -> Unit = {}
) {
    val primary          = MaterialTheme.colorScheme.primary
    val onSurface        = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant   = MaterialTheme.colorScheme.outlineVariant

    Surface(
        color = primary.copy(alpha = 0.08f),
        shape = GameDetailTokens.CardCorner,
        border = BorderStroke(1.dp, outlineVariant.copy(alpha = 0.20f)),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = GameDetailTokens.CardPadding, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.QueryStats,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = primary.copy(alpha = 0.72f)
                )
                Text(
                    text = "Your Stats",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primary.copy(alpha = 0.72f)
                )
            }

            // Row 1: Total plays | Last played | Avg time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "${stats.plays}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = primary
                    )
                    Text(
                        text = "plays",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    masteryLabel(stats.plays)?.let { label ->
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.10f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = primary.copy(alpha = 0.78f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                stats.lastPlayedDate?.let { date ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Last played",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant.copy(alpha = 0.55f)
                        )
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = onSurface.copy(alpha = 0.84f)
                        )
                    }
                }
                stats.avgDurationMinutes?.let { avg ->
                    val durationText = if (avg >= 60) "${avg / 60}h ${avg % 60}m" else "${avg}m"
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Avg time",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant.copy(alpha = 0.55f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = onSurface.copy(alpha = 0.045f),
                            border = BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.14f))
                        ) {
                            Text(
                                text = durationText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurface.copy(alpha = 0.82f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Row 2: Best score | Most wins
            if (stats.bestScore != null || stats.mostWins != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    stats.bestScore?.let { (name, score) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Best score",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(alpha = 0.55f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = primary.copy(alpha = 0.10f),
                                    border = BorderStroke(0.5.dp, primary.copy(alpha = 0.14f)),
                                    modifier = Modifier.clip(CircleShape).clickable { onViewHistoryPlayer(name) }
                                ) {
                                    Text(
                                        text = "$score",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = "by",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceVariant.copy(alpha = 0.50f)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = onSurface.copy(alpha = 0.045f),
                                    border = BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.14f)),
                                    modifier = Modifier.clip(CircleShape).clickable { onViewPlayers(name) }
                                ) {
                                    Text(
                                        text = compactPartnerName(name),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurface.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    stats.mostWins?.let { (name, wins) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Most wins",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(alpha = 0.55f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = onSurface.copy(alpha = 0.045f),
                                    border = BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.14f)),
                                    modifier = Modifier.clip(CircleShape).clickable { onViewHistoryPlayer(name) }
                                ) {
                                    Text(
                                        text = "$wins",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = onSurface.copy(alpha = 0.82f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = "wins",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurfaceVariant.copy(alpha = 0.50f)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = onSurface.copy(alpha = 0.045f),
                                    border = BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.14f)),
                                    modifier = Modifier.clip(CircleShape).clickable { onViewPlayers(name) }
                                ) {
                                    Text(
                                        text = compactPartnerName(name),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurface.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Frequent partners – pill chips
            if (stats.commonPlayers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Frequent partners",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stats.commonPlayers.forEach { (name, count) ->
                            Surface(
                                shape = CircleShape,
                                color = onSurface.copy(alpha = 0.045f),
                                border = BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.14f)),
                                modifier = Modifier.clip(CircleShape).clickable { onViewPlayers(name) }
                            ) {
                                Text(
                                    text = "${compactPartnerName(name)}  $count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurface.copy(alpha = 0.82f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Grouped info block

@Composable
private fun InfoGroupBlock(sections: List<InfoSection>) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        shape = GameDetailTokens.CardCorner,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            sections.forEachIndexed { i, section ->
                if (i > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                        modifier = Modifier.padding(horizontal = GameDetailTokens.CardPadding)
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = GameDetailTokens.CardPadding, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                    DetailGrid(
                        stats = section.stats,
                        emphasizeSurface = false,
                        secondaryLabels = section.secondaryLabels,
                        columns = section.columns
                    )
                }
            }
        }
    }
}

// Sleeves block

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SleevesBlock(
    game: GameItem,
    preferredManufacturer: SleeveManufacturer = SleeveManufacturer.AUTO,
    canEditSleeveTracking: Boolean = false,
    onOpenSleeveTrackingActions: (GameItem) -> Unit = {},
    onNavigateToSleeve: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val trackingStatus = sheetSleeveStatus(game)

    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f),
        shape = GameDetailTokens.CardCorner,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { onOpenSleeveTrackingActions(game) }
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GameDetailTokens.CardPadding, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Style,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        Text(
                            text = "Sleeves",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                    if (!expanded) {
                        val groups = game.sleeveCardSets
                            .filter { it.size != null || it.count != null }
                            .groupBy { it.size?.trim().orEmpty() }
                        val totalCards = game.sleeveCardSets.mapNotNull { it.count }.sum()
                        when {
                            groups.isNotEmpty() && totalCards > 0 -> Text(
                                text = "${groups.size} ${if (groups.size == 1) "size" else "sizes"} · $totalCards cards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            game.sleeveStatus == GameItem.SleeveStatus.MISSING -> Text(
                                text = "No data on BGG yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }

                    AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                        modifier = Modifier.padding(horizontal = GameDetailTokens.CardPadding)
                    )
                    Box(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .padding(horizontal = GameDetailTokens.CardPadding, vertical = 9.dp)
                    ) {
                        SleevesSection(
                            game = game,
                            preferredManufacturer = preferredManufacturer,
                            trackingStatus = trackingStatus,
                            canEditSleeveTracking = canEditSleeveTracking,
                            onNavigateToSleeve = onNavigateToSleeve
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SleeveTrackingActionSheetContent(
    game: GameItem,
    trackingStatus: SleeveTrackingState,
    canEdit: Boolean,
    onSelectStatus: (SleeveTrackingState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (canEdit) {
                    "Sleeve tracking: ${sleeveTrackingLabel(trackingStatus)}"
                } else {
                    "Connect Google Sheets to edit sleeve tracking."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Column {
                SleeveTrackingActionRow(
                    icon = Icons.Default.Check,
                    label = "Mark as sleeved",
                    selected = trackingStatus == SleeveTrackingState.SLEEVED,
                    enabled = canEdit,
                    onClick = { onSelectStatus(SleeveTrackingState.SLEEVED) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 62.dp))
                SleeveTrackingActionRow(
                    icon = Icons.Default.WarningAmber,
                    label = "Mark to sleeve",
                    selected = trackingStatus == SleeveTrackingState.TO_SLEEVE,
                    enabled = canEdit,
                    onClick = { onSelectStatus(SleeveTrackingState.TO_SLEEVE) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 62.dp))
                SleeveTrackingActionRow(
                    icon = Icons.Default.Style,
                    label = "Mark as possible to sleeve",
                    selected = trackingStatus == SleeveTrackingState.POSSIBLE,
                    enabled = canEdit,
                    onClick = { onSelectStatus(SleeveTrackingState.POSSIBLE) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 62.dp))
                SleeveTrackingActionRow(
                    icon = Icons.Default.Close,
                    label = "Mark as not sleeving",
                    selected = trackingStatus == SleeveTrackingState.NOT_SLEEVING,
                    enabled = canEdit,
                    onClick = { onSelectStatus(SleeveTrackingState.NOT_SLEEVING) }
                )
            }
        }
    }
}

@Composable
private fun SleeveTrackingActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (selected) {
                Text(
                    text = "Current",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Status chip

@Composable
private fun StatusChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    iconOnly: Boolean = false
) {
    Surface(color = tint.copy(alpha = 0.05f), shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = tint.copy(alpha = 0.84f)
            )
            if (!iconOnly) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.82f)
                )
            }
        }
    }
}

// Detail grid

@Composable
private fun DetailGrid(
    stats: List<SectionStat>,
    emphasizeSurface: Boolean = true,
    secondaryLabels: Set<String> = emptySet(),
    columns: Int = 2
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { stat ->
                    DetailCell(
                        label = stat.label,
                        value = stat.value,
                        icon = stat.icon,
                        emphasizeSurface = emphasizeSurface,
                        secondary = stat.label in secondaryLabels,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DetailCell(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emphasizeSurface: Boolean,
    secondary: Boolean,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (emphasizeSurface) 10.dp else 2.dp,
                    vertical = if (emphasizeSurface) 9.dp else 3.dp
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (secondary) 0.5f else 0.58f
                )
            )
            if (icon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    )
                    Text(
                        text = value,
                        style = if (secondary) {
                            MaterialTheme.typography.labelMedium.withTabularNumbers()
                        } else {
                            MaterialTheme.typography.bodyMedium.withTabularNumbers()
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (secondary) 0.76f else 0.84f
                        )
                    )
                }
            } else {
                Text(
                    text = value,
                    style = if (secondary) {
                        MaterialTheme.typography.labelMedium.withTabularNumbers()
                    } else {
                        MaterialTheme.typography.bodyMedium.withTabularNumbers()
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (secondary) 0.76f else 0.84f
                    )
                )
            }
        }
    }

    if (emphasizeSurface) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.medium
        ) { content() }
    } else {
        Box(modifier = modifier) { content() }
    }
}

// Sleeves expanded content

@Composable
private fun SleevesSection(
    game: GameItem,
    preferredManufacturer: SleeveManufacturer = SleeveManufacturer.AUTO,
    trackingStatus: SleeveTrackingState = SleeveTrackingState.UNKNOWN,
    canEditSleeveTracking: Boolean = false,
    onNavigateToSleeve: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val grouped = remember(game) {
        game.sleeveCardSets
            .filter { it.size != null || it.count != null }
            .groupBy { it.size?.trim().orEmpty() }
            .entries
            .sortedWith(compareBy({ SleeveDatabase.findBySize(it.key) == null }, { it.key }))
    }

    when {
        game.sleeveStatus == GameItem.SleeveStatus.MISSING ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "No sleeve data on BGG yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (canEditSleeveTracking) {
                    Text(
                        text = "Long press to update the sleeve tracking state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    )
                }
            }

        game.sleeveStatus == GameItem.SleeveStatus.ERROR ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = game.sleeveNote ?: "Could not load sleeve data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
                if (canEditSleeveTracking) {
                    Text(
                        text = "Long press to update the sleeve tracking state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    )
                }
            }

        grouped.isEmpty() -> Unit

        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEachIndexed { index, (size, sets) ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.06f)
                    )
                }
                val total = sets.mapNotNull { it.count }.sum()
                val sleeveEntry = SleeveDatabase.findBySize(size)
                val genericName = sleeveEntry?.genericName
                val preferred = sleeveEntry?.preferredFor(preferredManufacturer)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = genericName ?: size.ifBlank { "Unknown size" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onNavigateToSleeve(genericName ?: size.ifBlank { "Unknown size" }) }
                        )
                        if (genericName != null && size.isNotBlank()) {
                            Text(
                                text = size,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                                maxLines = 1
                            )
                            if (preferred != null) {
                                Text(
                                    text = compactManufacturerLine(preferred.first, preferred.second),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        val query = Uri.encode("${preferred.first} ${preferred.second}")
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                                        )
                                    }
                                )
                            }
                        }
                    }
                    if (total > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Box(
                                modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$total",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun compactManufacturerLine(brand: String, product: String): String {
    val shortBrand = brand.split(" ").first()
    val sizePattern = Regex("""\s*\d+\.?\d*\s*[×xX]\s*\d+\.?\d*\s*$""")
    val productWithoutBrand = when {
        product.startsWith(brand)      -> product.removePrefix(brand).trim()
        product.startsWith(shortBrand) -> product.removePrefix(shortBrand).trim()
        else                           -> product
    }
    val cleanProduct = productWithoutBrand.replace(sizePattern, "").trim()
    return if (cleanProduct.isNotBlank()) "$shortBrand · $cleanProduct" else shortBrand
}

private fun compactPartnerName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.length <= 16) return trimmed
    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.size >= 2) {
        val shortened = "${parts.first()} ${parts.last().first()}."
        if (shortened.length <= 16) return shortened
        return parts.first()
    }
    return trimmed.take(15) + "..."
}

private fun parsePlayerRange(s: String): List<Int> {
    val trimmed = s.trim()
    val rangeMatch = Regex("""(\d+)\s*[-–]\s*(\d+)""").find(trimmed)
    if (rangeMatch != null) {
        val from = rangeMatch.groupValues[1].toIntOrNull() ?: return emptyList()
        val to   = rangeMatch.groupValues[2].toIntOrNull() ?: return emptyList()
        return if (to - from <= 8) (from..to).toList() else emptyList()
    }
    if (trimmed.contains(",")) {
        return trimmed.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
    return trimmed.toIntOrNull()?.let { listOf(it) } ?: emptyList()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerCountBubbles(value: String, tint: Color) {
    val numbers = remember(value) { parsePlayerRange(value) }
    if (numbers.isEmpty()) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f)
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        numbers.forEach { n ->
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.13f)) {
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$n",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerPreferenceBlock(game: GameItem) {
    val primary          = MaterialTheme.colorScheme.primary
    val error            = MaterialTheme.colorScheme.error
    val onSurface        = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant   = MaterialTheme.colorScheme.outlineVariant

    val bestFor    = game.bestPlayers?.takeIf { it.isNotBlank() }
    val recFor     = game.recommendedPlayers?.takeIf { it.isNotBlank() }
    val notRecFor  = game.notRecommendedPlayers?.takeIf { it.isNotBlank() }
    val officialRange = playerLabel(game)
    val hasCommunityData = bestFor != null || recFor != null || notRecFor != null

    Surface(
        color = onSurface.copy(alpha = 0.03f),
        shape = GameDetailTokens.CardCorner,
        border = BorderStroke(0.5.dp, outlineVariant.copy(alpha = 0.1f)),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = GameDetailTokens.CardPadding, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Header row: "Players" label + official range right-aligned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Players",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceVariant.copy(alpha = 0.65f)
                )
                if (officialRange != null) {
                    Text(
                        text = officialRange,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = 0.50f)
                    )
                }
            }

            // Community poll columns — only rendered when any poll data exists
            if (hasCommunityData) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (bestFor != null) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Best for",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(alpha = 0.58f)
                            )
                            PlayerCountBubbles(value = bestFor, tint = primary)
                        }
                    }
                    if (recFor != null) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Great with",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant.copy(alpha = 0.58f)
                            )
                            PlayerCountBubbles(value = recFor, tint = onSurface.copy(alpha = 0.60f))
                        }
                    }
                    if (notRecFor != null) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Avoid",
                                style = MaterialTheme.typography.labelSmall,
                                color = error.copy(alpha = 0.58f)
                            )
                            PlayerCountBubbles(value = notRecFor, tint = error.copy(alpha = 0.70f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStickyHeader(
    game: GameItem,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    if (alpha <= 0f) return
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f * alpha),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f * alpha)),
        modifier = modifier.alpha(alpha)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!game.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = game.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            }
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun masteryLabel(plays: Int): String? = when {
    plays <= 0  -> null
    plays <= 4  -> "Learning"
    plays <= 14 -> "Familiar"
    plays <= 29 -> "Comfortable"
    plays <= 49 -> "Practiced"
    plays <= 99 -> "Deep"
    else        -> "Mastered"
}

private fun polishGameDetailStat(stat: SectionStat): SectionStat = stat

@Composable
private fun DialogPrimaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun DialogSecondaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun DialogUtilityActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun PersonalRatingRow(
    rating: Int?,
    onRateGame: (Int) -> Unit,
    onClearRating: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        shape = GameDetailTokens.CardCorner,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = GameDetailTokens.CardBorderAlpha)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(horizontal = GameDetailTokens.CardPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (rating != null) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (rating != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "Your rating",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (rating != null) "$rating / 10" else "Tap to rate",
                style = MaterialTheme.typography.bodyMedium,
                color = if (rating != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Rate this game") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (1..10).forEach { n ->
                            val selected = rating != null && n <= rating
                            Icon(
                                imageVector = if (selected) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$n",
                                modifier = Modifier
                                    .weight(1f)
                                    .size(28.dp)
                                    .clickable { onRateGame(n); showPicker = false },
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                    if (rating != null) {
                        Text(
                            "Current: $rating / 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rating != null) {
                        TextButton(onClick = { onClearRating(); showPicker = false }) {
                            Text("Clear")
                        }
                    }
                    TextButton(onClick = { showPicker = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

