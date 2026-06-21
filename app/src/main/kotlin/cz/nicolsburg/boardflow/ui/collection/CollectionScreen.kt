package cz.nicolsburg.boardflow.ui.collection

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import coil.compose.AsyncImage
import cz.nicolsburg.boardflow.SyncViewModel
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.SleeveTrackingState
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationKind
import cz.nicolsburg.boardflow.ui.common.BoardFlowFilterChip
import cz.nicolsburg.boardflow.ui.common.BoardFlowFilterSection
import cz.nicolsburg.boardflow.ui.common.BoardFlowInlineAction
import cz.nicolsburg.boardflow.ui.common.BoardFlowIcons
import cz.nicolsburg.boardflow.ui.common.BoardFlowAnimatedVisibility
import cz.nicolsburg.boardflow.ui.common.BoardFlowPullRefreshContainer
import cz.nicolsburg.boardflow.ui.common.BoardFlowModalBottomSheet
import cz.nicolsburg.boardflow.ui.common.rememberBoardFlowPressScale
import cz.nicolsburg.boardflow.ui.common.rememberBoardFlowShimmerAlpha
import cz.nicolsburg.boardflow.ui.common.BoardFlowOutlinedButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowSurfaceTokens
import cz.nicolsburg.boardflow.ui.common.GameSearchField
import cz.nicolsburg.boardflow.ui.common.SearchFieldActionButton
import cz.nicolsburg.boardflow.ui.common.ScreenTabRow
import cz.nicolsburg.boardflow.ui.common.swipeToNavigateTabs
import kotlinx.coroutines.flow.collect

private enum class SortMode(val label: String) {
    RATING("Rating"),
    NAME("Name"),
    WEIGHT("Weight"),
    PLAYS("Plays")
}

private enum class TabMode(val label: String) {
    SHELF("My Shelf"),
    SLEEVES("Sleeves"),
    STATS("Stats")
}

private enum class OwnershipFilter(val label: String) {
    ANY("Any"),
    OWNED("Owned"),
    WISHLIST("Wishlist"),
    PLAYED_NOT_OWNED("Not owned"),
}

