package com.bgg.combined.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.bgg.combined.AppViewModel
import com.bgg.combined.R
import com.bgg.combined.SyncViewModel
import com.bgg.combined.core.navigation.AppRoutes
import com.bgg.combined.ui.collection.CollectionScreen
import com.bgg.combined.ui.history.HistoryScreen
import com.bgg.combined.ui.players.PlayersScreen
import com.bgg.combined.ui.review.LogPlayScreen
import com.bgg.combined.ui.scan.ScanScreen
import com.bgg.combined.ui.search.NewPlayScreen
import com.bgg.combined.ui.settings.SettingsScreen
import com.bgg.combined.ui.sync.SyncScreen

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

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

    val collectionLoaded by appViewModel.collectionLoaded.collectAsState()
    val bggLoading by appViewModel.bggPlaysLoading.collectAsState()

    LaunchedEffect(Unit) { appViewModel.syncUnpostedPlays() }

    val tabs = listOf(
        BottomNavTab(AppRoutes.NEW_PLAY, "Log Play", Icons.AutoMirrored.Filled.NoteAdd),
        BottomNavTab(AppRoutes.HISTORY, "History", Icons.Default.History),
        BottomNavTab(AppRoutes.COLLECTION, "Collection", Icons.Default.GridView),
        BottomNavTab(AppRoutes.SYNC, "Sync", Icons.Default.Sync),
        BottomNavTab(AppRoutes.SETTINGS, "Settings", Icons.Default.Settings)
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

    val headerBack: (() -> Unit)? = when {
        isReview -> ({
            appViewModel.initEditablePlayers(emptyList())
            appViewModel.clearExtractedPlay()
            navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false)
        })
        isScan -> ({
            appViewModel.clearExtractedPlay()
            navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false)
        })
        isPlayers -> ({ navController.popBackStack() })
        else -> null
    }

    Scaffold(
        topBar = {
            AppHeader(subtitle = headerSubtitle, onNavigateBack = headerBack) {
                when (currentRoute) {
                    AppRoutes.NEW_PLAY -> IconButton(
                        onClick = { appViewModel.loadCollection() },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (collectionLoaded) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            if (collectionLoaded) Icons.Default.Refresh else Icons.Default.CloudDownload,
                            contentDescription = if (collectionLoaded) "Refresh collection" else "Load BGG collection"
                        )
                    }

                    AppRoutes.HISTORY -> {
                        IconButton(
                            onClick = { appViewModel.fetchBggPlays() },
                            enabled = !bggLoading,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh from BGG")
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isPlayers && !isScan && !isReview) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
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
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.NEW_PLAY,
            modifier = Modifier.padding(innerPadding)
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
                    onNavigateBack = { navController.popBackStack() },
                    onDiscard = { navController.popBackStack(AppRoutes.NEW_PLAY, inclusive = false) }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    subtitle: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
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
                                fontSize = 11.sp
                            )
                        ) {
                            append("by Nicolsburg")
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            Row(content = actions)
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
