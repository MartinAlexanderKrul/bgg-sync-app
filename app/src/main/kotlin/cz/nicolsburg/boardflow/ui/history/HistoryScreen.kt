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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import cz.nicolsburg.boardflow.model.Challenge
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.PlayerResult
import cz.nicolsburg.boardflow.model.deriveSessionHub
import cz.nicolsburg.boardflow.model.trimMemorySuffix
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
import cz.nicolsburg.boardflow.ui.challenges.ChallengesTabContent
import cz.nicolsburg.boardflow.ui.challenges.CreateChallengeDialog
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
import cz.nicolsburg.boardflow.util.toFlexibleLocalDateOrNull
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
import java.time.format.DateTimeFormatter

private data class HistoryNavState(
    val tab: HistoryTab,
    val filterGameId: Int?,
    val filterGameName: String?,
    val filterPlayers: List<String>,
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
    CHALLENGES("Challenges"),
    STATS("Stats"),
    PLAYERS("Players")
}

@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    onActiveTabChange: (String?) -> Unit = {},
    onHeaderActionsStateChange: (
        visible: Boolean,
        hasActiveFilters: Boolean,
        onImportQrClick: (() -> Unit)?,
        onFilterClick: (() -> Unit)?
    ) -> Unit = { _, _, _, _ -> },
    onPlayAgain: (cz.nicolsburg.boardflow.model.LoggedPlay) -> Unit = {},
    onPlayAgainSession: (cz.nicolsburg.boardflow.model.SessionHub) -> Unit = {},
    onImportQr: () -> Unit = {}
) {
    val historyPlays by viewModel.historyPlays.collectAsState()
    val collection by viewModel.collection.collectAsState()
    val collectionItems by viewModel.collectionItems.collectAsState()
    val historyThumbnailCache by viewModel.historyThumbnailCache.collectAsState()
    val bggPlays by viewModel.bggPlays.collectAsState()
    val bggLoading by viewModel.bggPlaysLoading.collectAsState()
    val bggError by viewModel.bggPlaysError.collectAsState()
    val players by viewModel.players.collectAsState()
    val visiblePlayers by viewModel.visiblePlayers.collectAsState()
    val statsPlayScope by viewModel.statsPlayScope.collectAsState()
    val playStats by viewModel.playStats.collectAsState()
    val statsTimeRange by viewModel.statsTimeRange.collectAsState()
    val deletingPlayId by viewModel.deletingBggPlayId.collectAsState()
    val bggDeleteError by viewModel.bggDeleteError.collectAsState()
    val bggEditError by viewModel.bggEditError.collectAsState()
    val editPlayLoading by viewModel.editPlayLoading.collectAsState()
    val postingPlayId by viewModel.postingPlayId.collectAsState()
    val bggPlaysCacheAgeMinutes by viewModel.bggPlaysCacheAgeMinutes.collectAsState()
    val customMoods by viewModel.customMoods.collectAsState()
    val moodUsageOrder by viewModel.moodUsageOrder.collectAsState()
    val chroniclePendingPlayIds by viewModel.chroniclePendingPlayIds.collectAsState()
    val chronicleEnabled by viewModel.chronicleEnabled.collectAsState()
    val challenges by viewModel.challenges.collectAsState()
    val challengeProgressList = remember(challenges, historyPlays, players, collectionItems) {
        viewModel.getChallengeProgressList()
    }
    var showCreateChallengeDialog by rememberSaveable { mutableStateOf(false) }
    var editingChallenge by remember { mutableStateOf<Challenge?>(null) }

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
    var sessionHubAnchor by remember { mutableStateOf<LoggedPlay?>(null) }
    var sessionHubTitle by remember { mutableStateOf<String?>(null) }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    var editingPlay by remember { mutableStateOf<LoggedPlay?>(null) }
    var playToShare by remember { mutableStateOf<LoggedPlay?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bggDeleteError) {
        if (bggDeleteError != null) {
            deleteError = bggDeleteError
            viewModel.clearBggDeleteError()
        }
    }
    LaunchedEffect(bggEditError) {
        if (bggEditError != null) {
            editError = bggEditError
            viewModel.clearBggEditError()
        }
    }
    LaunchedEffect(sessionHubAnchor?.sessionId) {
        sessionHubTitle = viewModel.getSessionTitle(sessionHubAnchor?.sessionId)
    }
    var navHistory by remember { mutableStateOf(listOf<HistoryNavState>()) }
    var restoredViewingPlayerId by remember { mutableStateOf<String?>(null) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var filterGameName by rememberSaveable { mutableStateOf<String?>(null) }
    val backdropUrl by remember(filterGameId, collection, historyThumbnailCache) {
        derivedStateOf { filterGameId?.let { id -> collection.firstOrNull { it.id == id }?.thumbnailUrl ?: historyThumbnailCache[id] } }
    }
    val selectedPlayThumbnail by remember(selectedPlay, collection, historyThumbnailCache) {
        derivedStateOf { selectedPlay?.gameId?.let { id -> collection.firstOrNull { it.id == id }?.thumbnailUrl ?: historyThumbnailCache[id] } }
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
    var filterPlayers by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val controlsVisibleState = remember { mutableStateOf(true) }
    var controlsVisible by controlsVisibleState
    val playsListState = rememberLazyListState()
    val statsListState = rememberLazyListState()
    val playersListState = rememberLazyListState()
    val challengesListState = rememberLazyListState()

    val hasActiveFilters = sortMode != HistorySortMode.DATE_DESC ||
        filterDateRange != HistoryDateRange.ALL ||
        filterPlayers.isNotEmpty() ||
        filterGameId != null

    val filteredPlays = remember(historyPlays, searchQuery, filterGameId, sortMode, filterDateRange, filterPlayers, players) {
        var result = historyPlays

        filterGameId?.let { id -> result = result.filter { it.gameId == id } }

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            result = result.filter {
                it.gameName.lowercase().contains(query) ||
                    it.date.matchesHistorySearchQuery(query) ||
                    it.location.lowercase().contains(query) ||
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

        if (filterPlayers.isNotEmpty()) {
            result = result.filter { play ->
                filterPlayers.any { playerDisplayName ->
                    if (playerDisplayName == "Unknown") {
                        play.players.any { it.name.isNotBlank() && !it.name.matchesSavedPlayer(players) }
                    } else {
                        val player = players.find { it.displayName == playerDisplayName }
                        val names = if (player != null) {
                            (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
                        } else {
                            listOf(playerDisplayName.lowercase().trim())
                        }
                        play.players.any { it.name.lowercase().trim() in names }
                    }
                }
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
    val visibleTabs = HistoryTab.entries
    val showHeaderActions = activeTab == HistoryTab.PLAYS && !controlsVisible
    var showAddPlayerDialog by rememberSaveable { mutableStateOf(false) }
    var editingPlayer by remember { mutableStateOf<cz.nicolsburg.boardflow.model.Player?>(null) }

    BackHandler(enabled = navHistory.isNotEmpty()) {
        val prev = navHistory.last()
        navHistory = navHistory.dropLast(1)
        activeTab = prev.tab
        filterGameId = prev.filterGameId
        filterGameName = prev.filterGameName
        filterPlayers = prev.filterPlayers
        searchQuery = prev.searchQuery
        selectedPlay = prev.selectedPlayId?.let { id -> historyPlays.find { it.id == id } }
        selectedGame = prev.selectedGameObjectId?.let { objId -> collectionItems.firstOrNull { it.objectId == objId } }
        restoredViewingPlayerId = prev.viewingPlayerId
    }

    BackHandler(enabled = activeTab == HistoryTab.CHALLENGES) {
        activeTab = HistoryTab.PLAYS
    }

    LaunchedEffect(activeTab) {
        controlsVisible = true
        if (activeTab == HistoryTab.PLAYS) playsListState.scrollToItem(0)
    }

    // Show controls when list reaches the very top (e.g. after filter/search resets position).
    LaunchedEffect(activeTab, playsListState, statsListState, playersListState, challengesListState) {
        snapshotFlow {
            when (activeTab) {
                HistoryTab.PLAYS -> playsListState.firstVisibleItemIndex == 0 && playsListState.firstVisibleItemScrollOffset < 8
                HistoryTab.STATS -> statsListState.firstVisibleItemIndex == 0 && statsListState.firstVisibleItemScrollOffset < 8
                HistoryTab.PLAYERS -> playersListState.firstVisibleItemIndex == 0 && playersListState.firstVisibleItemScrollOffset < 8
                HistoryTab.CHALLENGES -> challengesListState.firstVisibleItemIndex == 0 && challengesListState.firstVisibleItemScrollOffset < 8
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

    LaunchedEffect(searchQuery, filterGameId, sortMode, filterDateRange, filterPlayers) {
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
            nav.playerFilter?.let { filterPlayers = listOf(it) }
        }
        viewModel.consumePendingHistoryFilter()
    }

    LaunchedEffect(controlsVisible, activeTab) {
        onActiveTabChange(if (controlsVisible) null else activeTab.label)
    }

    LaunchedEffect(showHeaderActions, hasActiveFilters, activeTab) {
        onHeaderActionsStateChange(
            showHeaderActions,
            hasActiveFilters,
            if (showHeaderActions) onImportQr else null,
            if (showHeaderActions) ({ showFilters = true }) else null
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onHeaderActionsStateChange(false, false, null, null)
            onActiveTabChange(null)
        }
    }

    playToDelete?.let { play ->
        val isRemotePlay = play.postedToBgg
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
                        play = play,
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
            onOpenSessionHub = {
                selectedPlay = null
                sessionHubAnchor = play
            },
            onEditPlayer = { editingPlayer = it; selectedPlay = null },
            onViewGame = { g -> selectedGame = g },
            customMoods = customMoods,
            moodUsageOrder = moodUsageOrder,
            isChroniclePending = chroniclePendingPlayIds.contains(play.id),
            chronicleEnabled = chronicleEnabled,
            onEnsureChronicle = {
                viewModel.ensureChronicleForPlay(play) { savedMemory ->
                    selectedPlay = selectedPlay?.takeIf { it.id == play.id }?.copy(memory = savedMemory)
                }
            },
            onSaveMemory = { memory ->
                val presets = listOf("Chill", "Intense", "Chaotic", "Cozy", "Competitive", "Legendary")
                viewModel.savePlayMemory(play, memory, presets) { savedMemory ->
                    selectedPlay = selectedPlay?.takeIf { it.id == play.id }?.copy(memory = savedMemory)
                }
                selectedPlay = selectedPlay?.copy(memory = memory) ?: play.copy(memory = memory)
            }
        )
    }

    sessionHubAnchor?.let { anchor ->
        SessionHubDialog(
            session = historyPlays.deriveSessionHub(anchor, sessionHubTitle),
            players = players,
            onDismiss = { sessionHubAnchor = null },
            onRenameSession = { sessionId, title ->
                viewModel.renameSession(
                    sessionId = sessionId,
                    newTitle = title,
                    onSuccess = { savedTitle -> sessionHubTitle = savedTitle.ifBlank { null } }
                )
            },
            onOpenPlay = { play ->
                sessionHubAnchor = null
                selectedPlay = play
            },
            onPlayAgain = { session ->
                sessionHubAnchor = null
                onPlayAgainSession(session)
            }
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
                        filterPlayers = filterPlayers, searchQuery = searchQuery,
                        selectedPlayId = selectedPlay?.id, selectedGameObjectId = g.objectId
                    )
                    selectedGame = null
                    selectedPlay = null
                    activeTab = HistoryTab.PLAYS
                    filterGameId = gameObjectId
                    filterGameName = g.name
                    filterPlayers = emptyList()
                    searchQuery = ""
                }
            },
            onViewHistoryPlayer = { _, playerName ->
                if (gameObjectId != null) {
                    navHistory = navHistory + HistoryNavState(
                        tab = activeTab, filterGameId = filterGameId, filterGameName = filterGameName,
                        filterPlayers = filterPlayers, searchQuery = searchQuery,
                        selectedPlayId = selectedPlay?.id, selectedGameObjectId = g.objectId
                    )
                    selectedGame = null
                    selectedPlay = null
                    activeTab = HistoryTab.PLAYS
                    filterGameId = gameObjectId
                    filterGameName = g.name
                    filterPlayers = listOf(playerName)
                    searchQuery = ""
                }
            },
            onViewPlayers = { playerName ->
                navHistory = navHistory + HistoryNavState(
                    tab = activeTab, filterGameId = filterGameId, filterGameName = filterGameName,
                    filterPlayers = filterPlayers, searchQuery = searchQuery,
                    selectedPlayId = selectedPlay?.id, selectedGameObjectId = g.objectId
                )
                selectedGame = null
                selectedPlay = null
                activeTab = HistoryTab.PLAYS
                filterPlayers = listOf(playerName)
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
                onToggleHidden = { viewModel.updatePlayerHidden(livePlayer.id, it) },
                onDelete = { viewModel.deletePlayer(livePlayer.id); editingPlayer = null }
            )
        } else {
            editingPlayer = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            when (activeTab) {
                HistoryTab.PLAYERS -> FloatingActionButton(onClick = { showAddPlayerDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add player")
                }
                HistoryTab.CHALLENGES -> FloatingActionButton(onClick = { showCreateChallengeDialog = true }) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = "New challenge")
                }
                else -> {}
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
                    tabCount = visibleTabs.size,
                    selectedIndex = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                    onNavigate = { activeTab = visibleTabs[it] }
                )
        ) {
            if (activeTab != HistoryTab.PLAYS) {
                BoardFlowAnimatedVisibility(visible = controlsVisible) {
                    ScreenTabRow(
                        tabs = visibleTabs.map { it.label },
                        selectedIndex = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                        onTabSelected = { navHistory = emptyList(); activeTab = visibleTabs[it] }
                    )
                }
            }

            when (activeTab) {
                HistoryTab.PLAYS -> Column(modifier = Modifier.fillMaxSize()) {
                    BoardFlowAnimatedVisibility(visible = controlsVisible) {
                        Column {
                            ScreenTabRow(
                                tabs = visibleTabs.map { it.label },
                                selectedIndex = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                                onTabSelected = { navHistory = emptyList(); activeTab = visibleTabs[it] }
                            )

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

                            if (hasActiveFilters) {
                                val playerLabel = when {
                                    filterPlayers.isEmpty() -> null
                                    filterPlayers.size == 1 -> filterPlayers.first()
                                    filterPlayers.size == 2 -> filterPlayers.joinToString(" + ")
                                    else -> "${filterPlayers.take(2).joinToString(", ")} +${filterPlayers.size - 2}"
                                }
                                val label = buildList {
                                    filterGameName?.let { add(it) }
                                    playerLabel?.let { add(it) }
                                    if (filterDateRange != HistoryDateRange.ALL) add(filterDateRange.label)
                                    if (sortMode != HistorySortMode.DATE_DESC) add(sortMode.label)
                                }.joinToString(" • ")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = BoardFlowSurfaceTokens.Shape,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, end = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.FilterAlt,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    sortMode = HistorySortMode.DATE_DESC
                                                    filterDateRange = HistoryDateRange.ALL
                                                    filterGameId = null
                                                    filterGameName = null
                                                    filterPlayers = emptyList()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear filters",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

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
                        filterPlayers = filterPlayers,
                        onFilterPlayers = { filterPlayers = it },
                        players = players,
                        hasActiveFilters = hasActiveFilters,
                        onReset = {
                            sortMode = HistorySortMode.DATE_DESC
                            filterDateRange = HistoryDateRange.ALL
                            filterPlayers = emptyList()
                            filterGameId = null
                            filterGameName = null
                        }
                    )
                }
            }

                    val collectionIds = remember(collection) { collection.map { it.id }.toSet() }
                    val activeChallenges = challengeProgressList.filter { it.isActive }
                    PlaysContent(
                        plays = filteredPlays,
                        players = players,
                        loading = bggLoading,
                        error = bggError,
                        hasBggUsername = viewModel.prefs.bggUsername.isNotBlank(),
                        onOpenPlay = { selectedPlay = it },
                        onRefresh = ::triggerBggPlaysRefresh,
                        collectionIds = collectionIds,
                        listState = playsListState,
                        hasActiveFilters = hasActiveFilters,
                        onResetFilters = {
                            sortMode = HistorySortMode.DATE_DESC
                            filterDateRange = HistoryDateRange.ALL
                            filterPlayers = emptyList()
                            filterGameId = null
                            filterGameName = null
                            searchQuery = ""
                        },
                        activeChallenges = activeChallenges,
                        onChallengesClick = { activeTab = HistoryTab.CHALLENGES },
                        pendingPlays = localPendingPlays,
                        postingPlayId = postingPlayId,
                        syncingUnpostedPlays = syncingUnpostedPlays,
                        onPostPlay = viewModel::postSinglePlay,
                        onPostAll = viewModel::syncUnpostedPlays,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                HistoryTab.STATS -> StatsContent(
                    stats = playStats,
                    statsTimeRange = statsTimeRange,
                    onTimeRangeChange = viewModel::setStatsTimeRange,
                    listState = statsListState,
                    modifier = Modifier.fillMaxSize(),
                    onGameTapped = { gameId, gameName ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayers, searchQuery)
                        activeTab = HistoryTab.PLAYS
                        filterGameId = gameId
                        filterGameName = gameName
                        filterPlayers = emptyList()
                        searchQuery = ""
                    },
                    onPlayerTapped = { playerName ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayers, searchQuery)
                        activeTab = HistoryTab.PLAYS
                        filterPlayers = listOf(playerName)
                        filterGameId = null
                        filterGameName = null
                        searchQuery = ""
                    },
                    onPlaysFilter = { recentDays ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayers, searchQuery)
                        activeTab = HistoryTab.PLAYS
                        filterDateRange = when {
                            recentDays <= 7  -> HistoryDateRange.THIS_WEEK
                            recentDays <= 31 -> HistoryDateRange.THIS_MONTH
                            else             -> HistoryDateRange.THIS_YEAR
                        }
                        filterGameId = null
                        filterGameName = null
                        filterPlayers = emptyList()
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
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayers, searchQuery, viewingPlayerId = sourcePlayerId.ifBlank { null })
                        activeTab = HistoryTab.PLAYS
                        filterPlayers = listOf(playerName)
                    },
                    onViewPlayerGame = { gameId, gameName, sourcePlayerId ->
                        navHistory = navHistory + HistoryNavState(activeTab, filterGameId, filterGameName, filterPlayers, searchQuery, viewingPlayerId = sourcePlayerId.ifBlank { null })
                        activeTab = HistoryTab.PLAYS
                        filterGameId = gameId
                        filterGameName = gameName
                        searchQuery = ""
                    },
                    modifier = Modifier.fillMaxSize()
                )
                HistoryTab.CHALLENGES -> ChallengesTabContent(
                    progressList = challengeProgressList,
                    onEdit = { editingChallenge = it },
                    onPause = { viewModel.pauseChallenge(it) },
                    onResume = { viewModel.resumeChallenge(it) },
                    onArchive = { viewModel.archiveChallenge(it) },
                    onRestore = { viewModel.restoreChallenge(it) },
                    onDelete = { viewModel.deleteChallenge(it) },
                    listState = challengesListState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (showCreateChallengeDialog) {
                CreateChallengeDialog(
                    collectionItems = collectionItems,
                    players = visiblePlayers,
                    onDismiss = { showCreateChallengeDialog = false },
                    onSave = { challenge ->
                        viewModel.addChallenge(challenge)
                        showCreateChallengeDialog = false
                    }
                )
            }

            editingChallenge?.let { challenge ->
                CreateChallengeDialog(
                    collectionItems = collectionItems,
                    players = visiblePlayers,
                    initialChallenge = challenge,
                    onDismiss = { editingChallenge = null },
                    onSave = { updated ->
                        viewModel.updateChallenge(updated)
                        editingChallenge = null
                    }
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
    collectionIds: Set<Int> = emptySet(),
    listState: LazyListState = rememberLazyListState(),
    hasActiveFilters: Boolean = false,
    onResetFilters: () -> Unit = {},
    activeChallenges: List<cz.nicolsburg.boardflow.model.ChallengeProgress> = emptyList(),
    onChallengesClick: () -> Unit = {},
    pendingPlays: List<LoggedPlay> = emptyList(),
    postingPlayId: String? = null,
    syncingUnpostedPlays: Boolean = false,
    onPostPlay: (String) -> Unit = {},
    onPostAll: () -> Unit = {},
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
                if (activeChallenges.isNotEmpty()) {
                    item(key = "challenges") {
                        ChallengesEntry(
                            progressList = activeChallenges,
                            onClick = onChallengesClick
                        )
                    }
                }
                if (pendingPlays.isNotEmpty()) {
                    item(key = "pending") {
                        PendingPlaysCard(
                            plays = pendingPlays,
                            postingPlayId = postingPlayId,
                            syncingUnpostedPlays = syncingUnpostedPlays,
                            onPostPlay = onPostPlay,
                            onPostAll = onPostAll
                        )
                    }
                }
                items(plays, key = { it.id }) { play ->
                    PlayHistoryCard(
                        play = play,
                        players = players,
                        isInCollection = play.gameId in collectionIds,
                        onClick = { onOpenPlay(play) },
                        modifier = Modifier.animateItem()
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
    isInCollection: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                if (!isInCollection) {
                    PlayBadge("not in your collection", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    onOpenSessionHub: () -> Unit = {},
    onEditPlayer: (Player) -> Unit = {},
    onViewGame: ((GameItem) -> Unit)? = null,
    onSaveMemory: (cz.nicolsburg.boardflow.model.SessionMemory) -> Unit = {},
    onEnsureChronicle: () -> Unit = {},
    customMoods: List<String> = emptyList(),
    moodUsageOrder: List<String> = emptyList(),
    isChroniclePending: Boolean = false,
    chronicleEnabled: Boolean = true
) {
    var viewingPlayer by remember { mutableStateOf<Player?>(null) }
    var viewingRival by remember { mutableStateOf<Player?>(null) }
    val memory = play.memory
    val chronicle = memory?.chronicleLine?.takeIf { chronicleEnabled && it.isNotBlank() }
    val hasMemoryInput = memory?.let { it.moods.isNotEmpty() || it.quote.isNotBlank() } == true

    LaunchedEffect(play.id, chronicleEnabled) {
        if (chronicleEnabled && chronicle == null && hasMemoryInput) onEnsureChronicle()
    }

    val sessionHub = remember(play, historyPlays) { historyPlays.deriveSessionHub(play) }
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

                item {
                    SessionHubPreviewCard(
                        sessionHub = sessionHub,
                        memory = memory,
                        chronicle = chronicle,
                        isChroniclePending = chronicleEnabled && hasMemoryInput && isChroniclePending,
                        onClick = onOpenSessionHub
                    )
                }

                item {
                    val visibleComments = remember(play.comments) {
                        play.comments.trimMemorySuffix().takeIf { it.isNotBlank() }
                    }
                    val detailRows = buildList {
                        if (play.quantity > 1) add("Played" to "${play.quantity} times")
                        if (play.incomplete) add("Status" to "Incomplete play")
                        if (visibleComments != null) add("Comment" to visibleComments)
                    }
                    if (detailRows.isNotEmpty()) {
                        DetailSection(rows = detailRows)
                    }
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
                    PlayMemorySection(
                        memory = play.memory,
                        customMoods = customMoods,
                        moodUsageOrder = moodUsageOrder,
                        isChroniclePending = isChroniclePending,
                        onSaveMemory = onSaveMemory
                    )
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
                onEdit = { viewingPlayer = null; onEditPlayer(livePlayer) },
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
                onEdit = { viewingRival = null; onEditPlayer(liveRival) },
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
    var comments by rememberSaveable(play.id) { mutableStateOf(play.comments.trimMemorySuffix()) }
    var editPlayers by rememberSaveable(play.id, stateSaver = PlayerResultListSaver) { mutableStateOf(play.players) }
    var collapsedPlayers by rememberSaveable(play.id) { mutableStateOf(List(play.players.size) { true }) }
    var playerRowKeys by rememberSaveable(play.id) { mutableStateOf(List(play.players.size) { java.util.UUID.randomUUID().toString() }) }
    var showDatePicker by rememberSaveable(play.id) { mutableStateOf(false) }
    var showAdvanced by rememberSaveable(play.id) { mutableStateOf(comments.isNotBlank()) }
    var nameFieldFocusIndex by remember { mutableStateOf(-1) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                date.toFlexibleLocalDateOrNull()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
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
private fun PlayMemorySection(
    memory: cz.nicolsburg.boardflow.model.SessionMemory?,
    onSaveMemory: (cz.nicolsburg.boardflow.model.SessionMemory) -> Unit,
    customMoods: List<String> = emptyList(),
    moodUsageOrder: List<String> = emptyList(),
    isChroniclePending: Boolean = false
) {
    val presetMoods = listOf("Chill", "Intense", "Chaotic", "Cozy", "Competitive", "Legendary")
    val moods = remember(customMoods, moodUsageOrder) {
        val all = (presetMoods + customMoods.filter { c -> presetMoods.none { it.equals(c, ignoreCase = true) } }).distinct()
        val orderIndex = { mood: String ->
            val idx = moodUsageOrder.indexOfFirst { it.equals(mood, ignoreCase = true) }
            if (idx == -1) Int.MAX_VALUE else idx
        }
        all.sortedBy { orderIndex(it) }
    }

    val hasMemory = memory?.run {
        moods.isNotEmpty() || note.isNotBlank() || quote.isNotBlank() || chronicleLine.isNotBlank()
    } == true
    var isEditing by remember { mutableStateOf(false) }
    var draftMoods by remember(memory) { mutableStateOf(memory?.moods ?: emptyList()) }
    var draftCustomText by remember { mutableStateOf("") }
    var draftQuote by remember(memory) { mutableStateOf(memory?.quote ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))))
            )
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    BoardFlowIcons.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Text(
                    "Highlights",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), Color.Transparent)))
            )
        }

        if (!isEditing) {
            if (hasMemory) {
                MemoryDisplay(memory = memory!!, onEdit = { isEditing = true })
            } else {
                MemoryEmptyState(onAdd = { isEditing = true })
            }
        } else {
            MemoryEditor(
                moods = moods,
                draftMoods = draftMoods,
                draftCustomText = draftCustomText,
                draftQuote = draftQuote,
                onToggleMood = { mood ->
                    draftMoods = if (draftMoods.contains(mood)) draftMoods - mood else draftMoods + mood
                },
                onCustomTextChange = { draftCustomText = it },
                onAddCustomMood = {
                    val mood = draftCustomText.trim().lowercase().replaceFirstChar { it.uppercaseChar() }
                    if (mood.isNotBlank()) {
                        draftMoods = (draftMoods + mood).distinct()
                        draftCustomText = ""
                    }
                },
                onQuoteChange = { draftQuote = it },
                onSave = {
                    val extra = draftCustomText.trim().lowercase().replaceFirstChar { it.uppercaseChar() }
                    val finalMoods = (draftMoods + if (extra.isNotBlank()) listOf(extra) else emptyList()).distinct()
                    onSaveMemory(cz.nicolsburg.boardflow.model.SessionMemory(moods = finalMoods, quote = draftQuote.trim()))
                    draftCustomText = ""
                    isEditing = false
                },
                onCancel = {
                    draftMoods = memory?.moods ?: emptyList()
                    draftCustomText = ""
                    draftQuote = memory?.quote ?: ""
                    isEditing = false
                }
            )
        }
    }
}

@Composable
private fun MemoryEmptyState(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onAdd)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "Nothing written yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            Text(
                "Add highlights",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun MemoryDisplay(
    memory: cz.nicolsburg.boardflow.model.SessionMemory,
    onEdit: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (memory.moods.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        memory.moods.forEach { MemoryChip(label = it, highlighted = true) }
                    }
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit memory",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            if (memory.note.isNotBlank()) {
                Text(
                    memory.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            if (memory.quote.isNotBlank()) {
                Text(
                    "— ${memory.quote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun SessionHubPreviewCard(
    sessionHub: cz.nicolsburg.boardflow.model.SessionHub,
    memory: cz.nicolsburg.boardflow.model.SessionMemory?,
    chronicle: String?,
    isChroniclePending: Boolean,
    onClick: () -> Unit
) {
    val title = remember(sessionHub.date) {
        runCatching {
            val dow = LocalDate.parse(sessionHub.date).dayOfWeek
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            "${dow}'s session"
        }.getOrDefault("Session")
    }
    val subtitle = buildString {
        append("${sessionHub.totalLoggedPlays} ${if (sessionHub.totalLoggedPlays == 1) "game" else "games"}")
        append(" · ${sessionHub.uniquePlayerNames.size} ${if (sessionHub.uniquePlayerNames.size == 1) "player" else "players"}")
        sessionHub.location.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }
    val highlightLine = chronicle
        ?: memory?.quote?.trim()?.takeIf { it.isNotBlank() }
        ?: if (isChroniclePending) "Composing the session chronicle..." else null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }

            highlightLine?.let { line ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.AutoStories,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(13.dp),
                        tint = Color(0xFFF0A500).copy(alpha = 0.72f)
                    )
                    Text(
                        line.let { if (chronicle == null && !isChroniclePending) "“$it”" else it },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isChroniclePending) 0.56f else 0.78f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryChip(label: String, highlighted: Boolean) {
    val bg = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    val fg = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer
             else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Surface(shape = CircleShape, color = bg) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MoodExpandChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp)
    )
}

@Composable
private fun MemorySelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.tertiaryContainer
             else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onTertiaryContainer
             else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val border = if (selected) null
                 else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    Surface(
        shape = CircleShape,
        color = bg,
        border = border,
        onClick = onClick
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun MemoryEditor(
    moods: List<String>,
    draftMoods: List<String>,
    draftCustomText: String,
    draftQuote: String,
    onToggleMood: (String) -> Unit,
    onCustomTextChange: (String) -> Unit,
    onAddCustomMood: () -> Unit,
    onQuoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val extraDraftMoods = draftMoods.filter { d -> moods.none { it.equals(d, ignoreCase = true) } }
            val allMoods = moods + extraDraftMoods
            var moodsExpanded by remember { mutableStateOf(false) }
            val collapsedLimit = 8
            val selectedMoods = allMoods.filter { m -> draftMoods.any { it.equals(m, ignoreCase = true) } }
            val unselectedMoods = allMoods.filter { m -> draftMoods.none { it.equals(m, ignoreCase = true) } }
            val visibleUnselected = if (moodsExpanded) unselectedMoods
                                    else unselectedMoods.take((collapsedLimit - selectedMoods.size).coerceAtLeast(0))
            val hiddenCount = unselectedMoods.size - visibleUnselected.size
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    (selectedMoods + visibleUnselected).forEach { mood ->
                        MemorySelectableChip(
                            label = mood,
                            selected = draftMoods.any { it.equals(mood, ignoreCase = true) },
                            onClick = { onToggleMood(mood) }
                        )
                    }
                }
                if (hiddenCount > 0) {
                    MoodExpandChip(label = "+$hiddenCount more", onClick = { moodsExpanded = true })
                } else if (moodsExpanded && unselectedMoods.size > (collapsedLimit - selectedMoods.size).coerceAtLeast(0)) {
                    MoodExpandChip(label = "Show less", onClick = { moodsExpanded = false })
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = draftCustomText,
                    onValueChange = { if (it.length <= 40) onCustomTextChange(it) },
                    placeholder = {
                        Text(
                            "Add another mood…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardActions = KeyboardActions(onDone = { if (draftCustomText.isNotBlank()) onAddCustomMood() }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                IconButton(
                    onClick = onAddCustomMood,
                    enabled = draftCustomText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add mood",
                        tint = if (draftCustomText.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            OutlinedTextField(
                value = draftQuote,
                onValueChange = { if (it.length <= 100) onQuoteChange(it) },
                placeholder = {
                    Text(
                        "Any memorable quote?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                BoardFlowTonalButton(onClick = onSave) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Save highlights", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
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

private fun String.matchesHistorySearchQuery(query: String): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return false

    val normalizedSeparatorsQuery = normalizedQuery
        .replace("\\s+".toRegex(), "")
        .replace('.', '-')
        .replace('/', '-')

    if (lowercase().contains(normalizedQuery) || lowercase().contains(normalizedSeparatorsQuery)) {
        return true
    }

    val parsedDate = toFlexibleLocalDateOrNull() ?: return false
    val candidates = listOf(
        parsedDate.toString(),
        parsedDate.format(DateTimeFormatter.ofPattern("d.M.yyyy")),
        parsedDate.format(DateTimeFormatter.ofPattern("d.M.")),
        parsedDate.format(DateTimeFormatter.ofPattern("d.M")),
        parsedDate.format(DateTimeFormatter.ofPattern("d/M/yyyy")),
        parsedDate.format(DateTimeFormatter.ofPattern("d/M/yy")),
        parsedDate.format(DateTimeFormatter.ofPattern("d/M")),
        parsedDate.format(DateTimeFormatter.ofPattern("M/d/yyyy")),
        parsedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")).lowercase()
    )

    return candidates.any { candidate ->
        val lowered = candidate.lowercase()
        lowered.contains(normalizedQuery) ||
            lowered.replace(" ", "").contains(normalizedQuery.replace(" ", "")) ||
            lowered.replace('.', '-').replace('/', '-').contains(normalizedSeparatorsQuery)
    }
}


@Composable
private fun HistoryFilterSheetContent(
    sortMode: HistorySortMode,
    onSortMode: (HistorySortMode) -> Unit,
    filterDateRange: HistoryDateRange,
    onFilterDateRange: (HistoryDateRange) -> Unit,
    filterPlayers: List<String>,
    onFilterPlayers: (List<String>) -> Unit,
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
                label = "Players",
                detail = "Show plays with any of the selected players."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BoardFlowFilterChip(
                        selected = filterPlayers.isEmpty(),
                        onClick = { onFilterPlayers(emptyList()) },
                        label = { Text("Anyone") }
                    )
                    players.forEach { player ->
                        val isSelected = player.displayName in filterPlayers
                        BoardFlowFilterChip(
                            selected = isSelected,
                            onClick = {
                                onFilterPlayers(
                                    if (isSelected) {
                                        filterPlayers - player.displayName
                                    } else {
                                        filterPlayers + player.displayName
                                    }
                                )
                            },
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

@Composable
private fun ChallengesEntry(
    progressList: List<cz.nicolsburg.boardflow.model.ChallengeProgress>,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Challenges",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (progressList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    progressList.take(3).forEach { progress ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    progress.challenge.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${progress.currentCount}/${progress.goalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
