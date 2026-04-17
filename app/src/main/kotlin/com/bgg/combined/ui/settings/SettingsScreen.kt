package com.bgg.combined.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bgg.combined.AppViewModel
import com.bgg.combined.SyncViewModel
import com.bgg.combined.ui.theme.AppTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    syncViewModel: SyncViewModel,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToPlayers: () -> Unit,
    onNavigateBack: (() -> Unit)? = null
) {
    val prefs   = viewModel.prefs
    val context = LocalContext.current

    var username      by remember { mutableStateOf(prefs.bggUsername) }
    var password      by remember { mutableStateOf(prefs.bggPassword) }
    var apiKey        by remember { mutableStateOf(prefs.geminiApiKey) }
    var modelEndpoint by remember { mutableStateOf(prefs.geminiModelEndpoint) }
    var showPwd       by remember { mutableStateOf(false) }
    var showKey       by remember { mutableStateOf(false) }
    var saved         by remember { mutableStateOf(false) }

    val currentTheme  by viewModel.appTheme.collectAsState()
    val googleAccount by syncViewModel.account.collectAsState()
    var themeExpanded by remember { mutableStateOf(false) }

    val doSave: () -> Unit = {
        prefs.bggUsername         = username.trim()
        prefs.bggPassword         = password.trim()
        prefs.geminiApiKey        = apiKey.trim()
        prefs.geminiModelEndpoint = modelEndpoint.trim()
        saved = true
    }

    DisposableEffect(Unit) {
        viewModel.settingsSaveCallback = doSave
        onDispose { viewModel.settingsSaveCallback = null }
    }

    var importExportStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showImportConfirm  by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.exportData().toByteArray()) }
                importExportStatus = Pair(true, "Data exported successfully")
            } catch (e: Exception) { importExportStatus = Pair(false, "Export failed: ${e.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw Exception("Could not read file")
                showImportConfirm = json
            } catch (e: Exception) { importExportStatus = Pair(false, "Import failed: ${e.message}") }
        }
    }

    showImportConfirm?.let { pendingJson ->
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text("Import Data") },
            text  = { Text("This will replace all local data (players, play history and settings). Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        viewModel.importData(pendingJson)
                        username = prefs.bggUsername; password = prefs.bggPassword
                        apiKey = prefs.geminiApiKey; modelEndpoint = prefs.geminiModelEndpoint
                        importExportStatus = Pair(true, "Data imported successfully")
                    } catch (e: Exception) { importExportStatus = Pair(false, "Import failed: ${e.message}") }
                    showImportConfirm = null
                }) { Text("Import", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showImportConfirm = null }) { Text("Cancel") } }
        )
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Appearance ────────────────────────────────────────────
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Theme", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = it }) {
                        OutlinedTextField(value = currentTheme.label, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                            AppTheme.entries.forEach { theme ->
                                DropdownMenuItem(text = { Text(theme.label) },
                                    onClick = { viewModel.setAppTheme(theme); themeExpanded = false },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ── Google Account ────────────────────────────────────────
                Text("Google Account", style = MaterialTheme.typography.titleMedium)
                Text("Required for Sync tab — syncing to Google Sheets and creating Drive folders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (googleAccount != null) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("✓  ${googleAccount!!.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onSignOut) { Text("Sign Out") }
                    }
                } else {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in with Google")
                    }
                }

                HorizontalDivider()

                // ── BGG Account ───────────────────────────────────────────
                Text("BoardGameGeek Account", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("BGG Username", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = username, onValueChange = { username = it; saved = false },
                        placeholder = { Text("e.g. boardgamer42") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("BGG Password", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = password, onValueChange = { password = it; saved = false },
                        singleLine = true,
                        visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }) {
                                Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password")
                            }
                        },
                        modifier = Modifier.fillMaxWidth())
                }

                HorizontalDivider()

                // ── Gemini API Key ────────────────────────────────────────
                Text("Google AI Studio API Key", style = MaterialTheme.typography.titleMedium)
                Text("Required for score extraction from photos. Get your free key at aistudio.google.com → Get API key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Gemini API Key", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; saved = false },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key")
                            }
                        },
                        modifier = Modifier.fillMaxWidth())
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Gemini Model", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = modelEndpoint, onValueChange = { modelEndpoint = it; saved = false },
                        placeholder = { Text("e.g. gemini-flash-latest") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }

                var modelListLoading by remember { mutableStateOf(false) }
                var availableModels  by remember { mutableStateOf<List<String>?>(null) }
                OutlinedButton(
                    onClick = {
                        modelListLoading = true
                        viewModel.checkAvailableModels { models ->
                            availableModels = models; modelListLoading = false
                        }
                    },
                    enabled = apiKey.isNotBlank() && !modelListLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (modelListLoading) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text("Check Available Models")
                }
                availableModels?.let { models ->
                    if (models.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Tap a model to use it:", style = MaterialTheme.typography.labelMedium)
                                models.forEach { model ->
                                    TextButton(onClick = { modelEndpoint = model; saved = false; availableModels = null },
                                        modifier = Modifier.fillMaxWidth()) {
                                        Text(model, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No models found. Check your API key.", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                Button(onClick = doSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                if (saved) Text("✓ Settings saved", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium)

                HorizontalDivider()

                // ── Players ───────────────────────────────────────────────
                Text("Players", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent   = { Text("Manage Players") },
                    supportingContent = { Text("Edit display names, aliases, and BGG usernames") },
                    leadingContent    = { Icon(Icons.Default.People, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickableRipple { onNavigateToPlayers() }
                )

                HorizontalDivider()

                // ── Collection Cache ──────────────────────────────────────
                Text("Collection Cache", style = MaterialTheme.typography.titleMedium)
                var hasCollection  by remember { mutableStateOf(prefs.hasCollection()) }
                var collectionSize by remember { mutableStateOf(prefs.getCollection().size) }
                Text(if (hasCollection) "$collectionSize games cached locally" else "No collection cached",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { viewModel.clearCollection(); hasCollection = false; collectionSize = 0 },
                    enabled = hasCollection, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear Collection Cache")
                }

                HorizontalDivider()

                // ── Backup & Restore ──────────────────────────────────────
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                Text("Export players, play history, and non-sensitive settings to a JSON file.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("⚠ Passwords and API keys are never included in backups.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { importExportStatus = null; exportLauncher.launch("bgg-backup-${LocalDate.now()}.json") },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Export Data")
                }
                OutlinedButton(onClick = { importExportStatus = null; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Import Data")
                }
                importExportStatus?.let { (success, message) ->
                    Text(if (success) "✓ $message" else "✗ $message",
                        color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// Extension to make any Modifier clickable without ripple pollution on ListItem
private fun Modifier.clickableRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
