package cz.nicolsburg.boardflow.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.R
import cz.nicolsburg.boardflow.SyncViewModel
import cz.nicolsburg.boardflow.core.navigation.AppRoutes
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.ui.collection.CollectionScreen
import cz.nicolsburg.boardflow.ui.common.BoardFlowCloseGlyph
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationKind
import cz.nicolsburg.boardflow.ui.common.BoardFlowIconButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowIcons
import cz.nicolsburg.boardflow.ui.common.boardFlowFadeIn
import cz.nicolsburg.boardflow.ui.common.boardFlowFadeOut
import cz.nicolsburg.boardflow.ui.history.HistoryScreen
import cz.nicolsburg.boardflow.ui.history.QrPlayImportScreen
import cz.nicolsburg.boardflow.ui.intro.IntroScreen
import cz.nicolsburg.boardflow.ui.review.LogPlayScreen
import cz.nicolsburg.boardflow.ui.scan.ScanScreen
import cz.nicolsburg.boardflow.ui.search.NewPlayScreen
import cz.nicolsburg.boardflow.ui.settings.SettingsScreen
import cz.nicolsburg.boardflow.ui.sync.SyncScreen

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private object AppChromeTokens {
    val HeaderHorizontalPadding = 16.dp
    val HeaderVerticalPadding = 8.dp
    val HeaderContentSpacing = 8.dp
    val HeaderLogoSize = 32.dp
    val HeaderCloseSize = 40.dp
    val BrandMetaSize = 10.sp
}

