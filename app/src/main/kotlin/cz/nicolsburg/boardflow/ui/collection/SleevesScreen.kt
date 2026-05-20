package cz.nicolsburg.boardflow.ui.collection

import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.SleeveTrackingState
import cz.nicolsburg.boardflow.model.SleeveDatabase
import cz.nicolsburg.boardflow.model.SleeveEntry
import cz.nicolsburg.boardflow.ui.common.BoardFlowIcons
import cz.nicolsburg.boardflow.ui.common.BoardFlowAnimatedVisibility
import cz.nicolsburg.boardflow.ui.common.SectionCard
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers

private data class GameSleevesEntry(val name: String, val count: Int, val exactSize: String)

private data class SleeveSizeGroup(
    val displayName: String,
    val sleeveEntry: SleeveEntry?,
    val totalCount: Int,
    val games: List<GameSleevesEntry>,
    val isUnknown: Boolean = false
)

private fun computeSleeveSummary(
    games: List<GameItem>,
    excludedGameIds: Set<String>,
    showAll: Boolean = false
): List<SleeveSizeGroup> {
    val toSleeve = games.filter { game ->
        game.isOwned &&
            (showAll || sheetSleeveStatus(game) == SleeveTrackingState.TO_SLEEVE) &&
            game.objectId !in excludedGameIds
    }

    // groupKey -> Triple(totalCount, sleeveEntry, Map<gameName, Pair<count, exactSize>>)
    val sizeGroups = LinkedHashMap<String, Triple<Int, SleeveEntry?, LinkedHashMap<String, Pair<Int, String>>>>()
    val noDataGames = LinkedHashSet<String>()

    for (game in toSleeve) {
        val cardSets = game.sleeveCardSets
        if (cardSets.isEmpty()) {
            noDataGames += game.name
            continue
        }

        // Aggregate per generic sleeve name for this game, preserving the raw size
        val gameGroups = mutableMapOf<String, Pair<Int, String>>() // groupKey -> (count, firstRawSize)
        for (cs in cardSets) {
            val rawSize = cs.size?.takeIf { it.isNotBlank() } ?: continue
            val entry = SleeveDatabase.findBySize(rawSize)
            val groupKey = entry?.genericName ?: rawSize
            val existing = gameGroups[groupKey]
            gameGroups[groupKey] = Pair(
                (existing?.first ?: 0) + (cs.count ?: 0),
                existing?.second ?: rawSize
            )
        }

        if (gameGroups.isEmpty()) {
            noDataGames += game.name
            continue
        }

        for ((groupKey, gamePair) in gameGroups) {
            val (gameCount, exactSize) = gamePair
            val sleeveEntry = SleeveDatabase.findBySize(exactSize)
            val existing = sizeGroups[groupKey]
            if (existing == null) {
                sizeGroups[groupKey] = Triple(
                    gameCount,
                    sleeveEntry,
                    linkedMapOf(game.name to Pair(gameCount, exactSize))
                )
            } else {
                val updatedGames = existing.third.also {
                    val prev = it[game.name]
                    it[game.name] = Pair((prev?.first ?: 0) + gameCount, exactSize)
                }
                sizeGroups[groupKey] = Triple(
                    existing.first + gameCount,
                    existing.second ?: sleeveEntry,
                    updatedGames
                )
            }
        }
    }

    val knownGroups = sizeGroups.entries
        .map { (displayName, triple) ->
            val (totalCount, sleeveEntry, gameMap) = triple
            SleeveSizeGroup(
                displayName = displayName,
                sleeveEntry = sleeveEntry,
                totalCount = totalCount,
                games = gameMap.map { (name, pair) -> GameSleevesEntry(name, pair.first, pair.second) }
            )
        }
        .sortedByDescending { it.totalCount }

    return if (noDataGames.isNotEmpty()) {
        knownGroups + SleeveSizeGroup(
            displayName = "No size data",
            sleeveEntry = null,
            totalCount = 0,
            games = noDataGames.map { GameSleevesEntry(it, 0, "") },
            isUnknown = true
        )
    } else {
        knownGroups
    }
}

