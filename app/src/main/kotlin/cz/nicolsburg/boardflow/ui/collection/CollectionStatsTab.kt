package cz.nicolsburg.boardflow.ui.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.SleeveTrackingState
import cz.nicolsburg.boardflow.ui.common.BoardFlowIconButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowInlineAction
import cz.nicolsburg.boardflow.ui.common.BoardFlowModalBottomSheet
import cz.nicolsburg.boardflow.ui.common.BoardFlowSurfaceTokens
import cz.nicolsburg.boardflow.ui.common.SectionCard
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers

// ── Data ──────────────────────────────────────────────────────────────────────

private data class CollectionStats(
    val totalOwned: Int,
    val wishlistCount: Int,
    val unplayedCount: Int,
    val avgRating: Double?,
    val totalBggPlays: Int,
    val playDepth: List<Pair<String, Int>>,
    val weightTiers: List<Pair<String, Int>>,
    val sleeved: Int,
    val toSleeve: Int,
    val notTracked: Int,
    val topPlayed: List<GameItem>,
    val neverPlayedGames: List<GameItem>,
)

private fun computeStats(games: List<GameItem>): CollectionStats {
    val owned = games.filter { it.isOwned }

    val unplayed = owned.filter { (it.numPlays ?: 0) == 0 }
    val ratingsWithValue = owned.mapNotNull { it.rating }.filter { it > 0 }
    val avgRating = if (ratingsWithValue.isNotEmpty()) ratingsWithValue.average() else null
    val totalBggPlays = owned.sumOf { it.numPlays ?: 0 }

    val playDepth = listOf(
        "Unplayed" to owned.count { (it.numPlays ?: 0) == 0 },
        "Tried  (1-4)" to owned.count { (it.numPlays ?: 0) in 1..4 },
        "Familiar  (5-14)" to owned.count { (it.numPlays ?: 0) in 5..14 },
        "Deep  (15+)" to owned.count { (it.numPlays ?: 0) >= 15 },
    ).filter { it.second > 0 }

    val weightOrder = listOf("Light", "Casual", "Medium-Light", "Medium", "Medium-Heavy", "Heavy", "Expert")
    val weightTiers = owned.mapNotNull { it.weight?.let { w -> gameWeightLabel(w) } }
        .groupingBy { it }.eachCount()
        .entries.sortedBy { weightOrder.indexOf(it.key).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        .map { it.key to it.value }

    val sleeveCounts = owned.groupBy { sheetSleeveStatus(it) }
    val sleeved = sleeveCounts[SleeveTrackingState.SLEEVED]?.size ?: 0
    val toSleeve = (sleeveCounts[SleeveTrackingState.TO_SLEEVE]?.size ?: 0) +
            (sleeveCounts[SleeveTrackingState.POSSIBLE]?.size ?: 0)
    val notTracked = (sleeveCounts[SleeveTrackingState.NOT_SLEEVING]?.size ?: 0) +
            (sleeveCounts[SleeveTrackingState.UNKNOWN]?.size ?: 0)

    val topPlayed = owned.filter { (it.numPlays ?: 0) > 0 }
        .sortedByDescending { it.numPlays ?: 0 }
        .take(5)

    return CollectionStats(
        totalOwned = owned.size,
        wishlistCount = games.count { it.isWishlisted },
        unplayedCount = unplayed.size,
        avgRating = avgRating,
        totalBggPlays = totalBggPlays,
        playDepth = playDepth,
        weightTiers = weightTiers,
        sleeved = sleeved,
        toSleeve = toSleeve,
        notTracked = notTracked,
        topPlayed = topPlayed,
        neverPlayedGames = unplayed.sortedBy { it.name.lowercase() },
    )
}

// ── Root composable ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionStatsTab(
    games: List<GameItem>,
    onMarkAsPlayed: (gameId: Int, gameName: String) -> Unit = { _, _ -> },
) {
    val stats = remember(games) { computeStats(games) }

    if (stats.totalOwned == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No owned games loaded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OverviewCard(stats) }
        if (stats.playDepth.isNotEmpty()) {
            item { PlayDepthCard(stats) }
        }
        if (stats.weightTiers.isNotEmpty()) {
            item { ComplexityCard(stats) }
        }
        if (stats.sleeved + stats.toSleeve + stats.notTracked > 0) {
            item { SleeveCard(stats) }
        }
        if (stats.topPlayed.isNotEmpty()) {
            item { TopPlayedCard(stats) }
        }
        if (stats.neverPlayedGames.isNotEmpty()) {
            item { UnplayedShelfCard(stats, onMarkAsPlayed) }
        }
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewCard(stats: CollectionStats) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BigStat(
                value = stats.totalOwned.toString(),
                label = "Owned",
                icon = { Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
            )
            BigStat(
                value = stats.wishlistCount.toString(),
                label = "Wishlist",
                icon = { Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary) },
            )
            BigStat(
                value = stats.unplayedCount.toString(),
                label = "Unplayed",
            )
        }

        if (stats.avgRating != null || stats.totalBggPlays > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stats.avgRating?.let { rating ->
                    InlineStat(
                        icon = Icons.Default.Star,
                        label = "Avg rating  ${formatDecimal(rating)}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (stats.totalBggPlays > 0) {
                    Text(
                        "${stats.totalBggPlays} total BGG plays",
                        style = MaterialTheme.typography.labelMedium.withTabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayDepthCard(stats: CollectionStats) {
    val max = stats.playDepth.maxOf { it.second }
    SectionCard {
        CardTitle("Play Depth")
        stats.playDepth.forEachIndexed { i, (label, count) ->
            if (i > 0) Spacer(Modifier.height(6.dp))
            StatBarRow(
                label = label,
                count = count,
                total = stats.totalOwned,
                fraction = if (max > 0) count.toFloat() / max else 0f,
                barColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ComplexityCard(stats: CollectionStats) {
    val max = stats.weightTiers.maxOf { it.second }
    SectionCard {
        CardTitle("Complexity")
        stats.weightTiers.forEachIndexed { i, (label, count) ->
            if (i > 0) Spacer(Modifier.height(6.dp))
            StatBarRow(
                label = label,
                count = count,
                total = stats.totalOwned,
                fraction = if (max > 0) count.toFloat() / max else 0f,
                barColor = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun SleeveCard(stats: CollectionStats) {
    SectionCard {
        CardTitle("Sleeve Coverage")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BigStat(stats.sleeved.toString(), "Sleeved")
            BigStat(stats.toSleeve.toString(), "To sleeve")
            BigStat(stats.notTracked.toString(), "Not tracking")
        }
        val trackedTotal = stats.sleeved + stats.toSleeve + stats.notTracked
        if (trackedTotal > 0 && stats.sleeved > 0) {
            val pct = (stats.sleeved * 100 / trackedTotal)
            Text(
                "$pct% of collection sleeved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopPlayedCard(stats: CollectionStats) {
    SectionCard {
        CardTitle("Most Played")
        stats.topPlayed.forEachIndexed { i, game ->
            if (i > 0) HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(16.dp),
                )
                Text(
                    game.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "${game.numPlays}x",
                    style = MaterialTheme.typography.labelMedium.withTabularNumbers(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun UnplayedShelfCard(
    stats: CollectionStats,
    onMarkAsPlayed: (gameId: Int, gameName: String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuGame by remember { mutableStateOf<GameItem?>(null) }

    menuGame?.let { game ->
        BoardFlowModalBottomSheet(
            onDismissRequest = { menuGame = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    game.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                BoardFlowInlineAction(
                    onClick = {
                        menuGame = null
                        game.objectId.toIntOrNull()?.let { id ->
                            onMarkAsPlayed(id, game.name)
                        }
                    },
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mark as played")
                }
            }
        }
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CardTitle("Unplayed Shelf")
                Text(
                    "${stats.neverPlayedGames.size} game${if (stats.neverPlayedGames.size == 1) "" else "s"} never played",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BoardFlowIconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(4.dp))
                stats.neverPlayedGames.forEach { game ->
                    Text(
                        game.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { menuGame = game },
                            )
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ── Shared primitives ─────────────────────────────────────────────────────────

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BigStat(
    value: String,
    label: String,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                icon()
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall.withTabularNumbers(),
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.withTabularNumbers(),
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatBarRow(
    label: String,
    count: Int,
    total: Int,
    fraction: Float,
    barColor: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$count  ·  ${if (total > 0) count * 100 / total else 0}%",
                style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor.copy(alpha = 0.75f)),
            )
        }
    }
}
