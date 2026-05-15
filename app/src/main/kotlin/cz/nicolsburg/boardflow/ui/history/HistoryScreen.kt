@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cz.nicolsburg.boardflow.ui.history

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.data.PlayShareSerializer
import cz.nicolsburg.boardflow.data.QrGenerator
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationKind
import cz.nicolsburg.boardflow.ui.common.BoardFlowDestructiveButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowIconButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowIcons
import cz.nicolsburg.boardflow.ui.common.BoardFlowSecondaryButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowTonalButton
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.PlayerResult
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import cz.nicolsburg.boardflow.ui.common.BoardFlowFilterChip
import cz.nicolsburg.boardflow.ui.common.BoardFlowFilterSection
import cz.nicolsburg.boardflow.ui.common.BoardFlowInlineAction
import cz.nicolsburg.boardflow.ui.common.BoardFlowMotion
import cz.nicolsburg.boardflow.ui.history.playInsights
import cz.nicolsburg.boardflow.ui.history.resolveCurrentPlayerName
import cz.nicolsburg.boardflow.ui.common.BoardFlowPullRefreshContainer
import cz.nicolsburg.boardflow.ui.common.BoardFlowAnimatedVisibility
import cz.nicolsburg.boardflow.ui.common.PlayerResultEditorCard
import cz.nicolsburg.boardflow.ui.common.BoardFlowModalBottomSheet
import cz.nicolsburg.boardflow.ui.common.BoardFlowSurfaceTokens
import cz.nicolsburg.boardflow.ui.common.GameBackdrop
import cz.nicolsburg.boardflow.ui.common.GameSearchField
import cz.nicolsburg.boardflow.ui.common.SearchFieldActionButton
import cz.nicolsburg.boardflow.ui.common.boardFlowTween
import cz.nicolsburg.boardflow.ui.common.rememberBoardFlowPressScale
import cz.nicolsburg.boardflow.ui.common.rememberBoardFlowShimmerAlpha
import kotlinx.coroutines.flow.collect
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import cz.nicolsburg.boardflow.ui.common.ScreenTabRow
import cz.nicolsburg.boardflow.ui.common.swipeToNavigateTabs
import androidx.activity.compose.BackHandler
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.ui.collection.GameDetailsDialog
import cz.nicolsburg.boardflow.ui.players.AddPlayerDialog
import cz.nicolsburg.boardflow.ui.players.EditPlayerDialog
import cz.nicolsburg.boardflow.ui.players.PlayerDetailDialog
import cz.nicolsburg.boardflow.ui.players.PlayersTabContent
import cz.nicolsburg.boardflow.ui.players.statsForPlayer
import java.io.File

private data class HistoryNavState(
    val tab: HistoryTab,
    val filterGameId: Int?,
    val filterGameName: String?,
    val filterPlayer: String?,
    val searchQuery: String,
    val selectedPlayId: String? = null,
    val selectedGameObjectId: String? = null,
    val viewingPlayerId: String? = null
)

private enum class HistorySortMode(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    GAME_NAME("Game name"),
    DURATION("Duration")
}

