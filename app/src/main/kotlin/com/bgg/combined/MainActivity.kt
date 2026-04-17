package com.bgg.combined

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bgg.combined.ui.collection.CollectionScreen
import com.bgg.combined.ui.history.HistoryScreen
import com.bgg.combined.ui.players.PlayersScreen
import com.bgg.combined.ui.review.LogPlayScreen
import com.bgg.combined.ui.scan.ScanScreen
import com.bgg.combined.ui.search.NewPlayScreen
import com.bgg.combined.ui.settings.SettingsScreen
import com.bgg.combined.ui.sync.SyncScreen
import com.bgg.combined.ui.theme.BggCombinedTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext) }
    private val appViewModel: AppViewModel by viewModels { AppViewModel.factory(container) }
    private val syncViewModel: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore persisted sync prefs into SyncViewModel
        val prefs = container.securePreferences
        syncViewModel.setSpreadsheetId(prefs.syncSpreadsheetId)
        syncViewModel.setSheetTabName(prefs.syncSheetTabName)

        // Restore last Google Sign-In account if available
        GoogleSignIn.getLastSignedInAccount(this)?.let { syncViewModel.setAccount(it) }

        setContent {
            val appTheme by appViewModel.appTheme.collectAsState()
            BggCombinedTheme(appTheme = appTheme) {
                BggApp(
                    appViewModel  = appViewModel,
                    syncViewModel = syncViewModel,
                    onRequestSignIn = { launchSignIn() },
                    onRequestSignOut = { launchSignOut() },
                    onRequestCsvPick = { csvPickerLauncher.launch("*/*") }
                )
            }
        }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { account ->
                    syncViewModel.setAccount(account)
                    syncViewModel.appendLog("Signed in as ${account.email ?: "unknown"}")
                }
                .addOnFailureListener { e ->
                    val code = if (e is ApiException) " (code ${e.statusCode})" else ""
                    syncViewModel.appendLog("Sign-in failed$code", type = com.bgg.combined.model.LogEntry.Type.ERROR)
                }
        }
    }

    private val csvPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val account = syncViewModel.account.value
        if (uri != null && account != null) {
            syncViewModel.syncCsv(account, contentResolver, uri)
        } else if (account == null) {
            syncViewModel.appendLog("Please sign in first", type = com.bgg.combined.model.LogEntry.Type.ERROR)
        }
    }

    private fun launchSignIn() {
        val serverClientId = getString(R.string.server_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive"),
                Scope("https://www.googleapis.com/auth/spreadsheets")
            )
            .requestServerAuthCode(serverClientId, false)
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener { signInLauncher.launch(client.signInIntent) }
    }

    private fun launchSignOut() {
        GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            .addOnCompleteListener {
                syncViewModel.setAccount(null)
                syncViewModel.appendLog("Signed out")
            }
    }
}

// ── Bottom nav tab definitions ─────────────────────────────────────────────

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// ── App header ─────────────────────────────────────────────────────────────

