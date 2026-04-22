package cz.nicolsburg.boardflow.ui.collection

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import coil.compose.AsyncImage
import cz.nicolsburg.boardflow.SyncViewModel
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowOutlinedButton
import cz.nicolsburg.boardflow.ui.common.GameSearchField
import cz.nicolsburg.boardflow.ui.common.SearchFieldActionButton
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers

private enum class SortMode(val label: String) {
    RATING("Rating"),
    NAME("Name"),
    WEIGHT("Weight"),
    PLAYS("Plays")
}

private enum class TabMode(val label: String) {
    OWNED("Owned"),
    WISHLIST("Wishlist")
}

private data class SectionStat(
    val label: String,
    val value: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionScreen(syncViewModel: SyncViewModel) {
    val account by syncViewModel.account.collectAsState()
    val spreadsheetId by syncViewModel.spreadsheetId.collectAsState()
    val allGames by syncViewModel.collectionGames.collectAsState()
    val loading by syncViewModel.collectionLoading.collectAsState()
    val error by syncViewModel.collectionError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.RATING) }
    var tabMode by remember { mutableStateOf(TabMode.OWNED) }
    var filterPlayers by remember { mutableStateOf<Int?>(null) }
    var filterBestFor by remember { mutableStateOf<Int?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    val listState = rememberLazyListState()
    val hasActiveFilters = tabMode != TabMode.OWNED || filterPlayers != null || filterBestFor != null || sortMode != SortMode.RATING

    LaunchedEffect(account, spreadsheetId) {
        if (allGames.isNotEmpty() || loading) return@LaunchedEffect
        syncViewModel.loadCachedCollection()
    }

    LaunchedEffect(searchQuery, sortMode, tabMode, filterPlayers, filterBestFor) {
        listState.scrollToItem(0)
    }

    val filteredGames = remember(allGames, searchQuery, sortMode, tabMode, filterPlayers, filterBestFor) {
        var result = allGames
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(query) }
        }
        result = when (tabMode) {
            TabMode.OWNED -> result.filter { it.isOwned || !it.isWishlisted }
            TabMode.WISHLIST -> result.filter { it.isWishlisted }
        }
        filterPlayers?.let { players ->
            result = result.filter { (it.minPlayers ?: 1) <= players && (it.maxPlayers ?: 99) >= players }
        }
        filterBestFor?.let { players ->
            result = result.filter { bestForMatches(it, players) }
        }
        when (sortMode) {
            SortMode.NAME -> result.sortedBy { it.name.lowercase() }
            SortMode.RATING -> result.sortedByDescending { it.rating ?: 0.0 }
            SortMode.WEIGHT -> result.sortedByDescending { it.weight ?: 0.0 }
            SortMode.PLAYS -> result.sortedByDescending { it.numPlays ?: 0 }
        }
    }

    selectedGame?.let { game ->
        GameDetailsDialog(
            game = game,
            onDismiss = { selectedGame = null }
        )
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> LoadingState()
                error != null -> ErrorState(error = error!!, onRetry = null)
                allGames.isEmpty() -> EmptyState(
                    accountReady = account != null,
                    spreadsheetReady = spreadsheetId.isNotBlank(),
                    hasCachedSource = spreadsheetId.isNotBlank(),
                    onLoad = null
                )
                else -> {
                    GameSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        trailingAction = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SearchFieldActionButton(onClick = { showFilters = !showFilters }) {
                                    Icon(
                                        if (showFilters) Icons.Default.Tune else Icons.Default.FilterAlt,
                                        contentDescription = if (showFilters) "Hide filters" else "Show filters"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    AnimatedVisibility(visible = showFilters) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Filters",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (hasActiveFilters) {
                                TextButton(
                                    onClick = {
                                        tabMode = TabMode.OWNED
                                        sortMode = SortMode.RATING
                                        filterPlayers = null
                                        filterBestFor = null
                                    }
                                ) {
                                    Text("Reset")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TabMode.entries.forEach { tab ->
                                FilterChip(
                                    selected = tabMode == tab,
                                    onClick = { tabMode = tab },
                                    colors = boardFlowFilterChipColors(),
                                    label = { Text(tab.label) }
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            items(SortMode.entries) { mode ->
                                FilterChip(
                                    selected = sortMode == mode,
                                    onClick = { sortMode = mode },
                                    colors = boardFlowFilterChipColors(),
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = filterPlayers == null,
                                    onClick = { filterPlayers = null },
                                    colors = boardFlowFilterChipColors(),
                                    label = { Text("Any players") }
                                )
                            }
                            items((1..6).toList()) { players ->
                                FilterChip(
                                    selected = filterPlayers == players,
                                    onClick = { filterPlayers = if (filterPlayers == players) null else players },
                                    colors = boardFlowFilterChipColors(),
                                    label = { Text(if (players == 6) "6+" else "$players") }
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = filterBestFor == null,
                                    onClick = { filterBestFor = null },
                                    colors = boardFlowFilterChipColors(),
                                    label = { Text("Any best for") }
                                )
                            }
                            items((1..6).toList()) { players ->
                                FilterChip(
                                    selected = filterBestFor == players,
                                    onClick = { filterBestFor = if (filterBestFor == players) null else players },
                                    colors = boardFlowFilterChipColors(),
                                    label = { Text("Best $players") }
                                )
                            }
                        }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredGames, key = { it.objectId.ifBlank { it.name } }) { game ->
                            GameCard(
                                game = game,
                                onClick = { selectedGame = game },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (filteredGames.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No games match these filters",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (hasActiveFilters) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BoardFlowOutlinedButton(
                                            onClick = {
                                                tabMode = TabMode.OWNED
                                                sortMode = SortMode.RATING
                                                filterPlayers = null
                                                filterBestFor = null
                                            }
                                        ) {
                                            Text("Clear filters")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(6) { ShimmerGameCard() }
    }
}

@Composable
private fun ShimmerGameCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(76.dp).background(shimmer, RoundedCornerShape(8.dp)))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(shimmer, RoundedCornerShape(4.dp)))
                Box(Modifier.fillMaxWidth(0.45f).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
                Box(Modifier.fillMaxWidth(0.3f).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            if (onRetry != null) {
                BoardFlowButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    accountReady: Boolean,
    spreadsheetReady: Boolean,
    hasCachedSource: Boolean,
    onLoad: (() -> Unit)?
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
            Text(
                "No collection loaded",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                when {
                    !accountReady && hasCachedSource -> "No cached collection is available on this device yet. Refresh from BGG in the Sync tab to cache it here."
                    !accountReady -> "Use the Sync tab to refresh your collection from BGG and cache it on this device."
                    !spreadsheetReady -> "Connect a spreadsheet in the Sync tab."
                    else -> "Tap refresh to load your collection."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (accountReady && spreadsheetReady && onLoad != null) {
                BoardFlowButton(onClick = onLoad) {
                    Text("Load Collection")
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bggUrl = bggSleevesUrl(game)
    val driveUrl = game.shareUrl?.takeIf { it.isNotBlank() }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(CardDefaults.shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            CollectionThumbnail(
                game = game,
                onOpenBgg = bggUrl?.let { { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
                onOpenDrive = driveUrl?.let { { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        game.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    game.rating?.let {
                        InlineStat(
                            icon = Icons.Default.Star,
                            label = formatDecimal(it),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (game.isWishlisted) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Wishlisted",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    game.yearPublished?.let { InlineStat(icon = Icons.Default.CalendarToday, label = it.toString()) }
                    game.weight?.let { InlineStat(icon = Icons.Default.Scale, label = formatDecimal(it)) }
                    game.playingTime?.let { InlineStat(icon = Icons.Default.Schedule, label = "${it}m") }
                    playerLabel(game)?.let { InlineStat(icon = Icons.Default.Groups, label = it) }
                }

                if (!game.bestPlayers.isNullOrBlank() || !game.recommendedPlayers.isNullOrBlank()) {
                    val recommendation = buildList {
                        game.bestPlayers?.takeIf { it.isNotBlank() }?.let { add("Best: $it") }
                        game.recommendedPlayers?.takeIf { it.isNotBlank() }?.let { add("Recommended: $it") }
                    }.joinToString("  -  ")
                    Text(
                        recommendation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            }
        }
    }
}

@Composable
private fun GameDetailsDialog(
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                    game.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.48f)
                                    )
                                }
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
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("  Open BGG")
                            }
                        }
                        if (driveUrl != null) {
                            BoardFlowOutlinedButton(
                                onClick = { open(driveUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("  Drive")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    icon: ImageVector,
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
                icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = tint
            )
            if (!iconOnly) {
                Text(
                    label,
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
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
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
                .padding(horizontal = if (emphasizeSurface) 10.dp else 2.dp, vertical = if (emphasizeSurface) 9.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (secondary) 0.55f else 0.8f)
            )
            Text(
                value,
                style = if (secondary) {
                    MaterialTheme.typography.labelMedium.withTabularNumbers()
                } else {
                    MaterialTheme.typography.bodyMedium.withTabularNumbers()
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (secondary) 0.75f else 1f)
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
            .entries.sortedBy { it.key }
    }

    when {
        game.sleeveStatus == GameItem.SleeveStatus.MISSING ->
            Text(
                "No sleeve data on BGG yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        game.sleeveStatus == GameItem.SleeveStatus.ERROR ->
            Text(
                game.sleeveNote ?: "Could not load sleeve data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
            )
        grouped.isEmpty() -> Unit
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            size.ifBlank { "Unknown size" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (total > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                shape = CircleShape
                            ) {
                                Text(
                                    "$total",
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

@Composable
private fun CollectionThumbnail(
    game: GameItem,
    onOpenBgg: (() -> Unit)?,
    onOpenDrive: (() -> Unit)?
) {
    val shape = MaterialTheme.shapes.medium
    Box(modifier = Modifier.size(76.dp)) {
        if (!game.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = game.thumbnailUrl,
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(shape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallLinkIcon(
                icon = Icons.Default.Language,
                contentDescription = "Open on BoardGameGeek",
                onClick = onOpenBgg
            )
            SmallLinkIcon(
                icon = Icons.Default.FolderOpen,
                contentDescription = "Open Drive folder",
                onClick = onOpenDrive
            )
        }
    }
}

@Composable
private fun SmallLinkIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)?
) {
    Surface(
        color = if (onClick != null) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .shadow(1.dp, MaterialTheme.shapes.small)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .size(12.dp),
            tint = if (onClick != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun InlineStat(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    large: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (large) 18.dp else 14.dp),
            tint = tint
        )
        Text(
            text = label,
            style = if (large) MaterialTheme.typography.titleMedium.withTabularNumbers()
                    else MaterialTheme.typography.labelMedium.withTabularNumbers(),
            fontWeight = if (large) FontWeight.SemiBold else null,
            color = tint
        )
    }
}

private fun playerLabel(game: GameItem): String? {
    return when {
        game.minPlayers != null && game.maxPlayers != null && game.minPlayers != game.maxPlayers ->
            "${game.minPlayers}-${game.maxPlayers} players"
        game.minPlayers != null ->
            "${game.minPlayers} players"
        else -> null
    }
}

private fun formatDecimal(value: Double): String = String.format("%.1f", value)

private fun bestForMatches(game: GameItem, players: Int): Boolean {
    val value = game.bestPlayers?.lowercase()?.trim().orEmpty()
    if (value.isBlank()) return false
    if (value == players.toString()) return true
    return value
        .split(",", "/", ";")
        .map { it.trim() }
        .any { token ->
            when {
                token == players.toString() -> true
                "-" in token -> {
                    val parts = token.split("-").map { it.trim().toIntOrNull() }
                    val min = parts.getOrNull(0)
                    val max = parts.getOrNull(1)
                    min != null && max != null && players in min..max
                }
                else -> false
            }
        }
}

private fun overviewStats(game: GameItem): List<SectionStat> {
    val stats = mutableListOf<SectionStat>()
    game.yearPublished?.toString()?.let { stats += SectionStat("Year", it) }
    playerLabel(game)?.let { stats += SectionStat("Players", it) }
    compactPlayTime(game)?.let { stats += SectionStat("Play time", it) }
    game.recommendedAge?.takeIf { it.isNotBlank() }?.let { stats += SectionStat("Age", it) }
    game.weight?.let { stats += SectionStat("Weight", formatDecimal(it)) }
    return stats
}

private fun ratingStats(game: GameItem): List<SectionStat> {
    return listOfNotNull(
        game.rating?.let { SectionStat("BGG rating", formatDecimal(it)) },
        game.bayesAverage?.let { SectionStat("Bayes rating", formatDecimal(it)) },
        game.rank?.let { SectionStat("Rank", "#$it") },
        game.numPlays?.takeIf { it > 0 }?.let { SectionStat("Plays", it.toString()) }
    )
}

private fun playerPreferenceStats(game: GameItem): List<SectionStat> {
    return listOfNotNull(
        game.bestPlayers?.takeIf { it.isNotBlank() }?.let { SectionStat("Best for", it) },
        game.recommendedPlayers?.takeIf { it.isNotBlank() }?.let { SectionStat("Recommended for", it) }
    )
}

private fun customDetailRows(game: GameItem): List<SectionStat> {
    val handledKeys = setOf(
        "objectid",
        "collid",
        "objectname",
        "game",
        "objecttype",
        "originalname",
        "yearpublished",
        "year",
        "rank",
        "average",
        "score",
        "communityrating",
        "baverage",
        "avgweight",
        "weight",
        "minplayers",
        "maxplayers",
        "playingtime",
        "minplaytime",
        "maxplaytime",
        "numowned",
        "numplays",
        "thumbnail",
        "shareurl",
        "share_url",
        "share url",
        "qrimage",
        "qr_image",
        "qr image",
        "drive",
        "language",
        "languagedependence",
        "bgglanguagedependence",
        "bggbestplayers",
        "bggrecplayers",
        "bggrecagerange",
        "bggurl",
        "own",
        "wishlist",
        "numowned",
        "price",
        "origprice",
        "orig price",
        "sleeved",
        "sleeves",
        "sleevejson"
    )
    return game.spreadsheetValues.entries
        .filter { (key, value) -> value.isNotBlank() && key !in handledKeys }
        .sortedBy { it.key }
        .map { (key, value) -> SectionStat(formatSourceKey(key), value) }
}

private fun bggSleevesUrl(game: GameItem): String? {
    val baseUrl = game.bggUrl?.substringBefore('?')?.trimEnd('/')
    if (!baseUrl.isNullOrBlank()) {
        return if (baseUrl.endsWith("/sleeves", ignoreCase = true)) baseUrl else "$baseUrl/sleeves"
    }
    val objectId = game.objectId.takeIf { it.isNotBlank() } ?: return null
    val objectType = game.spreadsheetValues["objecttype"] ?: game.bggValues["objecttype"]
    val route = when (objectType?.trim()?.lowercase()) {
        "boardgameexpansion" -> "boardgameexpansion"
        "boardgameaccessory" -> "boardgameaccessory"
        else -> "boardgame"
    }
    return "https://boardgamegeek.com/$route/$objectId/sleeves"
}

private fun formatSourceKey(key: String): String {
    if (key.equals("origprice", ignoreCase = true) || key.equals("orig price", ignoreCase = true)) {
        return "Price"
    }
    return key
        .replace('_', ' ')
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { it.titlecase() }
        }
}

private fun compactPlayTime(game: GameItem): String? {
    val playTime = game.playingTime
    val minPlayTime = game.minPlayTime
    val maxPlayTime = game.maxPlayTime
    return when {
        playTime != null -> "${playTime} min"
        minPlayTime != null && maxPlayTime != null && minPlayTime != maxPlayTime -> "$minPlayTime-$maxPlayTime min"
        minPlayTime != null -> "${minPlayTime} min"
        maxPlayTime != null -> "${maxPlayTime} min"
        else -> null
    }
}

private fun isSleeved(game: GameItem): Boolean {
    if (game.sleeveCardSets.any { (it.count ?: 0) > 0 }) return true
    return game.spreadsheetValues.entries.any { (key, value) ->
        key.equals("sleeved", ignoreCase = true) &&
            value.trim().lowercase() in setOf("1", "1.0", "true", "yes", "y")
    }
}

private data class HeaderChip(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)


private fun headerStatusChips(
    game: GameItem,
    primary: Color,
    secondary: Color
): List<HeaderChip> {
    return buildList {
        if (isSleeved(game)) {
            add(HeaderChip("Sleeved", Icons.Default.Check, primary))
        }
        if (game.isOwned) {
            add(HeaderChip("Owned", Icons.Default.Inventory2, primary))
        }
        if (game.isWishlisted) {
            add(HeaderChip("Wishlist", Icons.Default.Bookmark, secondary))
        }
    }
}

@Composable
private fun boardFlowFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)