private enum class HistoryDateRange(val label: String) {
    ALL("All time"),
    THIS_WEEK("Last 7 days"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year")
}

private enum class HistoryTab(val label: String) {
    PLAYS("Plays"),
    STATS("Stats"),
    PLAYERS("Players")
}

@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    onActiveTabChange: (String?) -> Unit = {},
    onPlayAgain: (cz.nicolsburg.boardflow.model.LoggedPlay) -> Unit = {},
    onImportQr: () -> Unit = {}
) {
    val historyPlays by viewModel.historyPlays.collectAsState()
    val collection by viewModel.collection.collectAsState()
    val collectionItems by viewModel.collectionItems.collectAsState()
    val bggPlays by viewModel.bggPlays.collectAsState()
    val bggLoading by viewModel.bggPlaysLoading.collectAsState()
    val bggError by viewModel.bggPlaysError.collectAsState()
    val players by viewModel.players.collectAsState()
    val statsPlayScope by viewModel.statsPlayScope.collectAsState()
    val deletingPlayId by viewModel.deletingBggPlayId.collectAsState()
    val editPlayLoading by viewModel.editPlayLoading.collectAsState()
    val postingPlayId by viewModel.postingPlayId.collectAsState()
    val bggPlaysCacheAgeMinutes by viewModel.bggPlaysCacheAgeMinutes.collectAsState()

    var showBggPlaysRefreshConfirm by remember { mutableStateOf(false) }

    if (showBggPlaysRefreshConfirm) {
        val minutes = bggPlaysCacheAgeMinutes
        val timeText = if (minutes < 1L) "less than a minute ago" else "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        BoardFlowConfirmationDialog(
            title = "Refresh again?",
            message = "Play history was last synced $timeText. Do you want to refresh again?",
            confirmLabel = "Refresh",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.NEUTRAL,
            onConfirm = {
                showBggPlaysRefreshConfirm = false
                viewModel.fetchBggPlays()
            },
            onDismiss = { showBggPlaysRefreshConfirm = false }
        )
    }

    fun triggerBggPlaysRefresh() {
        if (bggPlaysCacheAgeMinutes < 60L) {
            showBggPlaysRefreshConfirm = true
        } else {
            viewModel.fetchBggPlays()
        }
    }
    val syncingUnpostedPlays by viewModel.syncingUnpostedPlays.collectAsState()
    val pendingHistoryNavigation by viewModel.pendingHistoryNavigation.collectAsState()
    var playToDelete by remember { mutableStateOf<LoggedPlay?>(null) }
    var selectedPlay by remember { mutableStateOf<LoggedPlay?>(null) }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    var editingPlay by remember { mutableStateOf<LoggedPlay?>(null) }
    var playToShare by remember { mutableStateOf<LoggedPlay?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    var navHistory by remember { mutableStateOf(listOf<HistoryNavState>()) }
    var restoredViewingPlayerId by remember { mutableStateOf<String?>(null) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var filterGameName by rememberSaveable { mutableStateOf<String?>(null) }
    val backdropUrl by remember(filterGameId, collection) {
        derivedStateOf { filterGameId?.let { id -> collection.firstOrNull { it.id == id }?.thumbnailUrl } }
    }
    val selectedPlayThumbnail by remember(selectedPlay, collection) {
        derivedStateOf { selectedPlay?.gameId?.let { id -> collection.firstOrNull { it.id == id }?.thumbnailUrl } }
    }
    val selectedPlayGame by remember(selectedPlay, collectionItems) {
        derivedStateOf {
            selectedPlay?.let { play ->
                collectionItems.firstOrNull { it.objectId.toIntOrNull() == play.gameId }
            }
        }
    }
    var sortMode by rememberSaveable { mutableStateOf(HistorySortMode.DATE_DESC) }
    var filterDateRange by rememberSaveable { mutableStateOf(HistoryDateRange.ALL) }
    var filterPlayer by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val controlsVisibleState = remember { mutableStateOf(true) }
    var controlsVisible by controlsVisibleState
    val playsListState = rememberLazyListState()
    val statsListState = rememberLazyListState()
    val playersListState = rememberLazyListState()

    val hasActiveFilters = sortMode != HistorySortMode.DATE_DESC ||
        filterDateRange != HistoryDateRange.ALL ||
        filterPlayer != null ||
        filterGameId != null

    val filteredPlays = remember(historyPlays, searchQuery, filterGameId, sortMode, filterDateRange, filterPlayer, players) {
        var result = historyPlays

        filterGameId?.let { id -> result = result.filter { it.gameId == id } }

        if (searchQuery.isNotBlank() && filterGameId == null) {
            val query = searchQuery.trim().lowercase()
            result = result.filter {
                it.gameName.lowercase().contains(query) ||
                    it.players.any { p -> p.name.lowercase().contains(query) }
            }
        }

        val today = LocalDate.now()
        result = when (filterDateRange) {
            HistoryDateRange.ALL -> result
            HistoryDateRange.THIS_WEEK -> {
                val cutoff = today.minusWeeks(1)
                result.filter { runCatching { !LocalDate.parse(it.date).isBefore(cutoff) }.getOrDefault(true) }
            }
            HistoryDateRange.THIS_MONTH -> result.filter {
                runCatching {
                    LocalDate.parse(it.date).let { d -> d.year == today.year && d.monthValue == today.monthValue }
                }.getOrDefault(true)
            }
            HistoryDateRange.THIS_YEAR -> result.filter {
                runCatching { LocalDate.parse(it.date).year == today.year }.getOrDefault(true)
            }
        }

        filterPlayer?.let { playerDisplayName ->
            if (playerDisplayName == "Unknown") {
                result = result.filter { play ->
                    play.players.any { it.name.isNotBlank() && !it.name.matchesSavedPlayer(players) }
                }
            } else {
                val player = players.find { it.displayName == playerDisplayName }
                val names = if (player != null) {
                    (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
                } else {
                    listOf(playerDisplayName.lowercase().trim())
                }
                result = result.filter { play -> play.players.any { it.name.lowercase().trim() in names } }
            }
        }

        when (sortMode) {
            HistorySortMode.DATE_DESC -> result
            HistorySortMode.DATE_ASC -> result.sortedBy { it.date }
            HistorySortMode.GAME_NAME -> result.sortedBy { it.gameName.lowercase() }
            HistorySortMode.DURATION -> result.sortedByDescending { it.durationMinutes }
        }
    }
    val localPendingPlays by remember(historyPlays) {
        derivedStateOf { historyPlays.filter { !it.postedToBgg } }
    }

    var activeTab by rememberSaveable { mutableStateOf(HistoryTab.PLAYS) }
    var showAddPlayerDialog by rememberSaveable { mutableStateOf(false) }
    var editingPlayer by remember { mutableStateOf<cz.nicolsburg.boardflow.model.Player?>(null) }

    BackHandler(enabled = navHistory.isNotEmpty()) {
        val prev = navHistory.last()
        navHistory = navHistory.dropLast(1)
        activeTab = prev.tab
        filterGameId = prev.filterGameId
        filterGameName = prev.filterGameName
        filterPlayer = prev.filterPlayer
        searchQuery = prev.searchQuery
        selectedPlay = prev.selectedPlayId?.let { id -> historyPlays.find { it.id == id } }
        selectedGame = prev.selectedGameObjectId?.let { objId -> collectionItems.firstOrNull { it.objectId == objId } }
        restoredViewingPlayerId = prev.viewingPlayerId
    }

    LaunchedEffect(activeTab) {
        controlsVisible = true
        if (activeTab == HistoryTab.PLAYS) playsListState.scrollToItem(0)
    }

    // Show controls when list reaches the very top (e.g. after filter/search resets position).
    LaunchedEffect(activeTab, playsListState, statsListState, playersListState) {
        snapshotFlow {
            when (activeTab) {
                HistoryTab.PLAYS -> playsListState.firstVisibleItemIndex == 0 && playsListState.firstVisibleItemScrollOffset < 8
                HistoryTab.STATS -> statsListState.firstVisibleItemIndex == 0 && statsListState.firstVisibleItemScrollOffset < 8
                HistoryTab.PLAYERS -> playersListState.firstVisibleItemIndex == 0 && playersListState.firstVisibleItemScrollOffset < 8
            }
        }.collect { atTop -> if (atTop) controlsVisibleState.value = true }
    }

    // Always restore controls when switching tabs.
    LaunchedEffect(activeTab) { controlsVisibleState.value = true }

    val hideThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    val scrollConnection = remember(hideThresholdPx) {
        var accumulated = 0f
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0f) {
                    accumulated += dy
                    if (accumulated < -hideThresholdPx && controlsVisibleState.value) {
                        controlsVisibleState.value = false
                        accumulated = 0f
                    }
                } else if (dy > 0f) {
                    accumulated = 0f
                    controlsVisibleState.value = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(searchQuery, filterGameId, sortMode, filterDateRange, filterPlayer) {
        playsListState.scrollToItem(0)
    }

    LaunchedEffect(Unit) {
        viewModel.loadPlayers()
        viewModel.loadPlayHistory()
        viewModel.loadCachedBggPlays()
    }

    LaunchedEffect(pendingHistoryNavigation) {
        val nav = pendingHistoryNavigation ?: return@LaunchedEffect
        if (nav.showPlayersTab) {
            activeTab = HistoryTab.PLAYERS
        } else {
            activeTab = HistoryTab.PLAYS
            nav.gameId?.let { id ->
                filterGameId = id
                filterGameName = historyPlays.firstOrNull { it.gameId == id }?.gameName
            }
            nav.playerFilter?.let { filterPlayer = it }
        }
        viewModel.consumePendingHistoryFilter()
    }

    LaunchedEffect(controlsVisible, activeTab) {
        onActiveTabChange(if (controlsVisible) null else activeTab.label)
    }

    DisposableEffect(Unit) {
        onDispose { onActiveTabChange(null) }
    }

    playToDelete?.let { play ->
        val isRemotePlay = play.postedToBgg && !play.id.isLikelyLocalUuid()
        BoardFlowConfirmationDialog(
            title = "Delete play?",
            message = if (isRemotePlay) {
                "Delete this play from BGG? This also removes it from the local cached history."
            } else {
                "Delete this local play from this device?"
            },
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.DESTRUCTIVE,
            onConfirm = {
                if (isRemotePlay) {
                    viewModel.deleteBggPlay(
                        playId = play.id,
                        onSuccess = {
                            selectedPlay = null
                            playToDelete = null
                            deleteError = null
                        },
                        onError = { message ->
                            deleteError = message
                            playToDelete = null
                        }
                    )
                } else {
                    viewModel.deleteLocalPlay(
                        playId = play.id,
                        onSuccess = {
                            selectedPlay = null
                            playToDelete = null
                            deleteError = null
                        },
                        onError = { message ->
                            deleteError = message
                            playToDelete = null
                        }
                    )
                }
            },
            onDismiss = { playToDelete = null }
        )
    }

    selectedPlay?.let { play ->
        PlayDetailsDialog(
            play = play,
            thumbnailUrl = selectedPlayThumbnail,
            game = selectedPlayGame,
            players = players,
            historyPlays = historyPlays,
            isDeleting = deletingPlayId == play.id,
            onDismiss = { selectedPlay = null },
            onEdit = { editingPlay = play; selectedPlay = null },
            onDeletePlay = { playToDelete = play },
            onShareQr = { playToShare = play },
            onPlayAgain = { selectedPlay = null; onPlayAgain(play) },
            onViewGame = { g -> selectedGame = g }
        )
    }

    selectedGame?.let { g ->
        val gameObjectId = g.objectId.toIntOrNull()
        GameDetailsDialog(
            game = g,
            onDismiss = { selectedGame = null },
            historyPlays = historyPlays,
            players = players,
            onViewHistory = { _ ->
                if (gameObjectId != null) {
                    navHistory = navHistory + HistoryNavState(
                        tab = activeTab, filterGameId = filterGameId, filterGameName = filterGameName,
                        filterPlayer = filterPlayer, searchQuery = searchQuery,
                        selectedPlayId = selectedPlay?.id, selectedGameObjectId = g.objectId
                    )
                    selectedGame = null
                    selectedPlay = null
                    activeTab = HistoryTab.PLAYS
                    filterGameId = gameObjectId
                    filterGameName = g.name
                    filterPlayer = null
                    searchQuery = ""
                }
            },
            onViewHistoryPlayer = { _, playerName ->
                if (gameObjectId != null) {
                    navHistory = navHistory + HistoryNavState(
                        tab = activeTab, filterGameId = filterGameId, filterGameName = filterGameName,
                        filterPlayer = filterPlayer, searchQuery = searchQuery,
                        selectedPlayId = selectedPlay?.id, selectedGameObjectId = g.objectId
                    )
                    selectedGame = null
                    selectedPlay = null
                    activeTab = HistoryTab.PLAYS
                    filterGameId = gameObjectId
                    filterGameName = g.name
                    filterPlayer = playerName
                    searchQuery = ""
                }
            },
            onViewPlayers = { playerName ->
                navHistory = navHistory + HistoryNavState(
                    tab = activeTab, filterGameId = filterGameId, filterGameName = filterGameName,
                    filterPlayer = filterPlayer, searchQuery = searchQuery,
                    selectedPlayId = selectedPlay?.id, selectedGameObjectId = g.objectId
                )
                selectedGame = null
                selectedPlay = null
                activeTab = HistoryTab.PLAYS
                filterPlayer = playerName
                filterGameId = null
                filterGameName = null
                searchQuery = ""
            }
        )
    }

    playToShare?.let { play ->
        SharePlayQrDialog(
            play = play,
            onDismiss = { playToShare = null }
        )
    }

    editingPlay?.let { play ->
        EditPlayDialog(
            play = play,
            rosterPlayers = players,
            isLoading = editPlayLoading,
            onDismiss = { editingPlay = null; editError = null },
            onSave = { date, durationMinutes, location, comments, players ->
                editError = null
                viewModel.editPlay(
                    play = play,
                    date = date,
                    durationMinutes = durationMinutes,
                    location = location,
                    comments = comments,
                    players = players,
                    onSuccess = {
                        editError = null
                        editingPlay = null
                    },
                    onError = { editError = it }
                )
            }
        )
    }

    if (showAddPlayerDialog) {
        AddPlayerDialog(
            onDismiss = { showAddPlayerDialog = false },
            onAdd = { name -> viewModel.addNewPlayer(name); showAddPlayerDialog = false }
        )
    }

    editingPlayer?.let { ep ->
        val livePlayer = players.find { it.id == ep.id }
        if (livePlayer != null) {
            EditPlayerDialog(
                player = livePlayer,
                onDismiss = { editingPlayer = null },
                onRenameDisplayName = { viewModel.updatePlayerDisplayName(livePlayer.id, it) },
                onUpdateBggUsername = { viewModel.updatePlayerBggUsername(livePlayer.id, it) },
                onAddAlias = { viewModel.addPlayerAlias(livePlayer.id, it) },
                onRemoveAlias = { viewModel.removePlayerAlias(livePlayer.id, it) },
                onDelete = { viewModel.deletePlayer(livePlayer.id); editingPlayer = null }
            )
        } else {
            editingPlayer = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (activeTab == HistoryTab.PLAYERS) {
                FloatingActionButton(onClick = { showAddPlayerDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add player")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        GameBackdrop(imageUrl = backdropUrl, height = 220.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .swipeToNavigateTabs(
                    tabCount = HistoryTab.entries.size,
                    selectedIndex = activeTab.ordinal,
                    onNavigate = { activeTab = HistoryTab.entries[it] }
                )
        ) {
            BoardFlowAnimatedVisibility(visible = controlsVisible) {
                ScreenTabRow(
                    tabs = HistoryTab.entries.map { it.label },
                    selectedIndex = activeTab.ordinal,
                    onTabSelected = { navHistory = emptyList(); activeTab = HistoryTab.entries[it] }
                )
            }

            when (activeTab) {
                HistoryTab.PLAYS -> Column(modifier = Modifier.fillMaxSize()) {
                    deleteError?.let { message ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                        message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            editError?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            BoardFlowAnimatedVisibility(visible = controlsVisible) {
                GameSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    trailingAction = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchFieldActionButton(onClick = onImportQr) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Import QR")
                            }
                            Box {
                                SearchFieldActionButton(onClick = { showFilters = true }) {
                                    Icon(BoardFlowIcons.Filter, contentDescription = "Sort & filter")
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
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            PendingPlaysCard(
                plays = localPendingPlays,
                postingPlayId = postingPlayId,
                syncingUnpostedPlays = syncingUnpostedPlays,
                onPostPlay = viewModel::postSinglePlay,
                onPostAll = viewModel::syncUnpostedPlays,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (showFilters) {
                BoardFlowModalBottomSheet(
                    onDismissRequest = { showFilters = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    HistoryFilterSheetContent(
                        sortMode = sortMode,
                        onSortMode = { sortMode = it },
                        filterDateRange = filterDateRange,
                        onFilterDateRange = { filterDateRange = it },
                        filterPlayer = filterPlayer,
                        onFilterPlayer = { filterPlayer = it },
                        players = players,
                        hasActiveFilters = hasActiveFilters,
                        onReset = {
                            sortMode = HistorySortMode.DATE_DESC
                            filterDateRange = HistoryDateRange.ALL
                            filterPlayer = null
                            filterGameId = null
                            filterGameName = null
                        }
                    )
                }
            }

                    AnimatedVisibility(visible = filterGameId != null || (filterPlayer != null && searchQuery.isBlank())) {
                        val label = filterGameName ?: filterPlayer ?: ""
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Filtered by: $label",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    filterGameId = null
                                    filterGameName = null
                                    filterPlayer = null
                                }) {
                                    Text(
                                        "Clear",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    PlaysContent(
                        plays = filteredPlays,
                        players = players,
                        loading = bggLoading,
                        error = bggError,
                        hasBggUsername = viewModel.prefs.bggUsername.isNotBlank(),
                        onOpenPlay = { selectedPlay = it },
                        onRefresh = ::triggerBggPlaysRefresh,
                        listState = playsListState,
                        hasActiveFilters = hasActiveFilters,
                        onResetFilters = {
                            sortMode = HistorySortMode.DATE_DESC
                            filterDateRange = HistoryDateRange.ALL
                            filterPlayer = null
                            filterGameId = null
                            filterGameName = null
                            searchQuery = ""
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                HistoryTab.STATS -> StatsContent(
                    plays = historyPlays,
                    players = players,
                    statsPlayScope = statsPlayScope,
                    currentPlayerName = resolveCurrentPlayerName(viewModel.prefs.bggUsername, players),
                    listState = statsListState,
                    modifier = Modifier.fillMaxSize(),
                    onGameTapped = { gameId, gameName ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayer, searchQuery)
                        activeTab = HistoryTab.PLAYS
                        filterGameId = gameId
                        filterGameName = gameName
                        filterPlayer = null
                        searchQuery = ""
                    },
                    onPlayerTapped = { playerName ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayer, searchQuery)
                        activeTab = HistoryTab.PLAYS
                        filterPlayer = playerName
                        filterGameId = null
                        filterGameName = null
                        searchQuery = ""
                    },
                    onPlaysFilter = { recentDays ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayer, searchQuery)
                        activeTab = HistoryTab.PLAYS
                        filterDateRange = when {
                            recentDays <= 7  -> HistoryDateRange.THIS_WEEK
                            recentDays <= 31 -> HistoryDateRange.THIS_MONTH
                            else             -> HistoryDateRange.THIS_YEAR
                        }
                        filterGameId = null
                        filterGameName = null
                        filterPlayer = null
                        searchQuery = ""
                    }
                )
                HistoryTab.PLAYERS -> PlayersTabContent(
                    players = players,
                    sourcePlays = historyPlays,
                    currentPlayerName = resolveCurrentPlayerName(viewModel.prefs.bggUsername, players),
                    listState = playersListState,
                    onEditPlayer = { editingPlayer = it },
                    openPlayerId = restoredViewingPlayerId,
                    onOpenPlayerConsumed = { restoredViewingPlayerId = null },
                    onViewPlayerPlays = { playerName, sourcePlayerId ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayer, searchQuery, viewingPlayerId = sourcePlayerId.ifBlank { null })
                        activeTab = HistoryTab.PLAYS
                        filterPlayer = playerName
                    },
                    onViewPlayerGame = { gameId, gameName, sourcePlayerId ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayer, searchQuery, viewingPlayerId = sourcePlayerId.ifBlank { null })
                        activeTab = HistoryTab.PLAYS
                        filterGameId = gameId
                        filterGameName = gameName
                        searchQuery = ""
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        } // Box
    }
}

@Composable
private fun PlaysContent(
    plays: List<LoggedPlay>,
    players: List<Player>,
    loading: Boolean,
    error: String?,
    hasBggUsername: Boolean,
    onOpenPlay: (LoggedPlay) -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hasActiveFilters: Boolean = false,
    onResetFilters: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val emptyState = rememberScrollState()
    val listAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val isAtTop = if (plays.isEmpty() && !loading) emptyState.value == 0 else listAtTop

    BoardFlowPullRefreshContainer(
        isRefreshing = loading,
        isAtTop = isAtTop,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        when {
            loading && plays.isEmpty() -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(5) { ShimmerPlayCard() }
            }

            error != null && plays.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(emptyState),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                    )
                    Text(
                        "Couldn't load history",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            plays.isEmpty() && hasActiveFilters -> Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(emptyState),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        BoardFlowIcons.Filter,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                    Text(
                        "No plays match your filters",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BoardFlowSecondaryButton(onClick = onResetFilters) {
                        Text("Reset filters")
                    }
                }
            }

            plays.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(emptyState),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    Text(
                        "No play history",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (!hasBggUsername)
                            "Set your BGG username in Settings to start tracking your play history."
                        else
                            "Use Sync to refresh your play history from BGG.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(plays, key = { it.id }) { play ->
                    PlayHistoryCard(
                        play = play,
                        players = players,
                        onClick = { onOpenPlay(play) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingPlaysCard(
    plays: List<LoggedPlay>,
    postingPlayId: String?,
    syncingUnpostedPlays: Boolean,
    onPostPlay: (String) -> Unit,
    onPostAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (plays.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BoardFlowSurfaceTokens.ContentCardShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        "Unposted plays",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        if (plays.size == 1) {
                            "1 play is saved locally and ready to post to BGG."
                        } else {
                            "${plays.size} plays are saved locally and ready to post to BGG."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                BoardFlowSecondaryButton(
                    onClick = onPostAll,
                    enabled = !syncingUnpostedPlays && postingPlayId == null
                ) {
                    if (syncingUnpostedPlays) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Post all")
                    }
                }
            }

            plays.take(3).forEach { play ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                play.gameName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${play.date} • ${play.players.size} players",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BoardFlowSecondaryButton(
                            onClick = { onPostPlay(play.id) },
                            enabled = !syncingUnpostedPlays && postingPlayId == null
                        ) {
                            if (postingPlayId == play.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Post")
                            }
                        }
                    }
                }
            }

            if (plays.size > 3) {
                Text(
                    "+${plays.size - 3} more waiting in local history",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}


@Composable
private fun PlayHistoryCard(
    play: LoggedPlay,
    players: List<Player>,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = rememberBoardFlowPressScale(isPressed = isPressed, label = "cardScale")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(BoardFlowSurfaceTokens.Shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = BoardFlowSurfaceTokens.Shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    play.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (play.durationMinutes > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                "${play.durationMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (play.location.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                play.location,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    play.gameName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (play.quantity > 1) {
                    PlayBadge("×${play.quantity}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (play.incomplete) {
                    PlayBadge("partial", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                play.players.forEach { player ->
                    HistoryListPlayerRow(player, resolveDisplayName(player.name, players))
                }
            }
        }
    }
}

@Composable
private fun ShimmerPlayCard() {
    val alpha = rememberBoardFlowShimmerAlpha(label = "historyShimmerAlpha")
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BoardFlowSurfaceTokens.Shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(72.dp).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).background(shimmer, RoundedCornerShape(4.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(Modifier.fillMaxWidth(0.4f).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
                        Box(Modifier.width(32.dp).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayBadge(label: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun resolvedPlayerColor(colorName: String): Color? {
    val knownColors = mapOf(
        "red" to Color(0xFFE53935), "blue" to Color(0xFF1E88E5), "green" to Color(0xFF43A047),
        "yellow" to Color(0xFFFDD835), "orange" to Color(0xFFFB8C00), "purple" to Color(0xFF8E24AA),
        "white" to Color(0xFFF5F5F5), "black" to Color(0xFF212121), "pink" to Color(0xFFE91E63),
        "brown" to Color(0xFF6D4C41), "gray" to Color(0xFF757575), "grey" to Color(0xFF757575),
        "cyan" to Color(0xFF00ACC1), "teal" to Color(0xFF00897B), "lime" to Color(0xFF7CB342)
    )
    return knownColors[colorName.lowercase().trim()]
        ?: runCatching { Color(android.graphics.Color.parseColor(colorName)) }.getOrNull()
}

@Composable
private fun PlayerColorDot(color: Color, size: Int = 9) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun playerMetaText(player: PlayerResult): String? {
    val meta = buildList {
        player.color.trim()
            .takeIf { it.isNotBlank() && resolvedPlayerColor(it) == null }
            ?.let { add("Team $it") }
        player.rating
            .trim()
            .takeIf { it.isNotBlank() && it != "N/A" }
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { add("rated $it") }
        if (player.isNew) add("first play")
    }
    return meta.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun HistoryListPlayerRow(player: PlayerResult, displayName: String) {
    val scoreText = player.score.takeUnless {
        val normalized = it.trim()
        normalized.isEmpty() || normalized == "0" || normalized == "0.0"
    } ?: "—"
    val showScore = !(player.isWinner && scoreText == "—")
    val inlineColor = player.color.takeIf { it.isNotBlank() }?.let(::resolvedPlayerColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (player.isWinner) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (player.isNew) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "First play",
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f),
                    modifier = Modifier.size(11.dp)
                )
            }
            inlineColor?.let { PlayerColorDot(color = it) }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (player.isWinner) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Winner",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (showScore) {
                Text(
                    scoreText,
                    style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                    color = if (player.isWinner) MaterialTheme.colorScheme.tertiary
                    else if (scoreText == "—") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun PlayDetailsPlayerRow(
    player: PlayerResult,
    displayName: String,
    rank: Int,
    matchedPlayer: Player?,
    onPlayerTap: (Player) -> Unit
) {
    val scoreText = player.score.takeUnless {
        val normalized = it.trim()
        normalized.isEmpty() || normalized == "0" || normalized == "0.0"
    } ?: "—"
    val metaText = playerMetaText(player)
    val showScore = !(player.isWinner && scoreText == "—")
    val inlineColor = player.color.takeIf { it.isNotBlank() }?.let(::resolvedPlayerColor)

    val rowBg = if (player.isWinner)
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = rowBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                rank.toOrdinal(),
                style = MaterialTheme.typography.labelSmall,
                color = if (player.isWinner) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.width(26.dp),
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (player.isWinner) FontWeight.SemiBold else FontWeight.Normal,
                        color = Color.White,
                        maxLines = 1,
                        modifier = if (matchedPlayer != null) {
                            Modifier.clickable { onPlayerTap(matchedPlayer) }
                        } else Modifier
                    )
                    if (player.isNew) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "First play",
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    inlineColor?.let { PlayerColorDot(color = it, size = 8) }
                }
                metaText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                        maxLines = 1
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (player.isWinner) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = "Winner",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (showScore) {
                    Text(
                        scoreText,
                        style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                        fontWeight = if (player.isWinner) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (player.isWinner) MaterialTheme.colorScheme.tertiary
                                else if (scoreText == "—") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayDetailsDialog(
    play: LoggedPlay,
    thumbnailUrl: String? = null,
    game: GameItem? = null,
    players: List<Player>,
    historyPlays: List<LoggedPlay> = emptyList(),
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeletePlay: () -> Unit,
    onShareQr: () -> Unit,
    onPlayAgain: () -> Unit = {},
    onViewGame: ((GameItem) -> Unit)? = null
) {
    var viewingPlayer by remember { mutableStateOf<Player?>(null) }
    var viewingRival by remember { mutableStateOf<Player?>(null) }

    val insights = remember(play, historyPlays) {
        buildList {
            addAll(historyPlays.playInsights(play))
            if (isEmpty()) {
                val firstPlayCount = play.players.count { it.isNew }
                if (firstPlayCount > 0) {
                    add(PlayInsight(
                        if (firstPlayCount == 1) "One player marked this as a first play."
                        else "$firstPlayCount players marked this as a first play."
                    ))
                }
                val ratedCount = play.players.count {
                    it.rating.trim().toIntOrNull()?.let { rating -> rating > 0 } == true
                }
                if (ratedCount > 0) {
                    add(PlayInsight(
                        if (ratedCount == 1) "One player left a rating for this session."
                        else "$ratedCount players left ratings for this session."
                    ))
                }
                if (play.quantity > 1) {
                    add(PlayInsight("Logged as ${play.quantity} sessions."))
                }
                if (play.incomplete) {
                    add(PlayInsight("Marked as an incomplete session."))
                }
            }
        }.take(2)
    }
    AnimatedDialog(
        onDismissRequest = onDismiss,
        backdrop = {
            GameBackdrop(
                imageUrl = thumbnailUrl,
                height = 200.dp,
                titleFadeAlpha = 0.26f,
                contentFadeAlpha = 0.78f,
                baseBlur = 1.5.dp
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                item {
                    val hasThumb = !thumbnailUrl.isNullOrBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (hasThumb) {
                            AsyncImage(
                                model = thumbnailUrl,
                                contentDescription = play.gameName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .then(if (game != null && onViewGame != null) Modifier.clickable { onViewGame(game) } else Modifier)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    play.gameName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasThumb) Color.White.copy(alpha = 0.95f)
                                            else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        .then(if (game != null && onViewGame != null) Modifier.clickable { onViewGame(game) } else Modifier)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = onShareQr,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share play",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (hasThumb) Color.White.copy(alpha = 0.88f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isDeleting) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                            Text(
                                remember(play.date) {
                                    runCatching {
                                        LocalDate.parse(play.date)
                                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
                                    }.getOrDefault(play.date)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasThumb) Color.White.copy(alpha = 0.65f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val chipBg = if (hasThumb) Color.Black.copy(alpha = 0.45f)
                                             else MaterialTheme.colorScheme.surfaceVariant
                                val chipFg = if (hasThumb) Color.White.copy(alpha = 0.90f)
                                             else MaterialTheme.colorScheme.onSurfaceVariant
                                @Composable fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
                                    Surface(shape = CircleShape, color = chipBg) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = chipFg)
                                            Text(label, style = MaterialTheme.typography.labelSmall, color = chipFg)
                                        }
                                    }
                                }
                                Chip(Icons.Default.Group, "${play.players.size}")
                                if (play.durationMinutes > 0) Chip(Icons.Default.Schedule, "${play.durationMinutes} min")
                                if (play.location.isNotBlank()) Chip(Icons.Default.LocationOn, play.location)
                            }
                        }
                    }
                }

                if (insights.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            insights.forEach { insight ->
                                PlayInsightStrip(insight = insight)
                            }
                        }
                    }
                }

                item {
                    DetailSection(
                        rows = buildList {
                            if (play.quantity > 1) add("Played" to "${play.quantity} times")
                            if (play.incomplete) add("Status" to "Incomplete play")
                            if (play.comments.isNotBlank()) add("Comment" to play.comments)
                        }
                    )
                }

                item {
                    Column {
                        // Premium gradient Players header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                                )
                                Text(
                                    "Players",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), Color.Transparent)
                                        )
                                    )
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val sortedPlayers = remember(play.players) {
                            play.players.sortedWith(
                                compareByDescending<PlayerResult> {
                                    it.score.trim().toFloatOrNull() ?: Float.NEGATIVE_INFINITY
                                }.thenByDescending { it.isWinner }
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            sortedPlayers.forEachIndexed { index, player ->
                                val displayName = resolveDisplayName(player.name, players)
                                val matchedPlayer = players.firstOrNull { p ->
                                    (listOf(p.displayName) + p.aliases).any {
                                        it.lowercase().trim() == player.name.lowercase().trim()
                                    }
                                }
                                PlayDetailsPlayerRow(
                                    player = player,
                                    displayName = displayName,
                                    rank = index + 1,
                                    matchedPlayer = matchedPlayer,
                                    onPlayerTap = { viewingPlayer = it }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    PlayDetailsActionsRow(
                        onPlayAgain = onPlayAgain,
                        onEdit = onEdit,
                        onDeletePlay = onDeletePlay,
                        enabled = !isDeleting
                    )
                }
            }
        }

    viewingPlayer?.let { vp ->
        val livePlayer = players.find { it.id == vp.id }
        if (livePlayer != null) {
            val stats = remember(historyPlays, livePlayer) { historyPlays.statsForPlayer(livePlayer) }
            val rivalries = remember(historyPlays, livePlayer) { historyPlays.rivalriesForPlayer(livePlayer) }
            PlayerDetailDialog(
                player = livePlayer,
                stats = stats,
                rivalries = rivalries,
                allPlayers = players,
                onDismiss = { viewingPlayer = null },
                onEdit = { viewingPlayer = null },
                onViewRival = { rival -> viewingRival = rival }
            )
        } else {
            viewingPlayer = null
        }
    }

    viewingRival?.let { rv ->
        val liveRival = players.find { it.id == rv.id }
        if (liveRival != null) {
            val stats = remember(historyPlays, liveRival) { historyPlays.statsForPlayer(liveRival) }
            val rivalries = remember(historyPlays, liveRival) { historyPlays.rivalriesForPlayer(liveRival) }
            PlayerDetailDialog(
                player = liveRival,
                stats = stats,
                rivalries = rivalries,
                allPlayers = players,
                onDismiss = { viewingRival = null },
                onEdit = { viewingRival = null },
                onViewRival = { rival -> viewingRival = rival }
            )
        } else {
            viewingRival = null
        }
    }
}

@Composable
private fun PlayerRowDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(0.5.dp).background(Color.White.copy(alpha = 0.18f)))
        Row(
            modifier = Modifier.padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(Modifier.size(2.5.dp).background(Color.White.copy(alpha = 0.28f), CircleShape))
            }
        }
        Box(Modifier.weight(1f).height(0.5.dp).background(Color.White.copy(alpha = 0.18f)))
    }
}

@Composable
private fun PlayDetailsActionsRow(
    onPlayAgain: () -> Unit,
    onEdit: () -> Unit,
    onDeletePlay: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoardFlowTonalButton(
            onClick = onEdit,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
            )
            Spacer(Modifier.width(5.dp))
            Text("Edit", style = MaterialTheme.typography.labelLarge)
        }

        BoardFlowTonalButton(
            onClick = onPlayAgain,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp)
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f)
            )
            Spacer(Modifier.width(5.dp))
            Text("Play again", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onDeletePlay,
            enabled = enabled,
            modifier = Modifier
                .size(40.dp)
                .padding(end = 2.dp)
        ) {
            Icon(
                BoardFlowIcons.Delete,
                contentDescription = "Delete play",
                modifier = Modifier.size(18.dp),
                tint = if (enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.92f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            )
        }
    }
}

@Composable
private fun SharePlayQrDialog(
    play: LoggedPlay,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val payload = remember(play) { PlayShareSerializer.encodeAsLink(play) }
    val qrPng = remember(payload) { QrGenerator.generatePng(payload, gameName = "", margin = 2) }
    val qrBitmap = remember(qrPng) { BitmapFactory.decodeByteArray(qrPng, 0, qrPng.size) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Share play QR", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Another BoardFlow user can scan this to import the play into local history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            qrBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR code for shared play",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
            Text(play.gameName, style = MaterialTheme.typography.titleSmall)
            Text(play.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoardFlowSecondaryButton(
                    onClick = { shareQrImage(context, play.gameName, qrPng) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share image")
                }
                BoardFlowButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun EditPlayDialog(
    play: LoggedPlay,
    rosterPlayers: List<Player>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (date: String, durationMinutes: Int, location: String, comments: String, players: List<PlayerResult>) -> Unit
) {
    var date by rememberSaveable(play.id) { mutableStateOf(play.date) }
    var duration by rememberSaveable(play.id) { mutableStateOf(if (play.durationMinutes > 0) play.durationMinutes.toString() else "") }
    var location by rememberSaveable(play.id) { mutableStateOf(play.location) }
    var comments by rememberSaveable(play.id) { mutableStateOf(play.comments) }
    var editPlayers by rememberSaveable(play.id, stateSaver = PlayerResultListSaver) { mutableStateOf(play.players) }
    var collapsedPlayers by rememberSaveable(play.id) { mutableStateOf(List(play.players.size) { true }) }
    var playerRowKeys by rememberSaveable(play.id) { mutableStateOf(List(play.players.size) { java.util.UUID.randomUUID().toString() }) }
    var showDatePicker by rememberSaveable(play.id) { mutableStateOf(false) }
    var showAdvanced by rememberSaveable(play.id) { mutableStateOf(comments.isNotBlank()) }
    var nameFieldFocusIndex by remember { mutableStateOf(-1) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = BoardFlowSurfaceTokens.ContentCardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Edit Play", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(play.gameName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(
                                modifier = Modifier.weight(1.3f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EditFieldLabel("Date")
                                EditCompactTextField(
                                    value = date,
                                    onValueChange = { date = it },
                                    label = "Date",
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { showDatePicker = true }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                )
                            }
                            Column(
                                modifier = Modifier.weight(0.7f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EditFieldLabel("Duration (min)")
                                EditCompactTextField(
                                    value = duration,
                                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                                    label = "Duration",
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            EditFieldLabel("Location")
                            EditCompactTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = "Location",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        TextButton(
                            onClick = { showAdvanced = !showAdvanced },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(if (showAdvanced) "Hide options" else "More options")
                                Icon(
                                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = expandVertically() + fadeIn(tween(150)),
                            exit  = shrinkVertically() + fadeOut(tween(150))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EditFieldLabel("Notes")
                                EditCompactTextField(
                                    value = comments,
                                    onValueChange = { comments = it },
                                    label = "Notes",
                                    singleLine = false,
                                    minLines = 3,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(
                            if (editPlayers.isNotEmpty()) "Players (${editPlayers.size})" else "Players",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    BoardFlowIconButton(
                        onClick = {
                            val nextIndex = editPlayers.size
                            editPlayers = editPlayers + PlayerResult("", "0", false)
                            collapsedPlayers = collapsedPlayers + false
                            playerRowKeys = playerRowKeys + java.util.UUID.randomUUID().toString()
                            nameFieldFocusIndex = nextIndex
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add player", modifier = Modifier.size(20.dp))
                    }
                }
            }

            itemsIndexed(
                items = editPlayers,
                key = { index, _ -> playerRowKeys.getOrElse(index) { "${play.id}-$index" } }
            ) { index, player ->
                PlayerResultEditorCard(
                    player = player,
                    rosterPlayers = rosterPlayers,
                    onUpdate = { updated ->
                        editPlayers = editPlayers.toMutableList().also { it[index] = updated }
                    },
                    onRemove = {
                        editPlayers = editPlayers.toMutableList().also { it.removeAt(index) }
                        collapsedPlayers = collapsedPlayers.toMutableList().also { it.removeAt(index) }
                        playerRowKeys = playerRowKeys.toMutableList().also { it.removeAt(index) }
                    },
                    collapsed = collapsedPlayers.getOrElse(index) { false },
                    onToggleCollapsed = {
                        collapsedPlayers = collapsedPlayers.toMutableList().also { it[index] = !it[index] }
                    },
                    requestNameFocus = index == nameFieldFocusIndex,
                    onNameFocusDone = { nameFieldFocusIndex = -1 }
                )
            }

            item {
                val addPlayer = {
                    val nextIndex = editPlayers.size
                    editPlayers = editPlayers + PlayerResult("", "0", false)
                    collapsedPlayers = collapsedPlayers + false
                    playerRowKeys = playerRowKeys + java.util.UUID.randomUUID().toString()
                    nameFieldFocusIndex = nextIndex
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(BoardFlowSurfaceTokens.ContentCardShape)
                        .clickable(onClick = addPlayer),
                    shape = BoardFlowSurfaceTokens.ContentCardShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add player",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            item {
                BoardFlowButton(
                    onClick = {
                        onSave(
                            date,
                            duration.toIntOrNull() ?: 0,
                            location,
                            comments,
                            editPlayers.toList()
                        )
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun shareQrImage(context: Context, gameName: String, pngBytes: ByteArray) {
    val fileName = "${QrGenerator.safeName(gameName)}_play_share.png"
    val file = File(context.cacheDir, fileName)
    file.writeBytes(pngBytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share BoardFlow play"))
}


@Composable
private fun EditFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    )
}

@Composable
private fun EditCompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        placeholder = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        },
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier.height(if (singleLine) 52.dp else 92.dp)
    )
}

private val PlayerResultListSaver = androidx.compose.runtime.saveable.listSaver<List<PlayerResult>, List<Any>>(
    save = { players ->
        players.map { player ->
            listOf(
                player.name,
                player.score,
                player.isWinner,
                player.color,
                player.rating,
                player.isNew
            )
        }
    },
    restore = { saved ->
        saved.map { item ->
            @Suppress("UNCHECKED_CAST")
            val values = item as List<Any>
            PlayerResult(
                name = values[0] as String,
                score = values[1] as String,
                isWinner = values[2] as Boolean,
                color = values[3] as String,
                rating = values[4] as String,
                isNew = values[5] as Boolean
            )
        }
    }
)

private fun String.isLikelyLocalUuid(): Boolean {
    return Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        .matches(this)
}

@Composable
private fun DetailSection(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp)
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun resolveDisplayName(name: String, players: List<Player>): String {
    if (name.isBlank()) return name
    val lower = name.lowercase().trim()
    return players.firstOrNull { player ->
        (listOf(player.displayName) + player.aliases).any { it.lowercase().trim() == lower }
    }?.displayName ?: name
}

private fun Int.toOrdinal(): String = when {
    this % 100 in 11..13 -> "${this}th"
    this % 10 == 1 -> "${this}st"
    this % 10 == 2 -> "${this}nd"
    this % 10 == 3 -> "${this}rd"
    else -> "${this}th"
}

private fun String.matchesSavedPlayer(players: List<Player>): Boolean {
    val lower = trim().lowercase()
    if (lower.isBlank()) return false
    return players.any { player ->
        (listOf(player.displayName) + player.aliases).any { it.lowercase().trim() == lower }
    }
}


@Composable
private fun HistoryFilterSheetContent(
    sortMode: HistorySortMode,
    onSortMode: (HistorySortMode) -> Unit,
    filterDateRange: HistoryDateRange,
    onFilterDateRange: (HistoryDateRange) -> Unit,
    filterPlayer: String?,
    onFilterPlayer: (String?) -> Unit,
    players: List<Player>,
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
                        "Play history",
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
            detail = "Choose how the history list is ordered."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistorySortMode.entries.forEach { mode ->
                    BoardFlowFilterChip(
                        selected = sortMode == mode,
                        onClick = { onSortMode(mode) },
                        leadingIcon = if (sortMode == mode) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(BoardFlowSurfaceTokens.FilterIconSize)
                                )
                            }
                        } else null,
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        BoardFlowFilterSection(
            label = "Date range",
            detail = "Show plays from a specific time period."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryDateRange.entries.forEach { range ->
                    BoardFlowFilterChip(
                        selected = filterDateRange == range,
                        onClick = { onFilterDateRange(range) },
                        leadingIcon = if (filterDateRange == range) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(BoardFlowSurfaceTokens.FilterIconSize)
                                )
                            }
                        } else null,
                        label = { Text(range.label) }
                    )
                }
            }
        }

        if (players.isNotEmpty()) {
            BoardFlowFilterSection(
                label = "Player",
                detail = "Show only plays with this player."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BoardFlowFilterChip(
                        selected = filterPlayer == null,
                        onClick = { onFilterPlayer(null) },
                        label = { Text("Anyone") }
                    )
                    players.forEach { player ->
                        val isSelected = filterPlayer == player.displayName
                        BoardFlowFilterChip(
                            selected = isSelected,
                            onClick = { onFilterPlayer(if (isSelected) null else player.displayName) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(BoardFlowSurfaceTokens.FilterIconSize)
                                    )
                                }
                            } else null,
                            label = { Text(player.displayName) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