@Composable
fun AppHeader(
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
                painter = painterResource(R.drawable.logo_no_borders),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "BGG Tools",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
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
                    Icon(Icons.Default.Close, contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ── Root composable ────────────────────────────────────────────────────────

@Composable
fun BggApp(
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
    val historyTab       by appViewModel.historySelectedTab.collectAsState()
    val bggLoading       by appViewModel.bggPlaysLoading.collectAsState()
    val localPlays       by appViewModel.playHistory.collectAsState()

    LaunchedEffect(Unit) { appViewModel.syncUnpostedPlays() }

    val tabs = listOf(
        BottomNavTab(Routes.NEW_PLAY,    "Log Play",   Icons.AutoMirrored.Filled.NoteAdd),
        BottomNavTab(Routes.HISTORY,     "History",    Icons.Default.History),
        BottomNavTab(Routes.COLLECTION,  "Collection", Icons.Default.GridView),
        BottomNavTab(Routes.SYNC,        "Sync",       Icons.Default.Sync),
        BottomNavTab(Routes.SETTINGS,    "Settings",   Icons.Default.Settings)
    )

    val selectedGameName = appViewModel.selectedGame?.name ?: ""
    val isScan   = currentRoute?.startsWith("scan/") == true
    val isReview = currentRoute == Routes.LOG_PLAY
    val isPlayers = currentRoute == Routes.PLAYERS

    val headerSubtitle = when {
        currentRoute == Routes.NEW_PLAY   -> "Log a New Play"
        currentRoute == Routes.HISTORY    -> "Play History"
        currentRoute == Routes.COLLECTION -> "My Collection"
        currentRoute == Routes.SYNC       -> "Sync to Sheets"
        currentRoute == Routes.SETTINGS   -> "Settings"
        currentRoute == Routes.PLAYERS    -> "Players"
        isScan                            -> selectedGameName
        isReview                          -> selectedGameName
        else                              -> ""
    }

    val headerBack: (() -> Unit)? = when {
        isReview  -> ({
            appViewModel.initEditablePlayers(emptyList())
            appViewModel.clearExtractedPlay()
            navController.popBackStack(Routes.NEW_PLAY, inclusive = false)
        })
        isScan    -> ({
            appViewModel.clearExtractedPlay()
            navController.popBackStack(Routes.NEW_PLAY, inclusive = false)
        })
        isPlayers -> ({ navController.popBackStack() })
        else      -> null
    }

    Scaffold(
        topBar = {
            AppHeader(subtitle = headerSubtitle, onNavigateBack = headerBack) {
                when (currentRoute) {
                    Routes.NEW_PLAY -> IconButton(
                        onClick = { appViewModel.loadCollection() },
                        colors  = IconButtonDefaults.iconButtonColors(
                            contentColor = if (collectionLoaded)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (collectionLoaded) Icons.Default.Refresh else Icons.Default.CloudDownload,
                            contentDescription = if (collectionLoaded) "Refresh collection" else "Load BGG collection"
                        )
                    }
                    Routes.HISTORY -> {
                        if (historyTab == 0) {
                            IconButton(
                                onClick = { appViewModel.fetchBggPlays() },
                                enabled = !bggLoading,
                                colors  = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) { Icon(Icons.Default.Refresh, contentDescription = "Refresh from BGG") }
                        }
                        if (historyTab == 1 && localPlays.isNotEmpty()) {
                            IconButton(
                                onClick = { appViewModel.requestHistoryDelete() },
                                colors  = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear local history") }
                        }
                    }
                    Routes.SETTINGS -> IconButton(
                        onClick = { appViewModel.settingsSaveCallback?.invoke() },
                        colors  = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) { Icon(Icons.Default.Check, contentDescription = "Save settings") }
                }
            }
        },
        bottomBar = {
            // Only show bottom bar for top-level tabs (not players or scan/review sub-screens)
            if (!isPlayers && !isScan && !isReview) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick  = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.NEW_PLAY) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            },
                            icon  = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.NEW_PLAY,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Routes.NEW_PLAY) {
                NewPlayScreen(
                    viewModel = appViewModel,
                    onGameSelected = { game ->
                        appViewModel.selectedGame = game
                        if (appViewModel.isOnline()) {
                            navController.navigate(Routes.scan(game.id, game.name))
                        } else {
                            appViewModel.initEditablePlayers(emptyList())
                            appViewModel.setExtractedPlayManual()
                            navController.navigate(Routes.LOG_PLAY)
                        }
                    }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(viewModel = appViewModel)
            }

            composable(Routes.COLLECTION) {
                CollectionScreen(syncViewModel = syncViewModel)
            }

            composable(Routes.SYNC) {
                SyncScreen(
                    syncViewModel    = syncViewModel,
                    onSignIn         = onRequestSignIn,
                    onSignOut        = onRequestSignOut,
                    onPickCsv        = onRequestCsvPick,
                    onSpreadsheetChanged = { id ->
                        syncViewModel.setSpreadsheetId(id)
                        appViewModel.prefs.syncSpreadsheetId = id
                    },
                    onTabNameChanged = { name ->
                        syncViewModel.setSheetTabName(name)
                        appViewModel.prefs.syncSheetTabName = name
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel         = appViewModel,
                    syncViewModel     = syncViewModel,
                    onSignIn          = onRequestSignIn,
                    onSignOut         = onRequestSignOut,
                    onNavigateToPlayers = { navController.navigate(Routes.PLAYERS) },
                    onNavigateBack    = { navController.popBackStack() }
                )
            }

            composable(Routes.PLAYERS) {
                PlayersScreen(viewModel = appViewModel)
            }

            composable(
                route     = Routes.SCAN,
                arguments = listOf(
                    navArgument("gameId")   { type = NavType.IntType },
                    navArgument("gameName") { type = NavType.StringType }
                )
            ) { backStack ->
                val gameName = java.net.URLDecoder.decode(
                    backStack.arguments?.getString("gameName") ?: "", "UTF-8"
                )
                ScanScreen(
                    viewModel         = appViewModel,
                    gameName          = gameName,
                    onScoresExtracted = { navController.navigate(Routes.LOG_PLAY) },
                    onDiscard         = { navController.popBackStack(Routes.NEW_PLAY, inclusive = false) }
                )
            }

            composable(Routes.LOG_PLAY) {
                LogPlayScreen(
                    viewModel      = appViewModel,
                    onPosted       = { navController.popBackStack(Routes.NEW_PLAY, inclusive = false) },
                    onNavigateBack = { navController.popBackStack() },
                    onDiscard      = { navController.popBackStack(Routes.NEW_PLAY, inclusive = false) }
                )
            }
        }
    }
}
