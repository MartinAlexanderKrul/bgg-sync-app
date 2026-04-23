package cz.nicolsburg.boardflow.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.R
import cz.nicolsburg.boardflow.SyncViewModel
import cz.nicolsburg.boardflow.core.navigation.AppRoutes
import cz.nicolsburg.boardflow.ui.collection.CollectionScreen
import cz.nicolsburg.boardflow.ui.common.BoardFlowCloseGlyph
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationKind
import cz.nicolsburg.boardflow.ui.common.BoardFlowIconButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowIcons
import cz.nicolsburg.boardflow.ui.history.HistoryScreen
import cz.nicolsburg.boardflow.ui.players.PlayersScreen
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val account by syncViewModel.account.collectAsState()
    val spreadsheetId by syncViewModel.spreadsheetId.collectAsState()
    val hasBggCredentials by syncViewModel.hasBggCredentials.collectAsState()
    val logPlayHasUnsavedChanges by appViewModel.logPlayHasUnsavedChanges.collectAsState()
    var startupSilentSyncRequested by rememberSaveable { mutableStateOf(false) }
    var showDiscardLogPlayConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appViewModel.syncUnpostedPlays()
        syncViewModel.refreshCredentialState()
        syncViewModel.loadCachedCollection()
        appViewModel.loadPlayHistory()
        appViewModel.loadCachedBggPlays()
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
    LaunchedEffect(collectionGames) {
        appViewModel.updateFromCollection(collectionGames)
    }

    // Tracks how far the current screen has scrolled so the header can show a divider.
    // Accumulated from NestedScrollConnection deltas; resets on route change.
    var contentScrolled by remember { mutableFloatStateOf(0f) }
    val showHeaderDivider by remember { derivedStateOf { contentScrolled > 0f } }

    LaunchedEffect(currentRoute) { contentScrolled = 0f }

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

    val tabs = listOf(
        BottomNavTab(AppRoutes.NEW_PLAY, "Log Play", Icons.AutoMirrored.Filled.NoteAdd),
        BottomNavTab(AppRoutes.HISTORY, "History", BoardFlowIcons.History),
        BottomNavTab(AppRoutes.COLLECTION, "Collection", BoardFlowIcons.Collection),
        BottomNavTab(AppRoutes.SYNC, "Sync", BoardFlowIcons.Sync),
        BottomNavTab(AppRoutes.SETTINGS, "Settings", BoardFlowIcons.Settings)
    )

    val selectedGameName = appViewModel.selectedGame?.name.orEmpty()
    val isScan = currentRoute?.startsWith("scan/") == true
    val isReview = currentRoute == AppRoutes.LOG_PLAY
    val isPlayers = currentRoute == AppRoutes.PLAYERS

    val headerSubtitle = when {
        currentRoute == AppRoutes.NEW_PLAY -> "Log a New Play"
        currentRoute == AppRoutes.HISTORY -> "Play History"
        currentRoute == AppRoutes.COLLECTION -> "My Collection"
        currentRoute == AppRoutes.SYNC -> "Sync to Sheets"
        currentRoute == AppRoutes.SETTINGS -> "Settings"
        currentRoute == AppRoutes.PLAYERS -> "Players"
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
        if (logPlayHasUnsavedChanges) {
            showDiscardLogPlayConfirm = true
        } else {
            leaveLogPlay()
        }
    }

    val headerBack: (() -> Unit)? = when {
        isReview -> ({
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
        isPlayers -> ({ navController.popBackStack() })
        else -> null
    }

    Scaffold(
        topBar = {
            AppHeader(
                subtitle = headerSubtitle,
                onNavigateBack = headerBack,
                showDivider = showHeaderDivider,
            )
        },
        bottomBar = {
            if (!isPlayers && !isScan && !isReview) {
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
                        appViewModel.selectedGame = game
                        if (appViewModel.isOnline()) {
                            navController.navigate(AppRoutes.scan(game.id, game.name))
                        } else {
                            appViewModel.initEditablePlayers(emptyList())
                            appViewModel.setExtractedPlayManual()
                            navController.navigate(AppRoutes.LOG_PLAY)
                        }
                    }
                )
            }

            composable(AppRoutes.HISTORY) {
                HistoryScreen(viewModel = appViewModel)
            }

            composable(AppRoutes.COLLECTION) {
                CollectionScreen(syncViewModel = syncViewModel)
            }

            composable(AppRoutes.SYNC) {
                SyncScreen(
                    syncViewModel = syncViewModel,
                    onPickCsv = onRequestCsvPick,
                    onSpreadsheetChanged = { id ->
                        syncViewModel.setSpreadsheetId(id)
                        appViewModel.prefs.syncSpreadsheetId = id
                    },
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
                    onNavigateToPlayers = { navController.navigate(AppRoutes.PLAYERS) }
                )
            }

            composable(AppRoutes.PLAYERS) {
                PlayersScreen(viewModel = appViewModel)
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
                    onPosted = { navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false) },
                    onNavigateBack = { requestLeaveLogPlay() },
                    onDiscard = { requestLeaveLogPlay() }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    subtitle: String,
    onNavigateBack: (() -> Unit)? = null,
    showDivider: Boolean = false,
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
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                thickness = 1.dp,
            )
        }
    }
}
