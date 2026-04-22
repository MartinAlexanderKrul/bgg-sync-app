package cz.nicolsburg.boardflow.ui.collection

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowOutlinedButton
import cz.nicolsburg.boardflow.ui.common.CornerCloseStrip
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers

@Composable
fun GameDetailsDialog(
    game: GameItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bggUrl = bggSleevesUrl(game)
    val driveUrl = game.shareUrl?.takeIf { it.isNotBlank() }

    val overviewStats = remember(game) { overviewStats(game) }
    val ratingStats = remember(game) { ratingStats(game) }
    val playerPreferenceStats = remember(game) { playerPreferenceStats(game) }
    val customRows = remember(game) { customDetailRows(game) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val headerChips = remember(game, primaryColor, secondaryColor) {
        headerStatusChips(game, primaryColor, secondaryColor)
    }
    val compactHeaderChips = headerChips.size > 2 || LocalConfiguration.current.screenWidthDp < 380

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (!game.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = game.thumbnailUrl,
                                    contentDescription = game.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = game.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 20.dp)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    game.rating?.let {
                                        InlineStat(
                                            icon = Icons.Default.Star,
                                            label = formatDecimal(it),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    headerChips.forEach { chip ->
                                        StatusChip(
                                            label = chip.label,
                                            icon = chip.icon,
                                            tint = chip.tint,
                                            iconOnly = compactHeaderChips
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (overviewStats.isNotEmpty()) {
                        item {
                            SectionBlock(title = "Overview") {
                                DetailGrid(
                                    stats = overviewStats,
                                    emphasizeSurface = false
                                )
                            }
                        }
                    }

                    if (ratingStats.isNotEmpty()) {
                        item {
                            SectionBlock(title = "Ratings & Stats") {
                                DetailGrid(
                                    stats = ratingStats,
                                    emphasizeSurface = false,
                                    secondaryLabels = setOf("Rank")
                                )
                            }
                        }
                    }

                    if (playerPreferenceStats.isNotEmpty()) {
                        item {
                            SectionBlock(title = "Players") {
                                DetailGrid(
                                    stats = playerPreferenceStats,
                                    emphasizeSurface = false
                                )
                            }
                        }
                    }

                    if (game.sleeveStatus != GameItem.SleeveStatus.UNKNOWN || game.sleeveCardSets.isNotEmpty()) {
                        item {
                            SectionBlock(title = "Sleeves") {
                                SleevesSection(game)
                            }
                        }
                    }

                    if (customRows.isNotEmpty()) {
                        item {
                            SectionBlock(title = "More") {
                                DetailGrid(
                                    stats = customRows,
                                    emphasizeSurface = false
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (bggUrl != null) {
                                BoardFlowButton(
                                    onClick = { open(bggUrl) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("  Open BGG")
                                }
                            }

                            if (driveUrl != null) {
                                BoardFlowOutlinedButton(
                                    onClick = { open(driveUrl) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("  Drive")
                                }
                            }
                        }
                    }
                }
            }

            CornerCloseStrip(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    iconOnly: Boolean = false
) {
    Surface(
        color = tint.copy(alpha = 0.08f),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = tint
            )
            if (!iconOnly) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun DetailGrid(
    stats: List<SectionStat>,
    emphasizeSurface: Boolean = true,
    secondaryLabels: Set<String> = emptySet()
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { stat ->
                    DetailCell(
                        label = stat.label,
                        value = stat.value,
                        emphasizeSurface = emphasizeSurface,
                        secondary = stat.label in secondaryLabels,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailCell(
    label: String,
    value: String,
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
                    vertical = if (emphasizeSurface) 9.dp else 4.dp
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (secondary) 0.55f else 0.8f
                )
            )
            Text(
                text = value,
                style = if (secondary) {
                    MaterialTheme.typography.labelMedium.withTabularNumbers()
                } else {
                    MaterialTheme.typography.bodyMedium.withTabularNumbers()
                },
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (secondary) 0.75f else 1f
                )
            )
        }
    }

    if (emphasizeSurface) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.medium
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
private fun SleevesSection(game: GameItem) {
    val grouped = remember(game) {
        game.sleeveCardSets
            .filter { it.size != null || it.count != null }
            .groupBy { it.size?.trim().orEmpty() }
            .entries
            .sortedBy { it.key }
    }

    when {
        game.sleeveStatus == GameItem.SleeveStatus.MISSING -> {
            Text(
                text = "No sleeve data on BGG yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        game.sleeveStatus == GameItem.SleeveStatus.ERROR -> {
            Text(
                text = game.sleeveNote ?: "Could not load sleeve data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
            )
        }

        grouped.isEmpty() -> Unit

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (size, sets) ->
                    val total = sets.mapNotNull { it.count }.sum()

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = size.ifBlank { "Unknown size" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )

                            if (total > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "$total",
                                        style = MaterialTheme.typography.labelMedium.withTabularNumbers(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}