package com.bgg.combined

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bgg.combined.model.LogEntry
import com.bgg.combined.ui.collection.CollectionScreen
import com.bgg.combined.ui.history.HistoryScreen
import com.bgg.combined.ui.players.PlayersScreen
import com.bgg.combined.ui.review.LogPlayScreen
import com.bgg.combined.ui.scan.ScanScreen
import com.bgg.combined.ui.search.NewPlayScreen
import com.bgg.combined.ui.settings.SettingsScreen
import com.bgg.combined.ui.sync.SyncScreen
import com.bgg.combined.ui.theme.BggCombinedTheme
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext) }
    private val appViewModel: AppViewModel by viewModels { AppViewModel.factory(container) }
    private val syncViewModel: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = container.securePreferences
        syncViewModel.setSpreadsheetId(prefs.syncSpreadsheetId)
        syncViewModel.setSheetTabName(prefs.syncSheetTabName)
        restoreAuthorizedAccount()

        setContent {
            val appTheme by appViewModel.appTheme.collectAsState()
            BggCombinedTheme(appTheme = appTheme) {
                BggApp(
                    appViewModel = appViewModel,
                    syncViewModel = syncViewModel,
                    onRequestSignIn = { launchSignIn() },
                    onRequestSignOut = { launchSignOut() },
                    onRequestCsvPick = { csvPickerLauncher.launch("*/*") }
                )
            }
        }
    }

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleAuthorizationIntent(result.data)
        } else {
            syncViewModel.appendLog("Google authorization cancelled", type = LogEntry.Type.ERROR)
        }
    }

    private val csvPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val account = syncViewModel.account.value
        if (uri != null && account != null) {
            syncViewModel.syncCsv(account, contentResolver, uri)
        } else if (account == null) {
            syncViewModel.appendLog("Please sign in first", type = LogEntry.Type.ERROR)
        }
    }

    private fun launchSignIn() {
        requestGoogleAuthorization(interactive = true)
    }

    private fun launchSignOut() {
        val account = syncViewModel.account.value ?: run {
            container.securePreferences.googleAuthorizedEmail = ""
            syncViewModel.setAccount(null)
            syncViewModel.appendLog("No Google account connected")
            return
        }

        Identity.getAuthorizationClient(this)
            .revokeAccess(
                RevokeAccessRequest.builder()
                    .setAccount(account)
                    .setScopes(googleScopes())
                    .build()
            )
            .addOnCompleteListener {
                syncViewModel.setAccount(null)
                syncViewModel.appendLog("Signed out")
                container.securePreferences.googleAuthorizedEmail = ""
            }
    }

    private fun restoreAuthorizedAccount() {
        val email = container.securePreferences.googleAuthorizedEmail.trim()
        if (email.isBlank()) return
        requestGoogleAuthorization(interactive = false, preferredAccount = Account(email, GOOGLE_ACCOUNT_TYPE))
    }

    private fun requestGoogleAuthorization(interactive: Boolean, preferredAccount: Account? = null) {
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(googleScopes())

        preferredAccount?.let { requestBuilder.setAccount(it) }

        Identity.getAuthorizationClient(this)
            .authorize(requestBuilder.build())
            .addOnSuccessListener { result ->
                when {
                    result.hasResolution() && interactive -> {
                        authorizationLauncher.launch(
                            IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build()
                        )
                    }
                    result.hasResolution() -> {
                        syncViewModel.setAccount(null)
                        container.securePreferences.googleAuthorizedEmail = ""
                    }
                    else -> handleAuthorizationSuccess(result, preferredAccount)
                }
            }
            .addOnFailureListener { error ->
                if (interactive) {
                    val code = if (error is ApiException) " (code ${error.statusCode})" else ""
                    syncViewModel.appendLog(
                        "Google authorization failed$code",
                        error.message ?: "Unknown error",
                        LogEntry.Type.ERROR
                    )
                } else {
                    syncViewModel.setAccount(null)
                    container.securePreferences.googleAuthorizedEmail = ""
                }
            }
    }

    private fun handleAuthorizationIntent(data: Intent?) {
        try {
            val result = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            handleAuthorizationSuccess(result, fallbackAccount = null)
        } catch (error: ApiException) {
            syncViewModel.appendLog(
                "Google authorization failed (code ${error.statusCode})",
                error.message ?: "Unknown error",
                LogEntry.Type.ERROR
            )
        }
    }

    private fun handleAuthorizationSuccess(result: AuthorizationResult, fallbackAccount: Account?) {
        val account = resolveAuthorizedAccount(result, fallbackAccount)
        if (account == null) {
            syncViewModel.appendLog(
                "Google authorization failed",
                "Authorized account details were unavailable",
                LogEntry.Type.ERROR
            )
            return
        }

        syncViewModel.setAccount(account)
        container.securePreferences.googleAuthorizedEmail = account.name
        syncViewModel.appendLog("Signed in as ${account.name}")
    }

    @Suppress("DEPRECATION")
    private fun resolveAuthorizedAccount(result: AuthorizationResult, fallbackAccount: Account?): Account? {
        fallbackAccount?.let { return it }
        result.toGoogleSignInAccount()?.account?.let { return it }

        val email = result.toGoogleSignInAccount()?.email?.trim()
        return email?.takeIf { it.isNotBlank() }?.let { Account(it, GOOGLE_ACCOUNT_TYPE) }
    }

    private fun googleScopes(): List<Scope> = SyncConfig.OAUTH_SCOPES.map(::Scope)

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

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
    val historyTab by appViewModel.historySelectedTab.collectAsState()
    val bggLoading by appViewModel.bggPlaysLoading.collectAsState()
    val localPlays by appViewModel.playHistory.collectAsState()

    LaunchedEffect(Unit) { appViewModel.syncUnpostedPlays() }

    val tabs = listOf(
        BottomNavTab(Routes.NEW_PLAY, "Log Play", Icons.AutoMirrored.Filled.NoteAdd),
        BottomNavTab(Routes.HISTORY, "History", Icons.Default.History),
        BottomNavTab(Routes.COLLECTION, "Collection", Icons.Default.GridView),
        BottomNavTab(Routes.SYNC, "Sync", Icons.Default.Sync),
        BottomNavTab(Routes.SETTINGS, "Settings", Icons.Default.Settings)
    )

    val selectedGameName = appViewModel.selectedGame?.name ?: ""
    val isScan = currentRoute?.startsWith("scan/") == true
    val isReview = currentRoute == Routes.LOG_PLAY
    val isPlayers = currentRoute == Routes.PLAYERS

    val headerSubtitle = when {
        currentRoute == Routes.NEW_PLAY -> "Log a New Play"
        currentRoute == Routes.HISTORY -> "Play History"
        currentRoute == Routes.COLLECTION -> "My Collection"
        currentRoute == Routes.SYNC -> "Sync to Sheets"
        currentRoute == Routes.SETTINGS -> "Settings"
        currentRoute == Routes.PLAYERS -> "Players"
        isScan -> selectedGameName
        isReview -> selectedGameName
        else -> ""
    }

    val headerBack: (() -> Unit)? = when {
        isReview -> ({
            appViewModel.initEditablePlayers(emptyList())
            appViewModel.clearExtractedPlay()
            navController.popBackStack(Routes.NEW_PLAY, inclusive = false)
        })
        isScan -> ({
            appViewModel.clearExtractedPlay()
            navController.popBackStack(Routes.NEW_PLAY, inclusive = false)
        })
        isPlayers -> ({ navController.popBackStack() })
        else -> null
    }

    Scaffold(
        topBar = {
            AppHeader(subtitle = headerSubtitle, onNavigateBack = headerBack) {
                when (currentRoute) {
                    Routes.NEW_PLAY -> IconButton(
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

                    Routes.HISTORY -> {
                        if (historyTab == 0) {
                            IconButton(
                                onClick = { appViewModel.fetchBggPlays() },
                                enabled = !bggLoading,
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh from BGG")
                            }
                        }
                        if (historyTab == 1 && localPlays.isNotEmpty()) {
                            IconButton(
                                onClick = { appViewModel.requestHistoryDelete() },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear local history")
                            }
                        }
                    }

                    Routes.SETTINGS -> IconButton(
                        onClick = { appViewModel.settingsSaveCallback?.invoke() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save settings")
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
                                        popUpTo(Routes.NEW_PLAY) { saveState = true }
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
            startDestination = Routes.NEW_PLAY,
            modifier = Modifier.padding(innerPadding)
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
                    syncViewModel = syncViewModel,
                    onPickCsv = onRequestCsvPick,
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
                    viewModel = appViewModel,
                    syncViewModel = syncViewModel,
                    onSignIn = onRequestSignIn,
                    onSignOut = onRequestSignOut,
                    onNavigateToPlayers = { navController.navigate(Routes.PLAYERS) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.PLAYERS) {
                PlayersScreen(viewModel = appViewModel)
            }

            composable(
                route = Routes.SCAN,
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
                    onScoresExtracted = { navController.navigate(Routes.LOG_PLAY) },
                    onDiscard = { navController.popBackStack(Routes.NEW_PLAY, inclusive = false) }
                )
            }

            composable(Routes.LOG_PLAY) {
                LogPlayScreen(
                    viewModel = appViewModel,
                    onPosted = { navController.popBackStack(Routes.NEW_PLAY, inclusive = false) },
                    onNavigateBack = { navController.popBackStack() },
                    onDiscard = { navController.popBackStack(Routes.NEW_PLAY, inclusive = false) }
                )
            }
        }
    }
}
