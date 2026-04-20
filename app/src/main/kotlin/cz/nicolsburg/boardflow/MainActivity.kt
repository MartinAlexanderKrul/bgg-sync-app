package cz.nicolsburg.boardflow

import android.app.Activity
import android.content.IntentSender
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import cz.nicolsburg.boardflow.auth.GoogleAuthManager
import cz.nicolsburg.boardflow.core.di.AppContainer
import cz.nicolsburg.boardflow.model.LogEntry
import cz.nicolsburg.boardflow.ui.app.BoardFlowApp
import cz.nicolsburg.boardflow.ui.theme.BggCombinedTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(applicationContext) }
    private val authManager by lazy { GoogleAuthManager(this) }
    private val appViewModel: AppViewModel by viewModels { AppViewModel.factory(container) }
    private val syncViewModel: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container.securePreferences.run {
            syncViewModel.setSpreadsheetId(syncSpreadsheetId)
            syncViewModel.setSheetTabName(syncSheetTabName)
            authManager.restoreAuthorizationIfPossible(
                previouslyAuthorizedEmail = googleAuthorizedEmail,
                onSignedIn = syncViewModel::setAccount,
                onLog = { title, detail, type -> appendSyncLog(title, detail, type) }
            )
        }

        setContent {
            val appTheme by appViewModel.appTheme.collectAsState()
            BggCombinedTheme(appTheme = appTheme) {
                BoardFlowApp(
                    appViewModel = appViewModel,
                    syncViewModel = syncViewModel,
                    onRequestSignIn = ::launchSignIn,
                    onRequestSignOut = ::launchSignOut,
                    onRequestCsvPick = { csvPickerLauncher.launch("*/*") }
                )
            }
        }
    }

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authManager.completeAuthorization(
                data = result.data,
                onSignedIn = { account ->
                    syncViewModel.setAccount(account)
                    container.securePreferences.googleAuthorizedEmail = account.name
                },
                onLog = { title, detail, type -> appendSyncLog(title, detail, type) }
            )
        } else {
            appendSyncLog("Google authorization cancelled", "Authorization dialog was dismissed", LogEntry.Type.INFO)
        }
    }

    private val csvPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val account = syncViewModel.account.value
        when {
            uri != null && account != null -> syncViewModel.syncCsv(account, contentResolver, uri)
            account == null -> appendSyncLog("Please sign in first", "Google account is required for sync", LogEntry.Type.ERROR)
        }
    }

    private fun launchSignIn() {
        lifecycleScope.launch {
            authManager.signIn(
                previouslyAuthorizedEmail = container.securePreferences.googleAuthorizedEmail,
                onSignedIn = { account ->
                    syncViewModel.setAccount(account)
                    container.securePreferences.googleAuthorizedEmail = account.name
                },
                onLaunchAuthorization = ::launchAuthorizationIntent,
                onLog = { title, detail, type -> appendSyncLog(title, detail, type) }
            )
        }
    }

    private fun launchSignOut() {
        val account = syncViewModel.account.value ?: run {
            container.securePreferences.googleAuthorizedEmail = ""
            syncViewModel.setAccount(null)
            appendSyncLog("No Google account connected", "", LogEntry.Type.INFO)
            return
        }

        authManager.signOut(account) {
            syncViewModel.setAccount(null)
            container.securePreferences.googleAuthorizedEmail = ""
            appendSyncLog("Signed out", "", LogEntry.Type.INFO)
        }

        lifecycleScope.launch {
            authManager.clearCredentialState()
        }
    }

    private fun launchAuthorizationIntent(intentSender: IntentSender) {
        authorizationLauncher.launch(
            IntentSenderRequest.Builder(intentSender).build()
        )
    }

    private fun appendSyncLog(title: String, detail: String, type: LogEntry.Type) {
        syncViewModel.appendLog(title, detail, type)
    }
}
