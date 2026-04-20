package com.bgg.combined.ui.sync

import android.accounts.Account
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bgg.combined.SyncViewModel
import com.bgg.combined.model.LogEntry
import com.bgg.combined.ui.common.AnimatedDialog
import com.bgg.combined.ui.common.BoardFlowButton
import com.bgg.combined.ui.common.BoardFlowOutlinedButton
import com.bgg.combined.ui.common.SectionCard
import com.bgg.combined.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    syncViewModel: SyncViewModel,
    onPickCsv: () -> Unit,
    onSpreadsheetChanged: (String) -> Unit
) {
    val account by syncViewModel.account.collectAsState()
    val spreadsheetId by syncViewModel.spreadsheetId.collectAsState()
    val spreadsheetTitle by syncViewModel.spreadsheetTitle.collectAsState()
    val log by syncViewModel.log.collectAsState()
    val busy by syncViewModel.busy.collectAsState()
    val hasBggCredentials by syncViewModel.hasBggCredentials.collectAsState()

    val hasConfiguredSheet = spreadsheetId.isNotBlank()
    val googleConnected = account != null
    val canSync = googleConnected && hasConfiguredSheet && hasBggCredentials

    val syncHint = when {
        !hasBggCredentials && !googleConnected -> "Connect BGG and Google in Settings"
        !hasBggCredentials -> "Connect BGG in Settings"
        !googleConnected -> "Connect Google in Settings"
        !hasConfiguredSheet -> "Connect a spreadsheet first"
        else -> null
    }

    var showSheetModal by remember { mutableStateOf(false) }
    var saveQrToDevice by remember { mutableStateOf(false) }
    var logDialogOpen by rememberSaveable { mutableStateOf(false) }
    var showClearLogConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val lastLogEntry = log.lastOrNull()

    LaunchedEffect(Unit) { syncViewModel.refreshCredentialState() }
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) {
            listState.animateScrollToItem(log.size - 1)
            if (busy) logDialogOpen = true
        }
    }

    if (showSheetModal) {
        SpreadsheetConnectModal(
            currentSheetName = spreadsheetTitle.ifBlank { null },
            onDismiss = { showSheetModal = false },
            onConnect = { input ->
                val acc = account ?: return@SpreadsheetConnectModal
                showSheetModal = false
                onSpreadsheetChanged(input)
                syncViewModel.connectExistingSpreadsheet(acc, input)
            }
        )
    }

    if (logDialogOpen && log.isNotEmpty()) {
        LogDialog(log = log, listState = listState, onDismiss = { logDialogOpen = false })
    }

    if (showClearLogConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLogConfirm = false },
            title = { Text("Clear Sync Log") },
            text = { Text("Clear all sync log entries? This only removes the local log history in the app.") },
            confirmButton = {
                TextButton(onClick = {
                    syncViewModel.clearLog()
                    logDialogOpen = false
                    showClearLogConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (log.isNotEmpty() && lastLogEntry != null) {
                LogBar(entry = lastLogEntry, onClick = { logDialogOpen = true })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Connection status bar ──────────────────────────────────
                ConnectionStatusBar(
                    googleConnected = googleConnected,
                    googleLabel = account?.name.orEmpty(),
                    bggConnected = hasBggCredentials,
                    sheetConnected = hasConfiguredSheet,
                    sheetLabel = spreadsheetTitle.ifBlank { spreadsheetId }
                )

                // ── BGG section ───────────────────────────────────────────
                SectionHeader(title = "BoardGameGeek")
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BoardFlowButton(
                            onClick = { syncViewModel.refreshCollectionFromBgg(forceRefresh = true) },
                            enabled = !busy && hasBggCredentials,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Refresh Collection")
                        }
                        BoardFlowOutlinedButton(
                            onClick = { syncViewModel.refreshSleeveDataFromBgg(forceRefresh = true) },
                            enabled = !busy && hasBggCredentials,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Refresh Sleeve Data")
                        }
                        if (!hasBggCredentials) {
                            InlineHint("Connect BGG in Settings")
                        }
                    }
                }

                HorizontalDivider()

                // ── Google Sheets section ─────────────────────────────────
                SectionHeader(title = "Google Sheets")
                SectionCard(accented = hasConfiguredSheet) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (hasConfiguredSheet) spreadsheetTitle.ifBlank { "Connected" }
                                    else "No sheet connected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (hasConfiguredSheet) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasConfiguredSheet) {
                                    Text(
                                        spreadsheetId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showSheetModal = true },
                                enabled = googleConnected
                            ) {
                                Text(
                                    if (hasConfiguredSheet) "Change" else "Connect",
                                    color = if (googleConnected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }

                        BoardFlowButton(
                            onClick = {
                                val acc = account ?: return@BoardFlowButton
                                onSpreadsheetChanged(spreadsheetId)
                                syncViewModel.syncBgg(acc, forceRefresh = true)
                            },
                            enabled = !busy && canSync,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Sync to Google Sheets")
                        }

                        if (!canSync && syncHint != null) {
                            InlineHint(syncHint)
                        }
                    }
                }

                HorizontalDivider()

                // ── Advanced section (collapsed by default) ───────────────
                AdvancedSection(
                    busy = busy,
                    account = account,
                    hasConfiguredSheet = hasConfiguredSheet,
                    saveQrToDevice = saveQrToDevice,
                    onSaveQrChanged = { saveQrToDevice = it },
                    onPickCsv = {
                        account ?: return@AdvancedSection
                        onSpreadsheetChanged(spreadsheetId)
                        onPickCsv()
                    },
                    onCreateFolders = {
                        val acc = account ?: return@AdvancedSection
                        onSpreadsheetChanged(spreadsheetId)
                        syncViewModel.createFolders(acc, saveQrToGallery = saveQrToDevice)
                    }
                )

                // ── Controls ──────────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) {
                        BoardFlowOutlinedButton(
                            onClick = { syncViewModel.stopSync() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Stop")
                        }
                    }
                    BoardFlowOutlinedButton(
                        onClick = { showClearLogConfirm = true },
                        enabled = log.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Log")
                    }
                }

                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun ConnectionStatusBar(
    googleConnected: Boolean,
    googleLabel: String,
    bggConnected: Boolean,
    sheetConnected: Boolean,
    sheetLabel: String
) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusRow("Google", googleConnected, googleLabel.ifBlank { null })
            StatusRow("BGG", bggConnected, if (bggConnected) "credentials saved" else null)
            StatusRow("Sheet", sheetConnected, sheetLabel.ifBlank { null })
        }
    }
}