private enum class PlayStatusFilter(val label: String) {
    ANY("Any"),
    PLAYED("Played"),
    UNPLAYED("Unplayed")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(
    syncViewModel: SyncViewModel,
    historyPlays: List<LoggedPlay> = emptyList(),
    players: List<Player> = emptyList(),
    personalRatings: Map<String, Int> = emptyMap(),
    onLogPlay: (gameId: Int, gameName: String, thumbnailUrl: String?) -> Unit = { _, _, _ -> },
    onViewHistory: (Int) -> Unit = {},
    onViewHistoryPlayer: (gameId: Int, playerName: String) -> Unit = { _, _ -> },
    onViewPlayers: (playerName: String) -> Unit = {},
    onHeaderFilterStateChange: (visible: Boolean, hasActiveFilters: Boolean, onClick: (() -> Unit)?) -> Unit = { _, _, _ -> },
    onActiveTabChange: (String?) -> Unit = {},
    onMarkAsPlayed: (gameId: Int, gameName: String) -> Unit = { _, _ -> },
    onRateGame: (gameId: Int, objectId: String, rating: Int) -> Unit = { _, _, _ -> },
    onClearRating: (objectId: String) -> Unit = {},
) {
    val account by syncViewModel.account.collectAsState()
    val spreadsheetId by syncViewModel.spreadsheetId.collectAsState()
    val allGames by syncViewModel.collectionGames.collectAsState()
    val loading by syncViewModel.collectionLoading.collectAsState()
    val error by syncViewModel.collectionError.collectAsState()
    val sleevesExcludedGameIds by syncViewModel.sleevesExcludedGameIds.collectAsState()
    val hasBggCredentials by syncViewModel.hasBggCredentials.collectAsState()
    val lastSyncedAt by syncViewModel.lastSyncedAt.collectAsState()

    var pendingSyncConfirm by remember { mutableStateOf(false) }

    fun triggerSync(action: () -> Unit = { syncViewModel.refreshCollection(forceRefresh = true) }) {
        val elapsed = System.currentTimeMillis() - lastSyncedAt
        if (lastSyncedAt > 0L && elapsed < 3_600_000L) {
            pendingSyncConfirm = true
        } else {
            action()
        }
    }

    if (pendingSyncConfirm) {
        val elapsedMs = System.currentTimeMillis() - lastSyncedAt
        val minutes = (elapsedMs / 60_000).toInt()
        val timeText = if (minutes < 1) "less than a minute ago" else "$minutes minute${if (minutes == 1) "" else "s"} ago"
        BoardFlowConfirmationDialog(
            title = "Sync again?",
            message = "Collection was last synced $timeText. Do you want to sync again?",
            confirmLabel = "Sync",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.NEUTRAL,
            onConfirm = {
                pendingSyncConfirm = false
                syncViewModel.refreshCollection(forceRefresh = true)
            },
            onDismiss = { pendingSyncConfirm = false }
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.RATING) }
    var tabMode by remember { mutableStateOf(TabMode.SHELF) }
    var filterOwnership by remember { mutableStateOf(OwnershipFilter.OWNED) }
    var filterPlayStatus by remember { mutableStateOf(PlayStatusFilter.ANY) }
    var filterPlayers by remember { mutableStateOf<Int?>(null) }
    var filterBestFor by remember { mutableStateOf<Int?>(null) }
    var filterRecommendedFor by remember { mutableStateOf<Int?>(null) }
    var sleevesHighlightGroup by remember { mutableStateOf<String?>(null) }
    var sleevesReturnGame by remember { mutableStateOf<GameItem?>(null) }
    var sleevesReturnTab by remember { mutableStateOf(TabMode.SHELF) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    var sleeveTrackingGame by remember { mutableStateOf<GameItem?>(null) }
    var sleeveTrackingReturnGame by remember { mutableStateOf<GameItem?>(null) }

    BackHandler(enabled = tabMode == TabMode.SLEEVES && sleevesReturnGame != null) {
        selectedGame = sleevesReturnGame
        sleevesReturnGame = null
        tabMode = sleevesReturnTab
        sleevesHighlightGroup = null
    }

    val listState = rememberLazyListState()
    val sleeveListState = rememberLazyListState()
    var controlsVisible by remember { mutableStateOf(true) }
    val hasActiveFilters =
        filterOwnership != OwnershipFilter.OWNED ||
                filterPlayStatus != PlayStatusFilter.ANY ||
                filterPlayers != null ||
                filterBestFor != null ||
                filterRecommendedFor != null ||
                sortMode != SortMode.RATING
    val showHeaderFilterAction =
        !controlsVisible &&
                tabMode != TabMode.SLEEVES &&
                tabMode != TabMode.STATS &&
                allGames.isNotEmpty() &&
                !loading &&
                error == null

    LaunchedEffect(account, spreadsheetId) {
        if (allGames.isNotEmpty() || loading) return@LaunchedEffect
        syncViewModel.loadCachedCollection()
    }

    LaunchedEffect(searchQuery, sortMode, tabMode, filterOwnership, filterPlayStatus, filterPlayers, filterBestFor, filterRecommendedFor) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(tabMode) {
        controlsVisible = true
    }

    val playedGameIds = remember(historyPlays) { historyPlays.map { it.gameId }.toSet() }

    val filteredGames = remember(allGames, searchQuery, sortMode, tabMode, filterOwnership, filterPlayStatus, filterPlayers, filterBestFor, filterRecommendedFor, playedGameIds) {
        var result = allGames

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(query) }
        }

        result = when (tabMode) {
            TabMode.SHELF -> {
                val byOwnership = when (filterOwnership) {
                    OwnershipFilter.OWNED -> result.filter { it.isOwned }
                    OwnershipFilter.WISHLIST -> result.filter { it.isWishlisted }
                    OwnershipFilter.PLAYED_NOT_OWNED ->
                        result.filter { !it.isOwned && isPlayedGame(it, playedGameIds) }
                    OwnershipFilter.ANY -> result
                }
                when (filterPlayStatus) {
                    PlayStatusFilter.ANY -> byOwnership
                    PlayStatusFilter.PLAYED -> byOwnership.filter { isPlayedGame(it, playedGameIds) }
                    PlayStatusFilter.UNPLAYED -> byOwnership.filter { !isPlayedGame(it, playedGameIds) }
                }
            }
            TabMode.SLEEVES -> emptyList()
            TabMode.STATS -> emptyList()
        }

        filterPlayers?.let { players ->
            result = result.filter { (it.minPlayers ?: 1) <= players && (it.maxPlayers ?: 99) >= players }
        }

        filterBestFor?.let { players ->
            result = result.filter { bestForMatches(it, players) }
        }

        filterRecommendedFor?.let { players ->
            result = result.filter { recommendedForMatches(it, players) }
        }

        when (sortMode) {
            SortMode.NAME -> result.sortedBy { it.name.lowercase() }
            SortMode.RATING -> result.sortedByDescending { it.rating ?: 0.0 }
            SortMode.WEIGHT -> result.sortedByDescending { it.weight ?: 0.0 }
            SortMode.PLAYS -> result.sortedByDescending { it.numPlays ?: 0 }
        }
    }

