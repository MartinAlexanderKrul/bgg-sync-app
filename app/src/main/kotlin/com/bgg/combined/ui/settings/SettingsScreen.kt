package com.bgg.combined.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bgg.combined.AppViewModel
import com.bgg.combined.SyncViewModel
import com.bgg.combined.ui.common.BoardFlowButton
import com.bgg.combined.ui.common.BoardFlowOutlinedButton
import com.bgg.combined.ui.common.SectionCard
import com.bgg.combined.ui.common.SectionHeader
import com.bgg.combined.ui.common.clickableRow
import com.bgg.combined.BuildConfig
import com.bgg.combined.ui.theme.AppTheme
import java.time.LocalDate

private enum class SettingsSection(val title: String) {
    SETUP("Setup"),
    TOOLS("Tools"),
    AI("AI"),
    DATA("Data")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    syncViewModel: SyncViewModel,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateToPlayers: () -> Unit
) {
    val prefs = viewModel.prefs
    val context = LocalContext.current

    var username by remember { mutableStateOf(prefs.bggUsername) }
    var password by remember { mutableStateOf(prefs.bggPassword) }
    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var modelEndpoint by remember { mutableStateOf(prefs.geminiModelEndpoint) }
    var showPwd by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf(SettingsSection.SETUP) }

    val currentTheme by viewModel.appTheme.collectAsState()
    val googleAccount by syncViewModel.account.collectAsState()

    var importExportStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showImportConfirm by remember { mutableStateOf<String?>(null) }
    var includeSensitiveBackup by remember { mutableStateOf(false) }
    var modelListLoading by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>?>(null) }
    var hasCollection by remember { mutableStateOf(prefs.hasCollection()) }
    var collectionSize by remember { mutableStateOf(prefs.getCollection().size) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(viewModel.exportData(includeSensitiveBackup).toByteArray())
                }
                importExportStatus = true to "Data exported successfully"
            } catch (e: Exception) {
                importExportStatus = false to "Export failed: ${e.message}"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw Exception("Could not read file!")
                showImportConfirm = json
            } catch (e: Exception) {
                importExportStatus = false to "Import failed: ${e.message}"
            }
        }
    }

    showImportConfirm?.let { pendingJson ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text("Import Data") },
            text = { Text("This replaces local players, history, and non-sensitive settings.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    try {
                        viewModel.importData(pendingJson)
                        username = prefs.bggUsername
                        password = prefs.bggPassword
                        apiKey = prefs.geminiApiKey
                        modelEndpoint = prefs.geminiModelEndpoint
                        hasCollection = prefs.hasCollection()
                        collectionSize = prefs.getCollection().size
                        importExportStatus = true to "Data imported successfully"
                    } catch (e: Exception) {
                        importExportStatus = false to "Import failed: ${e.message}"
                    }
                    showImportConfirm = null
                }) { Text("Import", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showImportConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(accented = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Connect Google if you use Sheets, then add your BGG account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip("Google", googleAccount != null)
                            StatusChip("BGG", username.isNotBlank())
                            StatusChip("AI", apiKey.isNotBlank())
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SettingsSection.entries) { section ->
                        FilterChip(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            label = { Text(section.title) }
                        )
                    }
                }
            }

            if (selectedSection == SettingsSection.SETUP) {
                item {
                    SectionHeader(
                        title = "Setup",
                        subtitle = "Connect what you need first. Everything here saves instantly."
                    )
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.CloudDone,
                        title = "Google Sync",
                        subtitle = "Only needed for Sheets sync and Drive folders."
                    ) {
                        if (googleAccount != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    googleAccount?.name.orEmpty(),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                BoardFlowOutlinedButton(onClick = onSignOut) { Text("Sign out") }
                            }
                        } else {
                            BoardFlowButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                                Text("Sign in with Google")
                            }
                        }
                    }
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.People,
                        title = "BoardGameGeek",
                        subtitle = "Used for BGG collection refresh and play sync."
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                prefs.bggUsername = it.trim()
                            },
                            label = { Text("BGG username") },
                            placeholder = { Text("e.g. boardgamer42") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                prefs.bggPassword = it.trim()
                            },
                            label = { Text("BGG password") },
                            singleLine = true,
                            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPwd = !showPwd }) {
                                    Icon(
                                        if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.Palette,
                        title = "Appearance",
                        subtitle = "Choose how BoardFlow looks."
                    ) {
                        ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = it }) {
                            OutlinedTextField(
                                value = currentTheme.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Theme") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                                AppTheme.entries.forEach { theme ->
                                    DropdownMenuItem(
                                        text = { Text(theme.label) },
                                        onClick = {
                                            viewModel.setAppTheme(theme)
                                            themeExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedSection == SettingsSection.TOOLS) {
                item {
                    SectionHeader(
                        title = "Tools",
                        subtitle = "Manage supporting data and local caches."
                    )
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.People,
                        title = "Players",
                        subtitle = "Manage names, aliases, and BGG usernames."
                    ) {
                        ListItem(
                            headlineContent = { Text("Manage Players") },
                            supportingContent = { Text("Open the player manager") },
                            leadingContent = { Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickableRow(onClick = onNavigateToPlayers)
                        )
                    }
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.Storage,
                        title = "Collection Cache",
                        subtitle = if (hasCollection) "$collectionSize games cached locally" else "No collection cached"
                    ) {
                        BoardFlowOutlinedButton(
                            onClick = {
                                viewModel.clearCollection()
                                hasCollection = false
                                collectionSize = 0
                            },
                            enabled = hasCollection,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Collection Cache")
                        }
                    }
                }
            }

            if (selectedSection == SettingsSection.AI) {
                item {
                    SectionHeader(
                        title = "AI",
                        subtitle = "Optional extras for score extraction from photos."
                    )
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.AutoAwesome,
                        title = "Google AI Studio",
                        subtitle = "Optional. Used when you scan scoresheets."
                    ) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                prefs.geminiApiKey = it.trim()
                            },
                            label = { Text("Gemini API key") },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle key"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = modelEndpoint,
                            onValueChange = {
                                modelEndpoint = it
                                prefs.geminiModelEndpoint = it.trim()
                            },
                            label = { Text("Gemini model") },
                            placeholder = { Text("e.g. gemini-flash-latest") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BoardFlowOutlinedButton(
                            onClick = {
                                modelListLoading = true
                                viewModel.checkAvailableModels { models ->
                                    availableModels = models
                                    modelListLoading = false
                                }
                            },
                            enabled = apiKey.isNotBlank() && !modelListLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (modelListLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("  Checking models")
                            } else {
                                Text("Check Available Models")
                            }
                        }
                        availableModels?.let { models ->
                            if (models.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("Tap a model to use it", style = MaterialTheme.typography.labelMedium)
                                        models.forEach { model ->
                                            BoardFlowOutlinedButton(
                                                onClick = {
                                                    modelEndpoint = model
                                                    prefs.geminiModelEndpoint = model.trim()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(model)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "No models found. Check your API key.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (selectedSection == SettingsSection.DATA) {
                item {
                    SectionHeader(
                        title = "Data",
                        subtitle = "Back up your app state or restore it on a new device."
                    )
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.Backup,
                        title = "Backup & Restore",
                        subtitle = "Export and restore full app state for moving to a new phone."
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = includeSensitiveBackup,
                                onCheckedChange = { includeSensitiveBackup = it }
                            )
                            Column {
                                Text(
                                    "Include passwords and API keys",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Turn this on only if you want the backup file to restore your BGG password and Gemini API key too.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        BoardFlowOutlinedButton(
                            onClick = {
                                importExportStatus = null
                                exportLauncher.launch("boardflow-backup-${LocalDate.now()}.json")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Export Data")
                        }
                        BoardFlowOutlinedButton(
                            onClick = {
                                importExportStatus = null
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Import Data")
                        }
                        importExportStatus?.let { (success, message) ->
                            Text(
                                message,
                                color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "Backups include players, history, recent games, cached collection data, sync settings, theme, and local app state.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "BoardFlow",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun StatusChip(label: String, ready: Boolean) {
    FilterChip(
        selected = ready,
        onClick = {},
        label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        leadingIcon = if (ready) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        }
    )
}