@Composable
private fun StatusRow(label: String, connected: Boolean, detail: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            if (connected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (connected) Color(0xFF4CAF50)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
        if (connected && detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
            )
        }
    }
}

@Composable
private fun InlineHint(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdvancedSection(
    busy: Boolean,
    account: Account?,
    hasConfiguredSheet: Boolean,
    saveQrToDevice: Boolean,
    onSaveQrChanged: (Boolean) -> Unit,
    onPickCsv: () -> Unit,
    onCreateFolders: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Advanced",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoardFlowOutlinedButton(
                        onClick = onPickCsv,
                        enabled = !busy && account != null && hasConfiguredSheet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import from CSV")
                    }
                    BoardFlowOutlinedButton(
                        onClick = onCreateFolders,
                        enabled = !busy && account != null && hasConfiguredSheet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Folders & QR Codes")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = saveQrToDevice, onCheckedChange = onSaveQrChanged)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Also save QR images to this device", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Turn this on only if you want the PNG files in local storage too.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogBar(entry: LogEntry, onClick: () -> Unit) {
    val (containerColor, contentColor) = logColors(entry)
    Surface(
        color = containerColor,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, color = contentColor, style = MaterialTheme.typography.labelLarge)
                if (entry.status.isNotBlank()) {
                    Text(entry.status, color = contentColor, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(Icons.Default.ExpandMore, contentDescription = "Open log", tint = contentColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun LogDialog(
    log: List<LogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDismiss: () -> Unit
) {
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Sync log",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close log",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
                HorizontalDivider()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(log) { entry -> LogEntryRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val (iconText, iconColor) = when (entry.type) {
        LogEntry.Type.DONE -> "OK" to Color(0xFF4CAF50)
        LogEntry.Type.INSERTED -> "+" to MaterialTheme.colorScheme.primary
        LogEntry.Type.UPDATED -> "~" to MaterialTheme.colorScheme.onSurfaceVariant
        LogEntry.Type.ERROR -> "x" to MaterialTheme.colorScheme.error
        LogEntry.Type.HEADER -> ">" to MaterialTheme.colorScheme.primary
        LogEntry.Type.INFO -> "-" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            iconText,
            color = iconColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.size(width = 20.dp, height = 14.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = if (entry.type == LogEntry.Type.HEADER) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (entry.type == LogEntry.Type.HEADER) FontWeight.Bold else FontWeight.Normal,
                color = if (entry.type == LogEntry.Type.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (entry.status.isNotBlank()) {
                Text(
                    entry.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun logColors(entry: LogEntry): Pair<Color, Color> = when (entry.type) {
    LogEntry.Type.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    LogEntry.Type.DONE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}