    val activeListState = if (tabMode == TabMode.SLEEVES) sleeveListState else listState
    val activeListAtTop by remember(activeListState, tabMode) {
        derivedStateOf {
            activeListState.firstVisibleItemIndex == 0 && activeListState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(activeListState, tabMode) {
        var lastIndex = activeListState.firstVisibleItemIndex
        var lastOffset = activeListState.firstVisibleItemScrollOffset
        snapshotFlow { activeListState.firstVisibleItemIndex to activeListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)
                val atTop = index == 0 && offset < 8
                controlsVisible = atTop || !scrollingDown
                lastIndex = index
                lastOffset = offset
            }
    }

    LaunchedEffect(showHeaderFilterAction, hasActiveFilters) {
        onHeaderFilterStateChange(showHeaderFilterAction, hasActiveFilters) {
            showFilters = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onHeaderFilterStateChange(false, false, null)
            onActiveTabChange(null)
        }
    }

    LaunchedEffect(controlsVisible, tabMode) {
        onActiveTabChange(if (controlsVisible) null else tabMode.label)
    }

    selectedGame?.let { game ->
        val personalRating = remember(personalRatings, game.objectId) { personalRatings[game.objectId] }
        GameDetailsDialog(
            game = game,
            onDismiss = { selectedGame = null; sleevesReturnGame = null },
            historyPlays = historyPlays,
            players = players,
            personalRating = personalRating,
            onRateGame = { rating ->
                val gameId = game.objectId.toIntOrNull() ?: 0
                onRateGame(gameId, game.objectId, rating)
            },
            onClearRating = { onClearRating(game.objectId) },
            onLogPlay = {
                selectedGame = null
                onLogPlay(game.objectId.toIntOrNull() ?: 0, game.name, game.thumbnailUrl)
            },
            onViewHistory = { gameId ->
                selectedGame = null
                onViewHistory(gameId)
            },
            onViewHistoryPlayer = { gameId, playerName ->
                selectedGame = null
                onViewHistoryPlayer(gameId, playerName)
            },
            onViewPlayers = { playerName ->
                selectedGame = null
                onViewPlayers(playerName)
            },
            canEditSleeveTracking = syncViewModel.canEditSleeveTracking(),
            onOpenSleeveTrackingActions = { currentGame ->
                sleeveTrackingReturnGame = currentGame
                sleeveTrackingGame = currentGame
                selectedGame = null
            },
            onNavigateToSleeve = { groupName ->
                sleevesReturnGame = selectedGame
                sleevesReturnTab = tabMode
                selectedGame = null
                tabMode = TabMode.SLEEVES
                sleevesHighlightGroup = groupName
            }
        )
    }

    sleeveTrackingGame?.let { game ->
        BoardFlowModalBottomSheet(
            onDismissRequest = {
                sleeveTrackingGame = null
                selectedGame = sleeveTrackingReturnGame
                sleeveTrackingReturnGame = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            SleeveTrackingActionSheetContent(
                game = game,
                trackingStatus = sheetSleeveStatus(game),
                canEdit = syncViewModel.canEditSleeveTracking(),
                onSelectStatus = { status ->
                    syncViewModel.updateSleeveTrackingStatus(
                        game = game,
                        status = status,
                        onSuccess = { updatedGame ->
                            sleeveTrackingGame = null
                            sleeveTrackingReturnGame = null
                            selectedGame = updatedGame
                        }
                    )
                }
            )
        }
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .swipeToNavigateTabs(
                    tabCount = TabMode.entries.size,
                    selectedIndex = tabMode.ordinal,
                    onNavigate = { tabMode = TabMode.entries[it] }
                )
        ) {
            when {
                loading && allGames.isEmpty() -> LoadingState()
                else -> {
                    BoardFlowAnimatedVisibility(visible = controlsVisible) {
                        ScreenTabRow(
                            tabs = TabMode.entries.map { it.label },
                            selectedIndex = tabMode.ordinal,
                            onTabSelected = { tabMode = TabMode.entries[it] }
                        )
                    }

                    if (showFilters) {
                        BoardFlowModalBottomSheet(
                            onDismissRequest = { showFilters = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ) {
                            FilterSheetContent(
                                sortMode = sortMode,
                                onSortMode = { sortMode = it },
                                filterOwnership = filterOwnership,
                                onFilterOwnership = { filterOwnership = it },
                                filterPlayStatus = filterPlayStatus,
                                onFilterPlayStatus = { filterPlayStatus = it },
                                filterPlayers = filterPlayers,
                                onFilterPlayers = { filterPlayers = it },
                                filterBestFor = filterBestFor,
                                onFilterBestFor = { filterBestFor = it },
                                filterRecommendedFor = filterRecommendedFor,
                                onFilterRecommendedFor = { filterRecommendedFor = it },
                                hasActiveFilters = hasActiveFilters,
                                onReset = {
                                    sortMode = SortMode.RATING
                                    filterOwnership = OwnershipFilter.OWNED
                                    filterPlayStatus = PlayStatusFilter.ANY
                                    filterPlayers = null
                                    filterBestFor = null
                                    filterRecommendedFor = null
                                }
                            )
                        }
                    }

                    BoardFlowPullRefreshContainer(
                        isRefreshing = loading,
                        isAtTop = activeListAtTop,
                        onRefresh = { triggerSync() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            error != null && allGames.isEmpty() -> ErrorState(
                                error = error.orEmpty(),
                                onRetry = if (hasBggCredentials) ({ triggerSync() }) else null
                            )

                            allGames.isEmpty() -> EmptyState(
                                accountReady = account != null,
                                spreadsheetReady = spreadsheetId.isNotBlank(),
                                hasCachedSource = spreadsheetId.isNotBlank(),
                                onLoad = if (hasBggCredentials) ({ triggerSync() }) else null
                            )

                            tabMode == TabMode.SLEEVES -> SleevesContent(
                                allGames = allGames,
                                listState = sleeveListState,
                                excludedGameIds = sleevesExcludedGameIds,
                                onToggleExclusion = { syncViewModel.toggleSleeveGameExclusion(it) },
                                onExcludeAll = { syncViewModel.excludeAllSleeveGames(it) },
                                onIncludeAll = { syncViewModel.includeAllSleeveGames() },
                                initiallyExpandedGroup = sleevesHighlightGroup
                            )

                            tabMode == TabMode.STATS -> CollectionStatsTab(allGames, onMarkAsPlayed)

                            else -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    BoardFlowAnimatedVisibility(visible = controlsVisible) {
                                        GameSearchField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            trailingAction = {
                                                Box {
                                                    SearchFieldActionButton(onClick = { showFilters = true }) {
                                                        Icon(
                                                            BoardFlowIcons.Filter,
                                                            contentDescription = "Sort & filter"
                                                        )
                                                    }
                                                    if (hasActiveFilters) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(top = 8.dp, end = 8.dp)
                                                                .size(7.dp)
                                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
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
                                                modifier = Modifier.animateItem()
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
                                                                sortMode = SortMode.RATING
                                                                filterOwnership = OwnershipFilter.OWNED
                                                                filterPlayStatus = PlayStatusFilter.ANY
                                                                filterPlayers = null
                                                                filterBestFor = null
                                                                filterRecommendedFor = null
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
            }
        }
    }
}

@Composable
private fun LoadingState() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(6) { ShimmerGameCard() }
    }
}

@Composable
private fun ShimmerGameCard() {
    val alpha = rememberBoardFlowShimmerAlpha(label = "collectionShimmerAlpha")
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BoardFlowSurfaceTokens.Shape,
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
            Box(
                Modifier
                    .size(76.dp)
                    .background(shimmer, RoundedCornerShape(8.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .background(shimmer, RoundedCornerShape(4.dp))
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(10.dp)
                        .background(shimmer, RoundedCornerShape(4.dp))
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.3f)
                        .height(10.dp)
                        .background(shimmer, RoundedCornerShape(4.dp))
                )
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
                    !accountReady && hasCachedSource ->
                        "No cached collection is available on this device yet. Refresh from BGG in the Sync tab to cache it here."

                    !accountReady ->
                        "Use the Sync tab to refresh your collection from BGG and cache it on this device."

                    !spreadsheetReady ->
                        "Connect a spreadsheet in the Sync tab."

                    else ->
                        "Tap refresh to load your collection."
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bggUrl = bggSleevesUrl(game)
    val driveUrl = game.shareUrl?.takeIf { it.isNotBlank() }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = rememberBoardFlowPressScale(isPressed = isPressed, label = "cardScale")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(BoardFlowSurfaceTokens.Shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = BoardFlowSurfaceTokens.Shape,
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
                    game.yearPublished?.let {
                        InlineStat(icon = Icons.Default.CalendarToday, label = it.toString())
                    }
                    game.weight?.let {
                        InlineStat(icon = Icons.Default.Scale, label = collectionWeightLabel(it))
                    }
                    game.playingTime?.let {
                        InlineStat(icon = Icons.Default.Schedule, label = "${it}m")
                    }
                    playerLabel(game)?.let {
                        InlineStat(icon = Icons.Default.Groups, label = it)
                    }
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

private fun collectionWeightLabel(weight: Double): String = when (gameWeightLabel(weight)) {
    "Medium-Light" -> "Mid-Light"
    "Medium-Heavy" -> "Mid-Heavy"
    else -> gameWeightLabel(weight)
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
        color = if (onClick != null) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
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
            tint = if (onClick != null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
        )
    }
}

private fun playerCountMatches(rawValue: String?, players: Int): Boolean {
    val value = rawValue?.lowercase()?.trim().orEmpty()
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

private fun bestForMatches(game: GameItem, players: Int): Boolean =
    playerCountMatches(game.bestPlayers, players)

private fun recommendedForMatches(game: GameItem, players: Int): Boolean =
    playerCountMatches(game.recommendedPlayers, players)

// A game counts as played if it appears in local/BGG play history or carries a BGG play count.
private fun isPlayedGame(game: GameItem, playedGameIds: Set<Int>): Boolean =
    game.objectId.toIntOrNull() in playedGameIds || (game.numPlays ?: 0) > 0

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FilterSheetContent(
    sortMode: SortMode,
    onSortMode: (SortMode) -> Unit,
    filterOwnership: OwnershipFilter,
    onFilterOwnership: (OwnershipFilter) -> Unit,
    filterPlayStatus: PlayStatusFilter,
    onFilterPlayStatus: (PlayStatusFilter) -> Unit,
    filterPlayers: Int?,
    onFilterPlayers: (Int?) -> Unit,
    filterBestFor: Int?,
    onFilterBestFor: (Int?) -> Unit,
    filterRecommendedFor: Int?,
    onFilterRecommendedFor: (Int?) -> Unit,
    hasActiveFilters: Boolean,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Sort & Filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Collection view",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hasActiveFilters) {
                BoardFlowInlineAction(onClick = onReset) { Text("Reset") }
            }
        }

        BoardFlowFilterSection(
            label = "Sort by",
            detail = "Choose how the collection list is ordered."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortMode.entries.forEach { mode ->
                    BoardFlowFilterChip(
                        selected = sortMode == mode,
                        onClick = { onSortMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        BoardFlowFilterSection(
            label = "Show",
            detail = "Which games to list from your shelf and beyond."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OwnershipFilter.entries.forEach { option ->
                    BoardFlowFilterChip(
                        selected = filterOwnership == option,
                        onClick = { onFilterOwnership(option) },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        BoardFlowFilterSection(
            label = "Play status",
            detail = "Filter by whether you have played the game."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayStatusFilter.entries.forEach { option ->
                    BoardFlowFilterChip(
                        selected = filterPlayStatus == option,
                        onClick = { onFilterPlayStatus(option) },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        BoardFlowFilterSection(
            label = "Player Counts",
            detail = "Games filtered by player count information."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayerCountRow(
                    label = "Supports",
                    description = "Game supports this player count",
                    selected = filterPlayers,
                    onSelect = onFilterPlayers
                )
                PlayerCountRow(
                    label = "Recommended",
                    description = "Community recommends this count",
                    selected = filterRecommendedFor,
                    onSelect = onFilterRecommendedFor
                )
                PlayerCountRow(
                    label = "Best",
                    description = "Community's best player count",
                    selected = filterBestFor,
                    onSelect = onFilterBestFor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

// One labelled group inside the merged "Player Counts" card. The label and its definition sit on
// their own line above the chips, giving the chip row the full card width so all seven chips fit on
// a single line even on narrow phones. All chips use BoardFlowFilterChip so their selected/
// unselected styling and interaction match the other filter chips in the sheet.
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PlayerCountRow(
    label: String,
    description: String,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BoardFlowFilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Any") }
            )
            (1..6).forEach { n ->
                BoardFlowFilterChip(
                    selected = selected == n,
                    onClick = { onSelect(if (selected == n) null else n) },
                    label = { Text(if (n == 6) "6+" else "$n") }
                )
            }
        }
    }
}
