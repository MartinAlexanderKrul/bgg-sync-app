package com.bgg.combined.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bgg.combined.SyncViewModel
import com.bgg.combined.model.LogEntry
import com.bgg.combined.model.SavedSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SyncScreen(
    syncViewModel: SyncViewModel,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPickCsv: () -> Unit,
    onSpreadsheetChanged: (String) -> Unit,
    onTabNameChanged: (String) -> Unit
) {
    val account        by syncViewModel.account.collectAsState()
    val spreadsheetId  by syncViewModel.spreadsheetId.collectAsState()
    val sheetTabName   by syncViewModel.sheetTabName.collectAsState()
    val log            by syncViewModel.log.collectAsState()
    val busy           by syncViewModel.busy.collectAsState()
    val savedSheets    by syncViewModel.savedSheets.collectAsState()

    var spreadsheetField by remember(spreadsheetId) { mutableStateOf(spreadsheetId) }
    var tabNameField     by remember(sheetTabName)  { mutableStateOf(sheetTabName) }
    var showSaveDialog   by remember { mutableStateOf(false) }
    var saveSheetName    by remember { mutableStateOf("") }

    val listState    = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll log to bottom when new entries arrive
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Spreadsheet") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("…${spreadsheetField.takeLast(20)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = saveSheetName, onValueChange = { saveSheetName = it },
                        label = { Text("Name (optional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    syncViewModel.saveSheet(spreadsheetField, saveSheetName)
                    showSaveDialog = false; saveSheetName = ""
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Strip ─────────────────────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    if (account != null) "Signed in as ${account!!.email ?: "Google"}"
                    else "Sign in to sync with Google Sheets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Google Account ────────────────────────────────────────
                if (account == null) {
                    Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in with Google")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✓  ${account!!.email ?: "Signed in"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = onSignOut) { Text("Sign out") }
                    }
                }

                // ── Spreadsheet ID ────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = spreadsheetField,
                        onValueChange = { spreadsheetField = it },
                        label = { Text("Spreadsheet ID or URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        onFocusChangedBehavior = {
                            onSpreadsheetChanged(spreadsheetField)
                        }
                    )
                    IconButton(
                        onClick = { showSaveDialog = true; saveSheetName = "" },
                        enabled = spreadsheetField.isNotBlank()
                    ) { Icon(Icons.Default.Save, contentDescription = "Save spreadsheet") }
                }

                // ── Sheet tab name ────────────────────────────────────────
                OutlinedTextField(
                    value = tabNameField,
                    onValueChange = { tabNameField = it; onTabNameChanged(it) },
                    label = { Text("Sheet Tab Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── Saved sheet chips ─────────────────────────────────────
                if (savedSheets.isNotEmpty()) {
                    Text("Saved spreadsheets", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        savedSheets.forEach { sheet ->
                            InputChip(
                                selected = spreadsheetField == sheet.id,
                                onClick  = {
                                    spreadsheetField = sheet.id
                                    onSpreadsheetChanged(sheet.id)
                                },
                                label    = { Text(sheet.displayLabel) },
                                trailingIcon = {
                                    IconButton(onClick = { syncViewModel.deleteSheet(sheet.id) },
                                        modifier = Modifier.size(18.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove",
                                            modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ── Action buttons ────────────────────────────────────────
                Button(
                    onClick = {
                        val acc = account ?: return@Button
                        onSpreadsheetChanged(spreadsheetField)
                        syncViewModel.setSpreadsheetId(spreadsheetField)
                        onPickCsv()
                    },
                    enabled = !busy && account != null && spreadsheetField.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("📄  Sync from CSV file…") }

                Button(
                    onClick = {
                        val acc = account ?: return@Button
                        onSpreadsheetChanged(spreadsheetField)
                        syncViewModel.setSpreadsheetId(spreadsheetField)
                        syncViewModel.createFolders(acc)
                    },
                    enabled = !busy && account != null && spreadsheetField.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("📁  Create folders & QR codes") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) {
                        OutlinedButton(
                            onClick = { syncViewModel.stopSync() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text("Stop")
                        }
                    }
                    OutlinedButton(
                        onClick = { syncViewModel.clearLog() },
                        enabled = log.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear Log") }
                }

                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // ── Log ───────────────────────────────────────────────────────
            if (log.isNotEmpty()) {
                HorizontalDivider()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
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
        LogEntry.Type.DONE     -> "✓" to Color(0xFF4CAF50)
        LogEntry.Type.INSERTED -> "+" to MaterialTheme.colorScheme.primary
        LogEntry.Type.UPDATED  -> "↻" to MaterialTheme.colorScheme.onSurfaceVariant
        LogEntry.Type.ERROR    -> "✗" to MaterialTheme.colorScheme.error
        LogEntry.Type.HEADER   -> "▶" to MaterialTheme.colorScheme.primary
        LogEntry.Type.INFO     -> "·" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(iconText, color = iconColor, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name,
                style = if (entry.type == LogEntry.Type.HEADER)
                    MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.bodySmall,
                fontWeight = if (entry.type == LogEntry.Type.HEADER) FontWeight.Bold else FontWeight.Normal,
                color = if (entry.type == LogEntry.Type.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
            if (entry.status.isNotBlank()) {
                Text(entry.status, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Thin wrapper to handle focus-change commit on the spreadsheet field
@Composable
private fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier,
    onFocusChangedBehavior: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = singleLine,
        modifier = modifier
    )
}
