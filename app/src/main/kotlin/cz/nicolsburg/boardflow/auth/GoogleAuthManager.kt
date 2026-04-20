package cz.nicolsburg.boardflow.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import cz.nicolsburg.boardflow.R
import cz.nicolsburg.boardflow.model.LogEntry
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.util.UUID

class GoogleAuthManager(
    private val context: Context,
    private val credentialManager: CredentialManager = CredentialManager.create(context)
) {
    private var pendingAuthorizationAccount: Account? = null

    suspend fun signIn(
        previouslyAuthorizedEmail: String,
        onSignedIn: (Account) -> Unit,
        onLaunchAuthorization: (IntentSender) -> Unit,
        onLog: (title: String, detail: String, type: LogEntry.Type) -> Unit,
    ) {
        val selectedAccount = signInWithGoogleAccount(onLog) ?: return
        authorize(
            preferredAccount = selectedAccount,
            interactive = true,
            onSignedIn = onSignedIn,
            onLaunchAuthorization = onLaunchAuthorization,
            onLog = onLog
        )
    }

    fun restoreAuthorizationIfPossible(
        previouslyAuthorizedEmail: String,
        onSignedIn: (Account) -> Unit,
        onLog: (title: String, detail: String, type: LogEntry.Type) -> Unit,
    ) {
        val email = previouslyAuthorizedEmail.trim()
        if (email.isBlank()) return

        authorize(
            preferredAccount = Account(email, GOOGLE_ACCOUNT_TYPE),
            interactive = false,
            onSignedIn = onSignedIn,
            onLaunchAuthorization = {},
            onLog = onLog
        )
    }

    fun completeAuthorization(
        data: Intent?,
        onSignedIn: (Account) -> Unit,
        onLog: (title: String, detail: String, type: LogEntry.Type) -> Unit,
    ) {
        try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            handleAuthorizationSuccess(result, pendingAuthorizationAccount, onSignedIn, onLog)
        } catch (error: ApiException) {
            onLog("Google authorization failed", "Code ${error.statusCode}: ${error.message ?: "Unknown error"}", LogEntry.Type.ERROR)
        } finally {
            pendingAuthorizationAccount = null
        }
    }

    fun signOut(account: Account, onComplete: () -> Unit) {
        Identity.getAuthorizationClient(context)
            .revokeAccess(
                RevokeAccessRequest.builder()
                    .setAccount(account)
                    .setScopes(googleScopes())
                    .build()
            )
            .addOnCompleteListener {
                onComplete()
            }
    }

    suspend fun clearCredentialState() {
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun authorize(
        preferredAccount: Account,
        interactive: Boolean,
        onSignedIn: (Account) -> Unit,
        onLaunchAuthorization: (IntentSender) -> Unit,
        onLog: (title: String, detail: String, type: LogEntry.Type) -> Unit,
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(googleScopes())
            .setAccount(preferredAccount)
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { result ->
                when {
                    result.hasResolution() && interactive -> {
                        val sender = result.pendingIntent?.intentSender
                        if (sender == null) {
                            onLog("Google authorization failed", "Authorization resolution was missing", LogEntry.Type.ERROR)
                        } else {
                            pendingAuthorizationAccount = preferredAccount
                            onLaunchAuthorization(sender)
                        }
                    }
                    result.hasResolution() -> {
                        pendingAuthorizationAccount = null
                    }
                    else -> handleAuthorizationSuccess(result, preferredAccount, onSignedIn, onLog)
                }
            }
            .addOnFailureListener { error ->
                val code = (error as? ApiException)?.statusCode?.let { " (code $it)" }.orEmpty()
                if (interactive) {
                    onLog("Google authorization failed$code", error.message ?: "Unknown error", LogEntry.Type.ERROR)
                }
                pendingAuthorizationAccount = null
            }
    }

    private fun handleAuthorizationSuccess(
        result: AuthorizationResult,
        fallbackAccount: Account?,
        onSignedIn: (Account) -> Unit,
        onLog: (title: String, detail: String, type: LogEntry.Type) -> Unit,
    ) {
        val account = resolveAuthorizedAccount(result, fallbackAccount)
        if (account == null) {
            onLog("Google authorization failed", "Authorized account details were unavailable", LogEntry.Type.ERROR)
            return
        }

        onSignedIn(account)
        onLog("Signed in as ${account.name}", "", LogEntry.Type.DONE)
    }

    @Suppress("DEPRECATION")
    private fun resolveAuthorizedAccount(result: AuthorizationResult, fallbackAccount: Account?): Account? {
        fallbackAccount?.let { return it }
        result.toGoogleSignInAccount()?.account?.let { return it }
        val email = result.toGoogleSignInAccount()?.email?.trim()
        return email?.takeIf { it.isNotBlank() }?.let { Account(it, GOOGLE_ACCOUNT_TYPE) }
    }

    private suspend fun signInWithGoogleAccount(onLog: (title: String, detail: String, type: LogEntry.Type) -> Unit): Account? {
        val webClientId = context.getString(R.string.default_web_client_id)
        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(UUID.randomUUID().toString())
                .build()
            runCredentialRequest(option, onLog)
        } catch (error: GetCredentialCancellationException) {
            onLog("Google sign-in cancelled", error.message ?: "Credential Manager was dismissed", LogEntry.Type.INFO)
            null
        } catch (error: NoCredentialException) {
            onLog("Google sign-in unavailable", error.message ?: "No Google credential was returned", LogEntry.Type.INFO)
            null
        } catch (error: GetCredentialException) {
            onLog("Google sign-in failed", "${error.javaClass.simpleName}: ${error.message ?: "Unknown error"}", LogEntry.Type.ERROR)
            null
        }
    }

    private suspend fun runCredentialRequest(
        option: CredentialOption,
        onLog: (title: String, detail: String, type: LogEntry.Type) -> Unit,
    ): Account? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = credentialManager.getCredential(context, request)
        val customCredential = result.credential as? CustomCredential
            ?: throw IllegalStateException("Unexpected credential type")

        if (customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Unexpected Google credential response")
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(customCredential.data)
        } catch (error: GoogleIdTokenParsingException) {
            onLog("Google sign-in failed", error.message ?: "Invalid Google credential response", LogEntry.Type.ERROR)
            return null
        }

        val email = googleCredential.id.trim()
        if (email.isBlank()) {
            onLog("Google sign-in failed", "Google did not return an account email", LogEntry.Type.ERROR)
            return null
        }

        return Account(email, GOOGLE_ACCOUNT_TYPE)
    }

    private fun googleScopes(): List<Scope> = listOf(
        Scope("https://www.googleapis.com/auth/drive"),
        Scope("https://www.googleapis.com/auth/spreadsheets")
    )

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
