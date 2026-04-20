package com.bgg.combined.ui.sync

import android.accounts.Account
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bgg.combined.SyncViewModel
import com.bgg.combined.model.LogEntry
import com.bgg.combined.ui.common.AnimatedDialog
import com.bgg.combined.ui.common.BoardFlowButton
import com.bgg.combined.ui.common.BoardFlowOutlinedButton
import com.bgg.combined.ui.common.SectionCard

// ── Log summary model ─────────────────────────────────────────────────────────

private data class LogSummary(val headline: String, val detail: String?, val isError: Boolean)

private fun List<LogEntry>.deriveSummary(): LogSummary? {
    if (isEmpty()) return null
    val header = firstOrNull { it.type == LogEntry.Type.HEADER }
    val result = lastOrNull { it.type == LogEntry.Type.DONE || it.type == LogEntry.Type.ERROR }
    val headline = when {
        result?.name?.contains("Collection cached", ignoreCase = true) == true -> "Collection updated"
        result?.name?.contains("Sleeve refresh", ignoreCase = true) == true -> "Sleeve data refreshed"
        result?.name?.contains("Sync complete", ignoreCase = true) == true -> "Sync complete"
        result?.name?.contains("Connected", ignoreCase = true) == true -> "Sheet connected"
        result?.name?.contains("Done", ignoreCase = true) == true -> when {
            header?.name?.contains("Folder", ignoreCase = true) == true -> "Folders ready"
            header?.name?.contains("CSV", ignoreCase = true) == true -> "CSV import complete"
            else -> "Done"
        }
        result?.type == LogEntry.Type.ERROR -> "Finished with errors"
        header?.name?.contains("Refresh", ignoreCase = true) == true -> "Refreshing…"
        header?.name?.contains("Sync", ignoreCase = true) == true -> "Syncing…"
        header?.name?.contains("Connect", ignoreCase = true) == true -> "Connecting…"
        else -> header?.name ?: "Working…"
    }

    return LogSummary(
        headline = headline,
        detail = result?.status?.ifBlank { null },
        isError = result?.type == LogEntry.Type.ERROR
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun SyncScreen(
    syncViewModel: SyncViewModel,
    onPickCsv: () -> Unit,
    onSpreadsheetChanged: (String) -> Unit,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    bggUsername: String = "",
    bggPassword: String = "",
    onSaveBggCredentials: (String, String) -> Unit = { _, _ -> }
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
        !hasBggCredentials && !googleConnected -> "Set up BGG and sign in to Google first"
        !hasBggCredentials -> "Set up your BGG account first"
        !googleConnected -> "Sign in to Google first"
        !hasConfiguredSheet -> "Connect a sheet above to sync"
        else -> null
    }

    // Compact display label for the connected sheet
    val sheetDisplayLabel = when {
        spreadsheetTitle.isNotBlank() -> spreadsheetTitle
        spreadsheetId.isNotBlank() -> "…${spreadsheetId.takeLast(8)}"
        else -> ""
    }

    var showSheetModal by remember { mutableStateOf(false) }
    var showGoogleModal by remember { mutableStateOf(false) }
    var showBggModal by remember { mutableStateOf(false) }
    var saveQrToDevice by remember { mutableStateOf(false) }
    var logDialogOpen by rememberSaveable { mutableStateOf(false) }
    var showClearLogConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

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

    if (showGoogleModal) {
        GoogleManageModal(
            accountEmail = account?.name,
            onDismiss = { showGoogleModal = false },
            onSignIn = { onSignIn(); showGoogleModal = false },
            onSignOut = { onSignOut(); showGoogleModal = false }
        )
    }

    if (showBggModal) {
        BggEditModal(
            initialUsername = bggUsername,
            initialPassword = bggPassword,
            onDismiss = { showBggModal = false },
            onSave = { u, p ->
                onSaveBggCredentials(u, p)
                showBggModal = false
            }
        )
    }

    if (logDialogOpen && log.isNotEmpty()) {
        LogDialog(log = log, listState = listState, onDismiss = { logDialogOpen = false })
    }

    if (showClearLogConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLogConfirm = false },
            title = { Text("Clear Log") },
            text = { Text("Remove all entries from the local sync log?") },
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
            if (log.isNotEmpty()) {
                LogBar(log = log, busy = busy, onClick = { logDialogOpen = true })
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
                // ── Readiness hub (status + actions) ──────────────────────
                ReadinessHub(
                    googleConnected = googleConnected,
                    googleLabel = account?.name.orEmpty(),
                    bggConnected = hasBggCredentials,
                    sheetConnected = hasConfiguredSheet,
                    sheetLabel = sheetDisplayLabel,
                    onManageGoogle = { showGoogleModal = true },
                    onEditBgg = { showBggModal = true },
                    onChangeSheet = { showSheetModal = true }
                )

                // ── Step 1 — BGG ──────────────────────────────────────────
                StepSectionHeader(
                    step = "1",
                    title = "BoardGameGeek",
                    subtitle = "Fetch your latest collection from BGG."
                )
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
                            Text("Refresh Sleeve Sizes")
                        }
                        if (!hasBggCredentials) {
                            InlineHint("Set up your BGG account", onClick = { showBggModal = true })
                        }
                    }
                }

                HorizontalDivider()

                // ── Step 2 — Google Sheets ────────────────────────────────
                StepSectionHeader(
                    step = "2",
                    title = "Google Sheets",
                    subtitle = "Push your collection to the connected spreadsheet."
                )
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            InlineHint(
                                text = syncHint,
                                onClick = when {
                                    !googleConnected -> { { showGoogleModal = true } }
                                    !hasBggCredentials -> { { showBggModal = true } }
                                    else -> null
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ── Advanced (collapsed) ──────────────────────────────────
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
private fun ReadinessHub(
    googleConnected: Boolean,
    googleLabel: String,
    bggConnected: Boolean,
    sheetConnected: Boolean,
    sheetLabel: String,
    onManageGoogle: () -> Unit,
    onEditBgg: () -> Unit,
    onChangeSheet: () -> Unit
) {
    SectionCard {
        Column {
            ActionStatusRow(
                label = "Google",
                connected = googleConnected,
                detail = if (googleConnected) googleLabel else "Not signed in",
                actionLabel = if (googleConnected) "Manage" else "Sign in",
                onAction = onManageGoogle
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
            ActionStatusRow(
                label = "BGG",
                connected = bggConnected,
                detail = if (bggConnected) "Account saved" else "Not set up",
                actionLabel = if (bggConnected) "Edit" else "Set up",
                onAction = onEditBgg
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
            ActionStatusRow(
                label = "Sheet",
                connected = sheetConnected,
                detail = if (sheetConnected) sheetLabel else "No sheet selected",
                actionLabel = when {
                    sheetConnected -> "Change"
                    googleConnected -> "Connect"
                    else -> null
                },
                onAction = if (sheetConnected || googleConnected) onChangeSheet else null
            )
        }
    }
}

@Composable
private fun ActionStatusRow(
    label: String,
    connected: Boolean,
    detail: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (connected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (connected) Color(0xFF4CAF50)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelMedium,
            color = if (connected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onAction != null) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.clickable(onClick = onAction)
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun StepSectionHeader(step: String, title: String, subtitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        step,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp)
            )
        }
    }
}

@Composable
private fun InlineHint(text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onClick != null) {
            Text(
                "→",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
                    AdvancedGroupLabel("Import & Export")
                    BoardFlowOutlinedButton(
                        onClick = onPickCsv,
                        enabled = !busy && account != null && hasConfiguredSheet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import from CSV")
                    }

                    AdvancedGroupLabel("Automation")
                    BoardFlowOutlinedButton(
                        onClick = onCreateFolders,
                        enabled = !busy && account != null && hasConfiguredSheet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Drive Folders & QR Codes")
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
                                "Enable this to copy QR PNG files into local storage.",
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
private fun AdvancedGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LogBar(log: List<LogEntry>, busy: Boolean, onClick: () -> Unit) {
    val summary = log.deriveSummary() ?: return
    val (containerColor, contentColor) = when {
        busy -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        summary.isError -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        color = containerColor,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(summary.headline, color = contentColor, style = MaterialTheme.typography.labelLarge)
                summary.detail?.let {
                    Text(it, color = contentColor.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "View details",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.65f)
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "View log details",
                tint = contentColor.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LogDialog(
    log: List<LogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDismiss: () -> Unit
) {
    val summary = log.deriveSummary()

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
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Last operation",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                // User-facing result summary
                if (summary != null) {
                    val summaryContainer = if (summary.isError)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                    val summaryContent = if (summary.isError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = summaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                summary.headline,
                                style = MaterialTheme.typography.titleSmall,
                                color = summaryContent
                            )
                            summary.detail?.let { detail ->
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = summaryContent.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Details label
                Text(
                    "Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
                )

                // Raw log entries
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
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

// ── Account modals ────────────────────────────────────────────────────────────

@Composable
private fun GoogleManageModal(
    accountEmail: String?,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "Google Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                if (accountEmail != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Signed in as",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                accountEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    item {
                        BoardFlowOutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign out")
                        }
                    }
                } else {
                    item {
                        Text(
                            "Sign in to enable Google Sheets sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        BoardFlowButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign in with Google")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BggEditModal(
    initialUsername: String,
    initialPassword: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var showPwd by remember { mutableStateOf(false) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "BGG Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Username",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                placeholder = { Text("e.g. boardgamer42") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Password",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
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
                item {
                    BoardFlowButton(
                        onClick = { onSave(username.trim(), password.trim()) },
                        enabled = username.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
