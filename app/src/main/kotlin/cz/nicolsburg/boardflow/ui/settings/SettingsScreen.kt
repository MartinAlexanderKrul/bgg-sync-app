package cz.nicolsburg.boardflow.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowAnimatedVisibility
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.SyncViewModel
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationKind
import cz.nicolsburg.boardflow.ui.common.BoardFlowInlineAction
import cz.nicolsburg.boardflow.ui.common.BoardFlowOutlinedButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowPickerField
import cz.nicolsburg.boardflow.ui.common.BoardFlowPickerSheet
import cz.nicolsburg.boardflow.ui.common.ScreenTabRow
import cz.nicolsburg.boardflow.ui.common.SectionCard
import cz.nicolsburg.boardflow.ui.common.SectionHeader
import cz.nicolsburg.boardflow.ui.common.clickableRow
import cz.nicolsburg.boardflow.ui.common.swipeToNavigateTabs
import kotlinx.coroutines.flow.collect
import cz.nicolsburg.boardflow.ui.sync.SpreadsheetConnectDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.SuggestionChip
import cz.nicolsburg.boardflow.BuildConfig
import cz.nicolsburg.boardflow.model.GameRecognitionHint
import cz.nicolsburg.boardflow.model.SleeveManufacturer
import cz.nicolsburg.boardflow.model.StatsPlayScope
import cz.nicolsburg.boardflow.ui.theme.AppTheme
import java.time.LocalDate

private enum class SettingsSection(val title: String) {
    ACCOUNTS("Accounts"),
    APPEARANCE("Appearance"),
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
    onActiveTabChange: (String?) -> Unit = {}
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
    var manufacturerExpanded by remember { mutableStateOf(false) }
    var statsScopeExpanded by remember { mutableStateOf(false) }
    var selectedSection by remember { mutableStateOf(SettingsSection.ACCOUNTS) }

    val currentTheme by viewModel.appTheme.collectAsState()
    val currentManufacturer by viewModel.sleevePreferredManufacturer.collectAsState()
    val currentStatsPlayScope by viewModel.statsPlayScope.collectAsState()
    val googleAccount by syncViewModel.account.collectAsState()
    val spreadsheetId by syncViewModel.spreadsheetId.collectAsState()
    val spreadsheetTitle by syncViewModel.spreadsheetTitle.collectAsState()
    val cachedCollection by syncViewModel.collectionGames.collectAsState()