@Composable
internal fun SleevesContent(
    allGames: List<GameItem>,
    listState: LazyListState = rememberLazyListState(),
    excludedGameIds: Set<String> = emptySet(),
    onToggleExclusion: (String) -> Unit = {},
    onExcludeAll: (Set<String>) -> Unit = {},
    onIncludeAll: () -> Unit = {},
    initiallyExpandedGroup: String? = null,
    modifier: Modifier = Modifier
) {
    var showAllGames by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(initiallyExpandedGroup) {
        if (initiallyExpandedGroup != null) {
            expandedGroups = expandedGroups + initiallyExpandedGroup
        }
    }

    val context = LocalContext.current
    val onShare: () -> Unit = remember(allGames, excludedGameIds, showAllGames) {
        {
            val filtered = allGames.filter { game ->
                game.isOwned &&
                    (showAllGames || sheetSleeveStatus(game) == SleeveTrackingState.TO_SLEEVE) &&
                    game.objectId !in excludedGameIds
            }
            val csv = buildSleevesCsv(filtered)
            val intent = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "sleeve-sizes.csv")
                    putExtra(Intent.EXTRA_TEXT, csv)
                },
                null
            )
            context.startActivity(intent)
        }
    }

    val allGamesToSleeve = remember(allGames, showAllGames) {
        allGames
            .filter { game ->
                game.isOwned && (showAllGames || sheetSleeveStatus(game) == SleeveTrackingState.TO_SLEEVE)
            }
            .sortedBy { it.name.lowercase() }
    }

    val groups = remember(allGames, excludedGameIds, showAllGames) {
        computeSleeveSummary(allGames, excludedGameIds, showAll = showAllGames)
    }

    LaunchedEffect(initiallyExpandedGroup, groups) {
        if (initiallyExpandedGroup != null) {
            val idx = groups.indexOfFirst { it.displayName == initiallyExpandedGroup }
            if (idx >= 0) listState.animateScrollToItem(idx + 2)
        }
    }
    val includedCount = remember(allGamesToSleeve, excludedGameIds) {
        allGamesToSleeve.count { it.objectId !in excludedGameIds }
    }

    var showGameSelector by remember { mutableStateOf(false) }

    if (allGamesToSleeve.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    BoardFlowIcons.Sleeves,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
                Text(
                    "All games are sleeved",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header") {
            SleeveSummaryHeader(
                includedCount = includedCount,
                totalCount = allGamesToSleeve.size,
                sizesCount = groups.count { !it.isUnknown },
                expanded = showGameSelector,
                showAllGames = showAllGames,
                onToggleExpand = { showGameSelector = !showGameSelector },
                onSwipeLeft = { onExcludeAll(allGamesToSleeve.map { it.objectId }.toSet()) },
                onSwipeRight = { onIncludeAll() },
                onLongPress = { showAllGames = !showAllGames },
                onShare = onShare
            )
        }

        item(key = "game_selector") {
            BoardFlowAnimatedVisibility(visible = showGameSelector) {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Included in sleeve count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        allGamesToSleeve.forEach { game ->
                            val excluded = game.objectId in excludedGameIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleExclusion(game.objectId) }
                                    .padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    game.name,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 2.dp),
                                    color = if (excluded)
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                CompactSleeveCheckmark(checked = !excluded)
                            }
                        }
                    }
                }
            }
        }

        items(groups, key = { it.displayName }) { group ->
            SleeveSizeGroupCard(
                group = group,
                expanded = group.displayName in expandedGroups,
                onToggleExpand = {
                    expandedGroups = if (group.displayName in expandedGroups)
                        expandedGroups - group.displayName
                    else
                        expandedGroups + group.displayName
                }
            )
        }
    }
}

@Composable
private fun CompactSleeveCheckmark(checked: Boolean) {
    Surface(
        modifier = Modifier.size(22.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SleeveSummaryHeader(
    includedCount: Int,
    totalCount: Int,
    sizesCount: Int,
    expanded: Boolean,
    showAllGames: Boolean,
    onToggleExpand: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onLongPress: () -> Unit,
    onShare: () -> Unit
) {
    SectionCard(
        accented = true,
        modifier = Modifier
            .combinedClickable(
                onClick = onToggleExpand,
                onLongClick = onLongPress
            )
            .pointerInput(onSwipeLeft, onSwipeRight) {
                val threshold = 52.dp.toPx()
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        when {
                            accumulated < -threshold -> onSwipeLeft()
                            accumulated > threshold  -> onSwipeRight()
                        }
                        accumulated = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    BoardFlowIcons.Sleeves,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "$includedCount ${if (includedCount == 1) "game" else "games"} to sleeve",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val subtitle = buildList {
                        if (showAllGames) {
                            add("all owned")
                        } else {
                            if (sizesCount > 0) add("$sizesCount ${if (sizesCount == 1) "size" else "sizes"} needed")
                            if (includedCount < totalCount) add("${totalCount - includedCount} excluded")
                        }
                    }.joinToString("  ·  ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (showAllGames)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Export sleeve data",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand game list",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun sleeveSearchIntent(brand: String, product: String): Intent {
    val query = Uri.encode("$brand $product")
    return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
}

@Composable
private fun SleeveSizeGroupCard(
    group: SleeveSizeGroup,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {}
) {
    val context = LocalContext.current

    SectionCard(accented = false, onClick = onToggleExpand) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    group.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (group.isUnknown) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                group.sleeveEntry?.let {
                    Text(
                        it.recommendedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                Text(
                    "${group.games.size} ${if (group.games.size == 1) "game" else "games"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!group.isUnknown && group.totalCount > 0) {
                    Text(
                        "x${group.totalCount}",
                        style = MaterialTheme.typography.titleLarge.withTabularNumbers(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        BoardFlowAnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                group.games.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "· ${entry.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.exactSize.isNotBlank()) {
                                Text(
                                    entry.exactSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
                                )
                            }
                        }
                        if (!group.isUnknown && entry.count > 0) {
                            Text(
                                "×${entry.count}",
                                style = MaterialTheme.typography.bodySmall.withTabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                group.sleeveEntry?.let { entry ->
                    if (entry.manufacturerOptions.isNotEmpty()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Where to buy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                            entry.manufacturerOptions.forEach { (brand, product) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { context.startActivity(sleeveSearchIntent(brand, product)) },
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                        border = BorderStroke(
                                            0.5.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                                        )
                                    ) {
                                        Text(
                                            brand,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        product,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

private fun csvEscape(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"${value.replace("\"", "\"\"")}\""
    else value

private fun buildSleevesCsv(games: List<GameItem>): String {
    val sb = StringBuilder()
    sb.appendLine("Game,Card Set,Count,Size")
    games
        .filter { it.isOwned && it.sleeveCardSets.isNotEmpty() }
        .sortedBy { it.name }
        .forEach { game ->
            game.sleeveCardSets.forEach { cs ->
                sb.appendLine(
                    "${csvEscape(game.name)}," +
                    "${csvEscape(cs.label)}," +
                    "${cs.count ?: ""}," +
                    csvEscape(cs.size.orEmpty())
                )
            }
        }
    return sb.trimEnd().toString()
}