@Composable
fun BoardFlowApp(
    appViewModel: AppViewModel,
    syncViewModel: SyncViewModel,
    onRequestSignIn: () -> Unit,
    onRequestSignOut: () -> Unit,
    onRequestCsvPick: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val account by syncViewModel.account.collectAsState()
    val spreadsheetId by syncViewModel.spreadsheetId.collectAsState()
    val hasBggCredentials by syncViewModel.hasBggCredentials.collectAsState()
    val historyPlays by appViewModel.historyPlays.collectAsState()
    val players by appViewModel.players.collectAsState()
    val logPlayHasUnsavedChanges by appViewModel.logPlayHasUnsavedChanges.collectAsState()
    val logPlayPostSaveShowing by appViewModel.logPlayPostSaveShowing.collectAsState()
    val quickScanCorrectionMode by appViewModel.quickScanCorrectionMode.collectAsState()
    val pendingWidgetQuickScan by appViewModel.pendingWidgetQuickScan.collectAsState()
    val pendingWidgetOpenGameId by appViewModel.pendingWidgetOpenGameId.collectAsState()
    var startupSilentSyncRequested by rememberSaveable { mutableStateOf(false) }
    var showDiscardLogPlayConfirm by rememberSaveable { mutableStateOf(false) }
    var showIntro by rememberSaveable { mutableStateOf(!appViewModel.prefs.introSeen) }
    var collectionHeaderFilterVisible by remember { mutableStateOf(false) }
    var collectionHeaderHasActiveFilters by remember { mutableStateOf(false) }
    var collectionHeaderFilterClick by remember { mutableStateOf<(() -> Unit)?>(null) }
    var historyHeaderActionsVisible by remember { mutableStateOf(false) }
    var historyHeaderHasActiveFilters by remember { mutableStateOf(false) }
    var historyHeaderImportQrClick by remember { mutableStateOf<(() -> Unit)?>(null) }
    var historyHeaderFilterClick by remember { mutableStateOf<(() -> Unit)?>(null) }
    var activeTabLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        syncViewModel.refreshCredentialState()
        syncViewModel.loadCachedCollection()
        appViewModel.loadPlayHistory()
        appViewModel.loadCachedBggPlays()
        appViewModel.loadSessionContext()
        appViewModel.loadPlayers()
        appViewModel.loadChallenges()
    }

    LaunchedEffect(account?.name, spreadsheetId, hasBggCredentials) {
        if (!startupSilentSyncRequested && account != null && spreadsheetId.isNotBlank() && hasBggCredentials) {
            startupSilentSyncRequested = true
            syncViewModel.refreshCollectionSilentlyOnStartup(forceRefresh = true)
        }
    }

    // Bridge: keep AppViewModel's game list in sync with the rich collection so all
    // screens (including Log Play) use the same cached data.
    val collectionGames by syncViewModel.collectionGames.collectAsState()
    val syncBusy by syncViewModel.busy.collectAsState()

    LaunchedEffect(collectionGames) {
        appViewModel.updateFromCollection(collectionGames)
    }

    // Reload play data after any sync completes so historyPlays (and Stats) reflect
    // fresh data. SyncViewModel writes to Room via its own store; AppViewModel's _bggPlays
    // and _playHistory are not notified otherwise. Guard on !syncBusy prevents reads
    // mid-sync and collapses the two triggers (collectionGames change + busy→false) into
    // one effect so there is no duplicate call when both change together at sync completion.
    LaunchedEffect(collectionGames, syncBusy) {
        if (!syncBusy) {
            appViewModel.loadCachedBggPlays()
            appViewModel.loadPlayHistory()
        }
    }

    LaunchedEffect(pendingWidgetOpenGameId) {
        val gameId = pendingWidgetOpenGameId ?: return@LaunchedEffect
        appViewModel.consumeWidgetOpenPlay()
        appViewModel.setPendingHistoryFilter(gameId = gameId)
        navController.navigate(AppRoutes.HISTORY) {
            popUpTo(AppRoutes.NEW_PLAY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingWidgetQuickScan) {
        if (pendingWidgetQuickScan) {
            appViewModel.consumeWidgetQuickScan()
            appViewModel.exitQuickScanCorrectionMode()
            appViewModel.clearLogPlayFlow()
            val game = appViewModel.selectedGame
            navController.navigate(AppRoutes.scan(game?.id ?: 0, game?.name ?: "")) {
                popUpTo(AppRoutes.NEW_PLAY) { inclusive = false }
            }
        }
    }

    // Tracks how far the current screen has scrolled so the header can show a divider.
    // Accumulated from NestedScrollConnection deltas; resets on route change.
    var contentScrolled by remember { mutableFloatStateOf(0f) }
    val showHeaderDivider by remember { derivedStateOf { contentScrolled > 0f } }

    LaunchedEffect(currentRoute) {
        contentScrolled = 0f
        activeTabLabel = null
        if (currentRoute != AppRoutes.COLLECTION) {
            collectionHeaderFilterVisible = false
            collectionHeaderHasActiveFilters = false
            collectionHeaderFilterClick = null
        }
        if (currentRoute != AppRoutes.HISTORY) {
            historyHeaderActionsVisible = false
            historyHeaderHasActiveFilters = false
            historyHeaderImportQrClick = null
            historyHeaderFilterClick = null
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // consumed.y is negative when scrolling down, positive when scrolling up
                contentScrolled = (contentScrolled - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }

    // Case 6: user presses back from NewPlayScreen without selecting a game while correction mode is active.
    // Intercept the back gesture to clear the orphaned flag; the user stays on NewPlayScreen.
    BackHandler(enabled = currentRoute == AppRoutes.NEW_PLAY && quickScanCorrectionMode) {
        appViewModel.exitQuickScanCorrectionMode()
    }

    val tabs = listOf(
        BottomNavTab(AppRoutes.NEW_PLAY, "Log Play", Icons.AutoMirrored.Filled.NoteAdd),
        BottomNavTab(AppRoutes.HISTORY, "Journal", BoardFlowIcons.History),
        BottomNavTab(AppRoutes.COLLECTION, "Collection", BoardFlowIcons.Collection),
        BottomNavTab(AppRoutes.SYNC, "Sync", BoardFlowIcons.Sync),
        BottomNavTab(AppRoutes.SETTINGS, "Settings", BoardFlowIcons.Settings)
    )

    val selectedGameName = appViewModel.selectedGame?.name.orEmpty()
    val isScan = currentRoute?.startsWith("scan/") == true
    val isReview = currentRoute == AppRoutes.LOG_PLAY

    val headerSubtitle = when {
        currentRoute == AppRoutes.NEW_PLAY -> "Log a New Play"
        currentRoute == AppRoutes.HISTORY -> activeTabLabel ?: "Play Journal"
        currentRoute == AppRoutes.QR_IMPORT -> "Import Play"
        currentRoute == AppRoutes.COLLECTION -> activeTabLabel ?: "My Collection"
        currentRoute == AppRoutes.SYNC -> "Sync to Sheets"
        currentRoute == AppRoutes.SETTINGS -> activeTabLabel ?: "Settings"
        currentRoute == AppRoutes.CHALLENGES -> "Challenges"
        isScan || isReview -> selectedGameName
        else -> ""
    }

    fun leaveLogPlay() {
        appViewModel.clearLogPlayFlow()
        if (!navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false)) {
            navController.navigate(AppRoutes.NEW_PLAY) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun requestLeaveLogPlay() {
        val hasData = logPlayHasUnsavedChanges
            || appViewModel.editablePlayers.value.isNotEmpty()
            || appViewModel.extractedPlay.value != null
        if (hasData) {
            showDiscardLogPlayConfirm = true
        } else {
            leaveLogPlay()
        }
    }

    val headerBack: (() -> Unit)? = when {
        isReview && !logPlayPostSaveShowing -> ({
            requestLeaveLogPlay()
        })
        isScan -> ({
            appViewModel.clearLogPlayFlow()
            if (!navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false)) {
                navController.navigate(AppRoutes.NEW_PLAY) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        })
        else -> null
    }

    val headerAction: (@Composable () -> Unit)? =
        if (currentRoute == AppRoutes.COLLECTION && collectionHeaderFilterVisible && collectionHeaderFilterClick != null) {
            {
                CollectionHeaderFilterAction(
                    hasActiveFilters = collectionHeaderHasActiveFilters,
                    onClick = collectionHeaderFilterClick ?: {}
                )
            }
        } else if (
            currentRoute == AppRoutes.HISTORY &&
            historyHeaderActionsVisible &&
            (historyHeaderImportQrClick != null || historyHeaderFilterClick != null)
        ) {
            {
                HistoryHeaderActions(
                    hasActiveFilters = historyHeaderHasActiveFilters,
                    onImportQrClick = historyHeaderImportQrClick,
                    onFilterClick = historyHeaderFilterClick
                )
            }
        } else {
            null
        }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            AppHeader(
                subtitle = headerSubtitle,
                onNavigateBack = headerBack,
                showDivider = showHeaderDivider,
                actionContent = headerAction,
            )
        },
        bottomBar = {
            if (!isScan && !isReview) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(AppRoutes.NEW_PLAY) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showDiscardLogPlayConfirm) {
            BoardFlowConfirmationDialog(
                title = "Discard log play?",
                message = "You have unsaved play details. If you leave now, those changes will be lost.",
                confirmLabel = "Discard",
                dismissLabel = "Keep Editing",
                kind = BoardFlowConfirmationKind.DESTRUCTIVE,
                onConfirm = {
                    showDiscardLogPlayConfirm = false
                    leaveLogPlay()
                },
                onDismiss = { showDiscardLogPlayConfirm = false }
            )
        }

        NavHost(
            navController = navController,
            startDestination = AppRoutes.NEW_PLAY,
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection)
        ) {
            composable(AppRoutes.NEW_PLAY) {
                NewPlayScreen(
                    viewModel = appViewModel,
                    onGameSelected = { game ->
                        if (appViewModel.quickScanCorrectionMode.value) {
                            appViewModel.applyDetectedGameCorrection(game)
                            navController.navigate(AppRoutes.LOG_PLAY)
                        } else if (appViewModel.isOnline()) {
                            navController.navigate(AppRoutes.scan(game.id, game.name))
                        } else {
                            appViewModel.setExtractedPlayManual()
                            navController.navigate(AppRoutes.LOG_PLAY)
                        }
                    },
                    onPlayAgain = {
                        navController.navigate(AppRoutes.LOG_PLAY)
                    },
                    onScanQuick = {
                        // Starting a fresh scan exits any in-progress correction flow.
                        appViewModel.exitQuickScanCorrectionMode()
                        val game = appViewModel.selectedGame
                        navController.navigate(AppRoutes.scan(game?.id ?: 0, game?.name ?: ""))
                    }
                )
            }

            composable(AppRoutes.HISTORY) {
                HistoryScreen(
                    viewModel = appViewModel,
                    onActiveTabChange = { activeTabLabel = it },
                    onHeaderActionsStateChange = { visible, hasActiveFilters, onImportQrClick, onFilterClick ->
                        historyHeaderActionsVisible = visible
                        historyHeaderHasActiveFilters = hasActiveFilters
                        historyHeaderImportQrClick = onImportQrClick
                        historyHeaderFilterClick = onFilterClick
                    },
                    onPlayAgain = { play ->
                        appViewModel.setupPlayAgainFromPlay(play)
                        navController.navigate(AppRoutes.LOG_PLAY)
                    },
                    onPlayAgainSession = { session ->
                        appViewModel.setupPlayAgainFromSession(session.plays)
                        navController.navigate(AppRoutes.LOG_PLAY)
                    },
                    onImportQr = {
                        navController.navigate(AppRoutes.QR_IMPORT)
                    }
                )
            }

            composable(AppRoutes.COLLECTION) {
                CollectionScreen(
                    syncViewModel = syncViewModel,
                    historyPlays = historyPlays,
                    players = players,
                    onLogPlay = { gameId, gameName, thumbnailUrl ->
                        appViewModel.setupLogPlayById(gameId, gameName, thumbnailUrl)
                        if (appViewModel.isOnline()) {
                            navController.navigate(AppRoutes.scan(gameId, gameName))
                        } else {
                            appViewModel.setExtractedPlayManual()
                            navController.navigate(AppRoutes.LOG_PLAY)
                        }
                    },
                    onViewHistory = { gameId ->
                        appViewModel.setPendingHistoryFilter(gameId = gameId)
                        scope.launch {
                            navController.navigate(AppRoutes.HISTORY) {
                                popUpTo(AppRoutes.NEW_PLAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onViewHistoryPlayer = { gameId, playerName ->
                        appViewModel.setPendingHistoryFilter(gameId = gameId, playerFilter = playerName)
                        scope.launch {
                            navController.navigate(AppRoutes.HISTORY) {
                                popUpTo(AppRoutes.NEW_PLAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onViewPlayers = { playerName ->
                        appViewModel.setPendingHistoryFilter(playerFilter = playerName, showPlayersTab = true)
                        scope.launch {
                            navController.navigate(AppRoutes.HISTORY) {
                                popUpTo(AppRoutes.NEW_PLAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onHeaderFilterStateChange = { visible, hasActiveFilters, onClick ->
                        collectionHeaderFilterVisible = visible
                        collectionHeaderHasActiveFilters = hasActiveFilters
                        collectionHeaderFilterClick = onClick
                    },
                    onActiveTabChange = { activeTabLabel = it }
                )
            }

            composable(AppRoutes.SYNC) {
                SyncScreen(
                    syncViewModel = syncViewModel,
                    onPickCsv = onRequestCsvPick,
                    onSpreadsheetChanged = syncViewModel::setSpreadsheetId,
                    onSignIn = onRequestSignIn,
                    onSignOut = onRequestSignOut,
                    bggUsername = appViewModel.prefs.bggUsername,
                    bggPassword = appViewModel.prefs.bggPassword,
                    onSaveBggCredentials = { username, password ->
                        appViewModel.prefs.bggUsername = username
                        appViewModel.prefs.bggPassword = password
                        syncViewModel.refreshCredentialState()
                    }
                )
            }

            composable(AppRoutes.SETTINGS) {
                SettingsScreen(
                    viewModel = appViewModel,
                    syncViewModel = syncViewModel,
                    onSignIn = onRequestSignIn,
                    onSignOut = onRequestSignOut,
                    onActiveTabChange = { activeTabLabel = it }
                )
            }

            composable(
                route = AppRoutes.SCAN,
                arguments = listOf(
                    navArgument("gameId") { type = NavType.IntType },
                    navArgument("gameName") { type = NavType.StringType }
                )
            ) { backStack ->
                val gameName = java.net.URLDecoder.decode(
                    backStack.arguments?.getString("gameName") ?: "",
                    "UTF-8"
                )
                ScanScreen(
                    viewModel = appViewModel,
                    gameName = gameName,
                    onScoresExtracted = { navController.navigate(AppRoutes.LOG_PLAY) },
                    onDiscard = { navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false) }
                )
            }

            composable(AppRoutes.LOG_PLAY) {
                LogPlayScreen(
                    viewModel = appViewModel,
                    onPosted = {
                        appViewModel.clearLogPlayFlow()
                        navController.navigate(AppRoutes.HISTORY) {
                            popUpTo(AppRoutes.NEW_PLAY) { inclusive = false }
                        }
                    },
                    onChangeGame = { navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false) },
                    onNavigateBack = { requestLeaveLogPlay() },
                    onDiscard = { requestLeaveLogPlay() },
                    onChooseGame = {
                        appViewModel.enterQuickScanCorrectionMode()
                        navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false)
                    },
                    onPickRecommendation = { game ->
                        if (appViewModel.isOnline()) {
                            navController.navigate(AppRoutes.scan(game.id, game.name))
                        } else {
                            appViewModel.setExtractedPlayManual()
                            navController.navigate(AppRoutes.LOG_PLAY) {
                                popUpTo(AppRoutes.LOG_PLAY) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(AppRoutes.QR_IMPORT) {
                QrPlayImportScreen(
                    viewModel = appViewModel,
                    onDone = {
                        navController.popBackStack(AppRoutes.HISTORY, inclusive = false)
                    },
                    onCancel = {
                        appViewModel.clearPendingImportedPlay()
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = AppRoutes.PLAY_IMPORT,
                arguments = listOf(
                    navArgument("data") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "https://krul.cloud/boardflow/play-import?data={data}"
                    },
                    navDeepLink {
                        uriPattern = "boardflow://play-import?data={data}"
                    }
                )
            ) { backStack ->
                val rawData = backStack.arguments?.getString("data").orEmpty()
                val rawUrl = "boardflow://play-import?data=$rawData"
                QrPlayImportScreen(
                    viewModel = appViewModel,
                    initialRawData = rawUrl,
                    onDone = {
                        if (!navController.popBackStack(AppRoutes.HISTORY, inclusive = false)) {
                            navController.navigate(AppRoutes.HISTORY) {
                                popUpTo(AppRoutes.NEW_PLAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onCancel = {
                        appViewModel.clearPendingImportedPlay()
                        if (!navController.popBackStack()) {
                            navController.navigate(AppRoutes.HISTORY) {
                                popUpTo(AppRoutes.NEW_PLAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showIntro,
        enter = boardFlowFadeIn(),
        exit = boardFlowFadeOut()
    ) {
        IntroScreen(onDismiss = {
            appViewModel.prefs.introSeen = true
            showIntro = false
        })
    }
    } // end Box
}

@Composable
private fun AppHeader(
    subtitle: String,
    onNavigateBack: (() -> Unit)? = null,
    showDivider: Boolean = false,
    actionContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = AppChromeTokens.HeaderHorizontalPadding,
                    vertical = AppChromeTokens.HeaderVerticalPadding
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppChromeTokens.HeaderContentSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(AppChromeTokens.HeaderLogoSize)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("BoardFlow")
                            }
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    fontSize = AppChromeTokens.BrandMetaSize
                                )
                            ) {
                                append("by Nicolsburg")
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
                actionContent?.invoke()
                if (onNavigateBack != null) {
                    BoardFlowIconButton(onClick = onNavigateBack, modifier = Modifier.size(AppChromeTokens.HeaderCloseSize)) {
                        BoardFlowCloseGlyph(
                            contentDescription = "Back",
                            iconSize = 18.dp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showDivider,
            enter = boardFlowFadeIn(),
            exit = boardFlowFadeOut(),
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 1.dp,
            )
        }
    }
}

@Composable
private fun CollectionHeaderFilterAction(
    hasActiveFilters: Boolean,
    onClick: () -> Unit
) {
    Box {
        BoardFlowIconButton(
            onClick = onClick,
            modifier = Modifier.size(AppChromeTokens.HeaderCloseSize)
        ) {
            Icon(
                BoardFlowIcons.Filter,
                contentDescription = "Sort & filter",
                modifier = Modifier.size(20.dp),
                tint = if (hasActiveFilters) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
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
}

@Composable
private fun HistoryHeaderActions(
    hasActiveFilters: Boolean,
    onImportQrClick: (() -> Unit)?,
    onFilterClick: (() -> Unit)?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onImportQrClick != null) {
            BoardFlowIconButton(
                onClick = onImportQrClick,
                modifier = Modifier.size(AppChromeTokens.HeaderCloseSize)
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Import QR",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onFilterClick != null) {
            CollectionHeaderFilterAction(
                hasActiveFilters = hasActiveFilters,
                onClick = onFilterClick
            )
        }
    }
}
