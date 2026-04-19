package com.bgg.combined.ui.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.bgg.combined.SyncViewModel
import com.bgg.combined.model.LogEntry

private enum class SyncSetupMode {
    EXISTING_SHEET,
    CREATE_FROM_BGG
}

private enum class LogPanelState {
    CLOSED,
    MINIMIZED,
    EXPANDED
}

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
    val sheetTabName by syncViewModel.sheetTabName.collectAsState()
    val log by syncViewModel.log.collectAsState()
    val busy by syncViewModel.busy.collectAsState()
    val lastLogEntry = log.lastOrNull()

    var spreadsheetField by remember(spreadsheetId) { mutableStateOf(spreadsheetId) }
    var setupMode by remember { mutableStateOf(SyncSetupMode.EXISTING_SHEET) }
    var saveQrToDevice by remember { mutableStateOf(false) }
    var logPanelState by rememberSaveable { mutableStateOf(LogPanelState.CLOSED) }
    var showClearLogConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val hasConfiguredSheet = spreadsheetId.isNotBlank()

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) {
            listState.animateScrollToItem(log.size - 1)
            if (logPanelState == LogPanelState.CLOSED) {
                logPanelState = LogPanelState.MINIMIZED
            }
        }
    }

    if (logPanelState == LogPanelState.EXPANDED && log.isNotEmpty()) {
        LogDialog(
            log = log,
            listState = listState,
            onMinimize = { logPanelState = LogPanelState.MINIMIZED },
            onClose = { logPanelState = LogPanelState.CLOSED }
        )
    }

    if (showClearLogConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLogConfirm = false },
            title = { Text("Clear Sync Log") },
            text = { Text("Clear all sync log entries? This only removes the local log history in the app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        syncViewModel.clearLog()
                        logPanelState = LogPanelState.CLOSED
                        showClearLogConfirm = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (log.isNotEmpty() && logPanelState == LogPanelState.MINIMIZED && lastLogEntry != null) {
                MinimizedLogBar(
                    entry = lastLogEntry,
                    onExpand = { logPanelState = LogPanelState.EXPANDED },
                    onClose = { logPanelState = LogPanelState.CLOSED }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "You can keep Collection working from BGG only, or optionally connect Google Sheets for spreadsheet sync and Drive folders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "BGG only",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Refresh your BGG collection into the app without using Google Sheets. Collection will keep using the cached result on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { syncViewModel.refreshCollectionFromBgg(forceRefresh = true) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Refresh collection from BGG")
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    "Google Sheets is optional",
                    style = MaterialTheme.typography.titleSmall
                )

                SetupOptionCard(
                    title = "Use an existing Google Sheet",
                    subtitle = "Paste the spreadsheet link or ID and the app will use the first sheet automatically.",
                    selected = setupMode == SyncSetupMode.EXISTING_SHEET,
                    onClick = { setupMode = SyncSetupMode.EXISTING_SHEET }
                )

                if (setupMode == SyncSetupMode.EXISTING_SHEET) {
                    OutlinedTextField(
                        value = spreadsheetField,
                        onValueChange = { spreadsheetField = it },
                        label = { Text("Spreadsheet ID or URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val acc = account ?: return@Button
                            onSpreadsheetChanged(spreadsheetField)
                            syncViewModel.connectExistingSpreadsheet(acc, spreadsheetField)
                        },
                        enabled = !busy && account != null && spreadsheetField.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect spreadsheet")
                    }
                }

                SetupOptionCard(
                    title = "Create a new sheet from BGG",
                    subtitle = "Make a fresh Google Sheet and import your current BGG collection into it.",
                    selected = setupMode == SyncSetupMode.CREATE_FROM_BGG,
                    onClick = { setupMode = SyncSetupMode.CREATE_FROM_BGG }
                )

                if (setupMode == SyncSetupMode.CREATE_FROM_BGG) {
                    Button(
                        onClick = {
                            val acc = account ?: return@Button
                            syncViewModel.createSpreadsheetFromBgg(acc)
                        },
                        enabled = !busy && account != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create spreadsheet from BGG")
                    }
                }

                if (hasConfiguredSheet) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                spreadsheetTitle.ifBlank { "Connected spreadsheet" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "First sheet: $sheetTabName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "ID: $spreadsheetId",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (lastLogEntry != null && logPanelState == LogPanelState.CLOSED) {
                    LatestStatusCard(
                        entry = lastLogEntry,
                        onShow = { logPanelState = LogPanelState.EXPANDED }
                    )
                }

                HorizontalDivider()

                Button(
                    onClick = {
                        val acc = account ?: return@Button
                        onSpreadsheetChanged(spreadsheetId)
                        syncViewModel.syncBgg(acc, forceRefresh = true)
                    },
                    enabled = !busy && account != null && hasConfiguredSheet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Refresh from BGG")
                }

                Button(
                    onClick = {
                        account ?: return@Button
                        onSpreadsheetChanged(spreadsheetId)
                        onPickCsv()
                    },
                    enabled = !busy && account != null && hasConfiguredSheet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync from CSV file")
                }

                Button(
                    onClick = {
                        val acc = account ?: return@Button
                        onSpreadsheetChanged(spreadsheetId)
                        syncViewModel.createFolders(acc, saveQrToGallery = saveQrToDevice)
                    },
                    enabled = !busy && account != null && hasConfiguredSheet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create folders and QR codes")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = saveQrToDevice,
                        onCheckedChange = { saveQrToDevice = it }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Also save QR images to this device", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Turn this on only if you want the PNG files in local storage too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) {
                        OutlinedButton(
                            onClick = { syncViewModel.stopSync() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Stop")
                        }
                    }
                    OutlinedButton(
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

@Composable
private fun SetupOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LatestStatusCard(
    entry: LogEntry,
    onShow: () -> Unit
) {
    val (containerColor, contentColor) = logColors(entry)
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, color = contentColor, style = MaterialTheme.typography.labelLarge)
                if (entry.status.isNotBlank()) {
                    Text(entry.status, color = contentColor, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(Icons.Default.ExpandMore, contentDescription = "Show log", tint = contentColor)
        }
    }
}

@Composable
private fun MinimizedLogBar(
    entry: LogEntry,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    val (containerColor, contentColor) = logColors(entry)
    Surface(
        color = containerColor,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onExpand)
            ) {
                Text(entry.name, color = contentColor, style = MaterialTheme.typography.labelLarge)
                if (entry.status.isNotBlank()) {
                    Text(entry.status, color = contentColor, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onExpand) {
                Icon(Icons.Default.ExpandMore, contentDescription = "Open log", tint = contentColor)
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Hide log",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun LogDialog(
    log: List<LogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onMinimize) {
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
                    IconButton(onClick = onMinimize) {
                        Icon(Icons.Default.ExpandLess, contentDescription = "Minimize log")
                    }
                    IconButton(onClick = onClose) {
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
        LogEntry.Type.DONE -> "✓" to Color(0xFF4CAF50)
        LogEntry.Type.INSERTED -> "+" to MaterialTheme.colorScheme.primary
        LogEntry.Type.UPDATED -> "↻" to MaterialTheme.colorScheme.onSurfaceVariant
        LogEntry.Type.ERROR -> "✗" to MaterialTheme.colorScheme.error
        LogEntry.Type.HEADER -> "▶" to MaterialTheme.colorScheme.primary
        LogEntry.Type.INFO -> "·" to MaterialTheme.colorScheme.onSurfaceVariant
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
            modifier = Modifier.size(width = 14.dp, height = 14.dp)
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
private fun logColors(entry: LogEntry): Pair<Color, Color> {
    return when (entry.type) {
        LogEntry.Type.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LogEntry.Type.DONE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}