    var showSheetModal by remember { mutableStateOf(false) }
    var importExportStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showImportConfirm by remember { mutableStateOf<String?>(null) }
    var includeSensitiveBackup by remember { mutableStateOf(false) }
    var modelListLoading by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>?>(null) }
    var showGoogleSignOutConfirm by remember { mutableStateOf(false) }
    var showClearCollectionConfirm by remember { mutableStateOf(false) }
    var templateCount by remember { mutableStateOf(viewModel.prefs.loadGameRecognitionHints().size) }
    var showClearTemplatesConfirm by remember { mutableStateOf(false) }
    var clearTemplatesStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var playerHintCount by remember { mutableStateOf(viewModel.getPlayerRecognitionHintCount()) }
    var showClearPlayerHintsConfirm by remember { mutableStateOf(false) }
    var clearPlayerHintsStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val hasCollection = cachedCollection.isNotEmpty()

    val listState = rememberLazyListState()
    var controlsVisible by remember { mutableStateOf(true) }
    val collectionSize = cachedCollection.size

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
        BoardFlowConfirmationDialog(
            title = "Import backup and replace current data?",
            message = "This replaces local players, history, and non-sensitive settings on this device.",
            confirmLabel = "Import",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.DESTRUCTIVE,
            onConfirm = {
                try {
                    viewModel.importData(pendingJson)
                    username = prefs.bggUsername
                    password = prefs.bggPassword
                    apiKey = prefs.geminiApiKey
                    modelEndpoint = prefs.geminiModelEndpoint
                    syncViewModel.reloadLocalSyncPreferences()
                    syncViewModel.loadCachedCollection()
                    importExportStatus = true to "Data imported successfully"
                } catch (e: Exception) {
                    importExportStatus = false to "Import failed: ${e.message}"
                }
                showImportConfirm = null
            },
            onDismiss = { showImportConfirm = null }
        )
    }

    if (showGoogleSignOutConfirm) {
        BoardFlowConfirmationDialog(
            title = "Sign out of Google?",
            message = "Google Sheets and Drive sync will be unavailable until you sign in again.",
            confirmLabel = "Sign out",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.NEUTRAL,
            onConfirm = {
                showGoogleSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showGoogleSignOutConfirm = false }
        )
    }

    if (showClearCollectionConfirm) {
        BoardFlowConfirmationDialog(
            title = "Clear collection cache?",
            message = "This removes the cached collection from this device. You can refresh it again later from Sync.",
            confirmLabel = "Clear cache",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.DESTRUCTIVE,
            onConfirm = {
                showClearCollectionConfirm = false
                syncViewModel.clearCollectionCache()
            },
            onDismiss = { showClearCollectionConfirm = false }
        )
    }

    if (showClearTemplatesConfirm) {
        BoardFlowConfirmationDialog(
            title = "Clear recognition templates?",
            message = "This removes all saved game scoring layouts used to improve scan recognition. Your plays, roster, and collection are not affected.",
            confirmLabel = "Clear templates",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.DESTRUCTIVE,
            onConfirm = {
                showClearTemplatesConfirm = false
                viewModel.clearGameRecognitionHints()
                templateCount = 0
                clearTemplatesStatus = true to "Recognition templates cleared."
            },
            onDismiss = { showClearTemplatesConfirm = false }
        )
    }

    if (showClearPlayerHintsConfirm) {
        BoardFlowConfirmationDialog(
            title = "Clear player recognition hints?",
            message = "This removes all saved scan-name-to-player mappings. Future scans will rely on aliases and fuzzy matching until hints are re-learned.",
            confirmLabel = "Clear hints",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.DESTRUCTIVE,
            onConfirm = {
                showClearPlayerHintsConfirm = false
                viewModel.clearPlayerRecognitionHints()
                playerHintCount = 0
                clearPlayerHintsStatus = true to "Player recognition hints cleared."
            },
            onDismiss = { showClearPlayerHintsConfirm = false }
        )
    }

    if (showSheetModal) {
        SpreadsheetConnectDialog(
            currentSheetName = spreadsheetTitle.ifBlank { null },
            onDismiss = { showSheetModal = false },
            onConnect = { input ->
                val acc = googleAccount ?: return@SpreadsheetConnectDialog
                showSheetModal = false
                syncViewModel.connectExistingSpreadsheet(acc, input)
            },
            onCreateNew = googleAccount?.let { acc -> {
                showSheetModal = false
                syncViewModel.createSpreadsheetFromBgg(acc)
            }}
        )
    }

    LaunchedEffect(listState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)
                val atTop = index == 0 && offset < 8
                controlsVisible = atTop || !scrollingDown
                lastIndex = index
                lastOffset = offset
            }
    }

    LaunchedEffect(selectedSection) {
        controlsVisible = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(controlsVisible, selectedSection) {
        onActiveTabChange(if (controlsVisible) null else selectedSection.title)
    }

    DisposableEffect(Unit) {
        onDispose { onActiveTabChange(null) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BoardFlowAnimatedVisibility(visible = controlsVisible) {
                ScreenTabRow(
                    tabs = SettingsSection.entries.map { it.title },
                    selectedIndex = selectedSection.ordinal,
                    onTabSelected = { selectedSection = SettingsSection.entries[it] }
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .swipeToNavigateTabs(
                        tabCount = SettingsSection.entries.size,
                        selectedIndex = selectedSection.ordinal,
                        onNavigate = { selectedSection = SettingsSection.entries[it] }
                    ),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            if (selectedSection == SettingsSection.ACCOUNTS) {
                item {
                    SectionHeader(
                        title = "Accounts",
                        subtitle = "Sign in to Google for Sheets sync, then add your BGG account."
                    )
                }

                item { SettingsSectionLabel("Connections") }

                item {
                    SettingsCard(
                        icon = Icons.Default.CloudDone,
                        title = "Google",
                        subtitle = "Required for Sheets sync and Drive folders."
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
                                BoardFlowOutlinedButton(onClick = { showGoogleSignOutConfirm = true }) { Text("Sign out") }
                            }
                            HorizontalDivider()
                            // Google Sheets sub-section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.GridOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Google Sheets", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        if (spreadsheetId.isNotBlank())
                                            spreadsheetTitle.ifBlank { "…${spreadsheetId.takeLast(8)}" }
                                        else "No sheet selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (spreadsheetId.isNotBlank()) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                BoardFlowInlineAction(onClick = { showSheetModal = true }) {
                                    Text(
                                        if (spreadsheetId.isNotBlank()) "Change" else "Connect",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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

            }

            if (selectedSection == SettingsSection.APPEARANCE) {
                item {
                    SectionHeader(
                        title = "Appearance",
                        subtitle = "Choose how BoardFlow looks."
                    )
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        subtitle = "Set the visual style for the app."
                    ) {
                        BoardFlowPickerField(
                            label = "Theme",
                            value = currentTheme.label,
                            expanded = themeExpanded,
                            onClick = { themeExpanded = true }
                        )
                        if (themeExpanded) {
                            BoardFlowPickerSheet(
                                title = "Choose theme",
                                options = AppTheme.entries,
                                selectedOption = currentTheme,
                                optionLabel = { it.label },
                                onSelect = { theme ->
                                    viewModel.setAppTheme(theme)
                                    themeExpanded = false
                                },
                                onDismiss = { themeExpanded = false }
                            )
                        }
                    }
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.GridOn,
                        title = "History stats",
                        subtitle = "Choose which logged plays shape the Stats tab."
                    ) {
                        BoardFlowPickerField(
                            label = "Stats source",
                            value = currentStatsPlayScope.label,
                            expanded = statsScopeExpanded,
                            onClick = { statsScopeExpanded = true }
                        )
                        Text(
                            currentStatsPlayScope.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                        if (statsScopeExpanded) {
                            BoardFlowPickerSheet(
                                title = "Choose stats source",
                                options = StatsPlayScope.entries,
                                selectedOption = currentStatsPlayScope,
                                optionLabel = { it.label },
                                onSelect = { scope ->
                                    viewModel.setStatsPlayScope(scope)
                                    statsScopeExpanded = false
                                },
                                onDismiss = { statsScopeExpanded = false }
                            )
                        }
                    }
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.GridOn,
                        title = "Sleeve manufacturer",
                        subtitle = "Priority brand shown for sleeve recommendations."
                    ) {
                        BoardFlowPickerField(
                            label = "Manufacturer",
                            value = currentManufacturer.label,
                            expanded = manufacturerExpanded,
                            onClick = { manufacturerExpanded = true }
                        )
                        if (manufacturerExpanded) {
                            BoardFlowPickerSheet(
                                title = "Choose sleeve manufacturer",
                                options = SleeveManufacturer.entries,
                                selectedOption = currentManufacturer,
                                optionLabel = { it.label },
                                onSelect = { manufacturer ->
                                    viewModel.setSleevePreferredManufacturer(manufacturer)
                                    manufacturerExpanded = false
                                },
                                onDismiss = { manufacturerExpanded = false }
                            )
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
                        var showApiHelp by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                prefs.geminiApiKey = it.trim()
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Gemini API key")
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { showApiHelp = true },
                                        modifier = Modifier
                                            .size(20.dp)
                                            .focusable()
                                            .semantics {
                                                contentDescription = "How to get API key"
                                                role = androidx.compose.ui.semantics.Role.Button
                                            }
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
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
                        if (showApiHelp) {
                            AnimatedDialog(onDismissRequest = { showApiHelp = false }) {
                                val uriHandler = LocalUriHandler.current
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "How to get a Gemini API key",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    item { HorizontalDivider() }
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("1. Visit ", style = MaterialTheme.typography.bodyMedium)
                                            TextButton(
                                                onClick = { uriHandler.openUri("https://aistudio.google.com") },
                                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                            ) {
                                                Text("aistudio.google.com", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    item {
                                        Text("2. Sign in and open your profile > API Keys.", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    item {
                                        Text("3. Create a key and paste it here.", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        var modelPickerOpen by remember { mutableStateOf(false) }
                        if (availableModels?.isNotEmpty() == true) {
                            BoardFlowPickerField(
                                label = "Gemini model",
                                value = modelEndpoint.ifBlank { "Not selected" },
                                expanded = modelPickerOpen,
                                onClick = { modelPickerOpen = true }
                            )
                            if (modelPickerOpen) {
                                BoardFlowPickerSheet(
                                    title = "Choose Gemini model",
                                    options = availableModels ?: emptyList(),
                                    selectedOption = modelEndpoint,
                                    optionLabel = { it },
                                    onSelect = { model ->
                                        modelEndpoint = model
                                        prefs.geminiModelEndpoint = model.trim()
                                        modelPickerOpen = false
                                    },
                                    onDismiss = { modelPickerOpen = false }
                                )
                            }
                        } else {
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
                        }
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
                                Text("Refresh available models")
                            }
                        }
                        availableModels?.let { models ->
                            if (models.isEmpty()) {
                                Text(
                                    "No models found. Check your API key.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.Layers,
                        title = "Recognition Templates",
                        subtitle = "Saved scoring layouts that improve game detection from photos."
                    ) {
                        Text(
                            if (templateCount == 0) "No templates saved yet."
                            else "$templateCount game template${if (templateCount == 1) "" else "s"} saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (templateCount > 0) {
                            BoardFlowOutlinedButton(
                                onClick = { showTemplatesDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View templates")
                            }
                        }
                        BoardFlowOutlinedButton(
                            onClick = { showClearTemplatesConfirm = true },
                            enabled = templateCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear recognition templates")
                        }
                        clearTemplatesStatus?.let { (success, message) ->
                            Text(
                                message,
                                color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (showTemplatesDialog) {
                            RecognitionTemplatesDialog(
                                viewModel = viewModel,
                                onDismiss = { showTemplatesDialog = false },
                                onTemplatesChanged = { newCount -> templateCount = newCount }
                            )
                        }
                    }
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.Person,
                        title = "Player Recognition Hints",
                        subtitle = "Learned scan-name-to-player mappings that pre-fill roster players from scan output."
                    ) {
                        Text(
                            if (playerHintCount == 0) "No hints saved yet."
                            else "$playerHintCount player hint${if (playerHintCount == 1) "" else "s"} saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BoardFlowOutlinedButton(
                            onClick = { showClearPlayerHintsConfirm = true },
                            enabled = playerHintCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear player recognition hints")
                        }
                        clearPlayerHintsStatus?.let { (success, message) ->
                            Text(
                                message,
                                color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (selectedSection == SettingsSection.DATA) {
                item {
                    SectionHeader(
                        title = "Data",
                        subtitle = "Manage cached data, or back up and restore the app on a new device."
                    )
                }

                item {
                    SettingsCard(
                        icon = Icons.Default.Storage,
                        title = "Collection Cache",
                        subtitle = if (hasCollection) "$collectionSize games cached locally" else "No collection cached"
                    ) {
                        BoardFlowOutlinedButton(
                            onClick = { showClearCollectionConfirm = true },
                            enabled = hasCollection,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Collection Cache")
                        }
                    }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecognitionTemplatesDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onTemplatesChanged: (Int) -> Unit
) {
    var templates by remember { mutableStateOf(viewModel.prefs.loadGameRecognitionHints()) }
    var editingHint by remember { mutableStateOf<GameRecognitionHint?>(null) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text(
                    "Recognition Templates",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                )
            }
            item {
                Text(
                    "Edit or delete saved recognition templates.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            item { HorizontalDivider() }
            if (templates.isEmpty()) {
                item {
                    Text(
                        "No templates saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(templates.size) { index ->
                    val hint = templates[index]
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                hint.gameName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "confirmed ${hint.timesConfirmed}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        if (hint.normalizedCategories.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                hint.normalizedCategories.forEach { cat ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            cat,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "No categories saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { editingHint = hint },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.deleteGameRecognitionHint(hint.gameObjectId)
                                    templates = viewModel.prefs.loadGameRecognitionHints()
                                    onTemplatesChanged(templates.size)
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Delete")
                            }
                        }
                    }
                    if (index < templates.size - 1) HorizontalDivider()
                }
            }
        }
    }

    editingHint?.let { hint ->
        EditTemplateDialog(
            hint = hint,
            onSave = { updated ->
                viewModel.replaceGameRecognitionHint(updated)
                templates = viewModel.prefs.loadGameRecognitionHints()
                editingHint = null
            },
            onDismiss = { editingHint = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTemplateDialog(
    hint: GameRecognitionHint,
    onSave: (GameRecognitionHint) -> Unit,
    onDismiss: () -> Unit
) {
    var categories by remember { mutableStateOf(hint.normalizedCategories.toMutableList()) }
    var newCatInput by remember { mutableStateOf("") }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                hint.gameName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider()
            Text(
                "Scoring categories",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (categories.isEmpty()) {
                Text(
                    "No categories — add one below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        SuggestionChip(
                            onClick = {
                                categories = categories.toMutableList().also { it.remove(cat) }
                            },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            icon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newCatInput,
                    onValueChange = { newCatInput = it },
                    label = { Text("Add category") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val normalized = newCatInput.trim()
                            .lowercase()
                            .replace(Regex("[^a-z0-9 ]"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        if (normalized.isNotBlank() && !categories.contains(normalized)) {
                            categories = categories.toMutableList().also { it.add(normalized) }
                        }
                        newCatInput = ""
                    },
                    enabled = newCatInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                BoardFlowButton(
                    onClick = { onSave(hint.copy(normalizedCategories = categories.toList())) }
                ) { Text("Save") }
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
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    )
}
