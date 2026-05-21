@file:OptIn(ExperimentalMaterial3Api::class)

package cz.nicolsburg.boardflow.ui.history

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.data.PlayShareSerializer
import cz.nicolsburg.boardflow.data.SessionShareSerializer
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.PlayerResult
import cz.nicolsburg.boardflow.ui.common.BoardFlowCameraActionPanel
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowCameraSecondaryAction
import cz.nicolsburg.boardflow.ui.common.BoardFlowCameraPermissionPrompt
import cz.nicolsburg.boardflow.ui.common.BoardFlowCameraScene
import cz.nicolsburg.boardflow.ui.common.BoardFlowSecondaryButton
import cz.nicolsburg.boardflow.util.toFlexibleLocalDateOrNull
import cz.nicolsburg.boardflow.ui.common.PlayerResultEditorCard
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrPlayImportScreen(
    viewModel: AppViewModel,
    initialRawData: String? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingPlay by viewModel.pendingImportedPlay.collectAsState()
    val pendingSession by viewModel.pendingImportedSession.collectAsState()
    val rosterPlayers by viewModel.players.collectAsState()
    val collection by viewModel.collection.collectAsState()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    var decodeError by rememberSaveable { mutableStateOf<String?>(null) }
    var isParsing by rememberSaveable { mutableStateOf(false) }
    var hasHandledScan by rememberSaveable { mutableStateOf(false) }

    fun handleRawQr(raw: String) {
        if (SessionShareSerializer.isSessionQr(raw)) {
            SessionShareSerializer.decode(raw)
                .onSuccess { viewModel.setPendingImportedSession(it) }
                .onFailure {
                    decodeError = it.message ?: "Could not read BoardFlow session data."
                    hasHandledScan = false
                }
        } else {
            PlayShareSerializer.decode(raw)
                .onSuccess { viewModel.setPendingImportedPlay(it) }
                .onFailure {
                    decodeError = it.message ?: "Could not read BoardFlow play data."
                    hasHandledScan = false
                }
        }
    }

    LaunchedEffect(initialRawData) {
        val raw = initialRawData?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (pendingPlay != null || pendingSession != null || hasHandledScan) return@LaunchedEffect
        hasHandledScan = true
        isParsing = true
        decodeError = null
        handleRawQr(raw)
        isParsing = false
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        isParsing = true
        decodeError = null
        runCatching { decodeQrFromUri(context, uri) }
            .onSuccess { raw ->
                hasHandledScan = true
                handleRawQr(raw)
            }
            .onFailure { decodeError = it.message ?: "Could not decode QR from image." }
        isParsing = false
    }

    if (pendingSession != null) {
        QrSessionImportReview(
            plays = pendingSession!!,
            onCancel = {
                viewModel.clearPendingImportedSession()
                onCancel()
            },
            onSave = {
                viewModel.saveImportedSession(
                    plays = pendingSession!!,
                    onSuccess = onDone,
                    onError = { decodeError = it }
                )
            },
            errorMessage = decodeError
        )
        return
    }

    if (pendingPlay != null) {
        QrPlayImportReview(
            play = pendingPlay!!,
            rosterPlayers = rosterPlayers,
            collectionNames = collection.associateBy({ it.id }, { it.name }),
            onCancel = {
                viewModel.clearPendingImportedPlay()
                onCancel()
            },
            onSave = { updatedPlay ->
                viewModel.saveImportedPlay(
                    play = updatedPlay,
                    onSuccess = onDone,
                    onError = { decodeError = it }
                )
            },
            errorMessage = decodeError
        )
        return
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                cameraPermission.status.isGranted -> {
                    BoardFlowCameraScene(
                        title = "Import BoardFlow play",
                        subtitle = "Point the camera at a shared BoardFlow play QR code.",
                        preview = {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).also { previewView ->
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val analyzer = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                            analyzer.setAnalyzer(
                                                ContextCompat.getMainExecutor(ctx),
                                                QrCodeAnalyzer(
                                                    onQrDetected = { raw ->
                                                        if (hasHandledScan || isParsing) return@QrCodeAnalyzer
                                                        hasHandledScan = true
                                                        isParsing = true
                                                        decodeError = null
                                                        handleRawQr(raw)
                                                        isParsing = false
                                                    }
                                                )
                                            )
                                            try {
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                    preview,
                                                    analyzer
                                                )
                                            } catch (_: Exception) {
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        bottomContent = {
                            BoardFlowCameraActionPanel(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 20.dp, vertical = 24.dp),
                                status = decodeError ?: "Scanning automatically",
                                secondaryActions = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        BoardFlowCameraSecondaryAction(
                                            icon = Icons.Default.Photo,
                                            label = "From image",
                                            onClick = { galleryLauncher.launch("image/*") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        BoardFlowCameraSecondaryAction(
                                            icon = Icons.Default.QrCodeScanner,
                                            label = "Cancel",
                                            onClick = onCancel,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            )
                        },
                        overlayContent = {
                            if (isParsing) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    )
                }
                else -> {
                    LaunchedEffect(Unit) { cameraPermission.launchPermissionRequest() }
                    BoardFlowCameraPermissionPrompt(
                        message = "Camera access is needed to scan shared play QR codes.",
                        icon = {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        actions = {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BoardFlowButton(onClick = { cameraPermission.launchPermissionRequest() }) {
                                    Text("Allow camera")
                                }
                                BoardFlowSecondaryButton(onClick = { galleryLauncher.launch("image/*") }) {
                                    Text("Use image")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QrSessionImportReview(
    plays: List<LoggedPlay>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    errorMessage: String?
) {
    val gameNames = remember(plays) {
        plays.map { it.gameName }.distinct()
    }
    val totalPlays = plays.sumOf { it.quantity.coerceAtLeast(1) }
    val date = plays.firstOrNull()?.date ?: ""
    val location = plays.firstOrNull()?.location?.takeIf { it.isNotBlank() }
    val allPlayerNames = remember(plays) {
        plays.flatMap { it.players }.map { it.name.trim() }.filter { it.isNotBlank() }.distinct()
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Import Session", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (gameNames.size == 1) gameNames.first() else "${gameNames.size} games",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            buildString {
                                append("$totalPlays play${if (totalPlays != 1) "s" else ""}")
                                if (date.isNotBlank()) append("  •  $date")
                                if (location != null) append("  •  $location")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (allPlayerNames.isNotEmpty()) {
                            Text(
                                allPlayerNames.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(plays, key = { it.id }) { play ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(play.gameName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            buildString {
                                append("${play.players.size} players")
                                if (play.durationMinutes > 0) append("  •  ${play.durationMinutes} min")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardFlowButton(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save $totalPlays play${if (totalPlays != 1) "s" else ""} to local history")
                    }
                    BoardFlowSecondaryButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrPlayImportReview(
    play: LoggedPlay,
    rosterPlayers: List<Player>,
    collectionNames: Map<Int, String>,
    onCancel: () -> Unit,
    onSave: (LoggedPlay) -> Unit,
    errorMessage: String?
) {
    var date by rememberSaveable(play.id) { mutableStateOf(play.date) }
    var duration by rememberSaveable(play.id) { mutableStateOf(if (play.durationMinutes > 0) play.durationMinutes.toString() else "") }
    var location by rememberSaveable(play.id) { mutableStateOf(play.location) }
    var comments by rememberSaveable(play.id) { mutableStateOf(play.comments) }
    var quantity by rememberSaveable(play.id) { mutableStateOf(play.quantity.toString()) }
    var incomplete by rememberSaveable(play.id) { mutableStateOf(play.incomplete) }
    var nowInStats by rememberSaveable(play.id) { mutableStateOf(play.nowInStats) }
    var editPlayers by rememberSaveable(play.id, stateSaver = ImportPlayerResultListSaver) { mutableStateOf(play.players) }
    var collapsedPlayers by rememberSaveable(play.id) { mutableStateOf(List(play.players.size) { false }) }
    var playerRowKeys by rememberSaveable(play.id) { mutableStateOf(List(play.players.size) { java.util.UUID.randomUUID().toString() }) }
    var showDatePicker by rememberSaveable(play.id) { mutableStateOf(false) }
    var showNotes by rememberSaveable(play.id) { mutableStateOf(play.comments.isNotBlank()) }

    val matchedGameName = remember(play.gameId, play.gameName, collectionNames) {
        collectionNames[play.gameId]?.takeIf { it.equals(play.gameName, ignoreCase = true) }
            ?: collectionNames[play.gameId]
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching {
            date.toFlexibleLocalDateOrNull()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    LaunchedEffect(editPlayers.size) {
        if (collapsedPlayers.size != editPlayers.size) {
            collapsedPlayers = List(editPlayers.size) { index -> collapsedPlayers.getOrElse(index) { false } }
        }
        if (playerRowKeys.size != editPlayers.size) {
            playerRowKeys = List(editPlayers.size) { index -> playerRowKeys.getOrElse(index) { java.util.UUID.randomUUID().toString() } }
        }
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Import Play", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(play.gameName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f)
                        ) {
                            Text(
                                matchedGameName?.let { "Matched to collection: $it" } ?: "Not currently matched to a collection game. This will still save locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImportField(
                                value = date,
                                onValueChange = {},
                                label = "Date",
                                readOnly = true,
                                modifier = Modifier.weight(1.3f),
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date", modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                            ImportField(
                                value = duration,
                                onValueChange = { duration = it.filter(Char::isDigit) },
                                label = "Duration",
                                modifier = Modifier.weight(0.7f),
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImportField(
                                value = quantity,
                                onValueChange = { quantity = it.filter(Char::isDigit) },
                                label = "Quantity",
                                modifier = Modifier.width(120.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            ImportField(
                                value = location,
                                onValueChange = { location = it },
                                label = "Location",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SmallToggleCard(
                                label = "Incomplete",
                                selected = incomplete,
                                onClick = { incomplete = !incomplete },
                                modifier = Modifier.weight(1f)
                            )
                            SmallToggleCard(
                                label = "Count in stats",
                                selected = nowInStats,
                                onClick = { nowInStats = !nowInStats },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (showNotes || comments.isNotBlank()) {
                            ImportField(
                                value = comments,
                                onValueChange = { comments = it },
                                label = "Notes",
                                singleLine = false,
                                minLines = 3,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            TextButton(onClick = { showNotes = true }, contentPadding = PaddingValues(0.dp)) {
                                Text("+ Add notes")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Players", style = MaterialTheme.typography.titleSmall)
                    BoardFlowSecondaryButton(
                        onClick = {
                            editPlayers = editPlayers + PlayerResult("", "0", false)
                            collapsedPlayers = collapsedPlayers + false
                            playerRowKeys = playerRowKeys + java.util.UUID.randomUUID().toString()
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                }
            }

            itemsIndexed(editPlayers, key = { index, _ -> playerRowKeys.getOrElse(index) { "import-player-$index" } }) { index, player ->
                PlayerResultEditorCard(
                    player = player,
                    rosterPlayers = rosterPlayers,
                    onUpdate = { updated -> editPlayers = editPlayers.toMutableList().also { it[index] = updated } },
                    onRemove = {
                        editPlayers = editPlayers.toMutableList().also { it.removeAt(index) }
                        collapsedPlayers = collapsedPlayers.toMutableList().also { it.removeAt(index) }
                        playerRowKeys = playerRowKeys.toMutableList().also { it.removeAt(index) }
                    },
                    collapsed = collapsedPlayers.getOrElse(index) { false },
                    onToggleCollapsed = {
                        collapsedPlayers = collapsedPlayers.toMutableList().also { it[index] = !it[index] }
                    }
                )
            }

            errorMessage?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardFlowButton(
                        onClick = {
                            val normalizedDate = date.toFlexibleLocalDateOrNull()?.toString() ?: date
                            onSave(
                                play.copy(
                                    date = normalizedDate,
                                    durationMinutes = duration.toIntOrNull() ?: 0,
                                    location = location,
                                    comments = comments,
                                    quantity = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                    incomplete = incomplete,
                                    nowInStats = nowInStats,
                                    players = editPlayers
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save to local history")
                    }
                    BoardFlowSecondaryButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun SmallToggleCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private class QrCodeAnalyzer(
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        val result = runCatching { decodeQrFromImageProxy(image) }.getOrNull()
        image.close()
        if (!result.isNullOrBlank()) {
            onQrDetected(result)
        }
    }

    private fun decodeQrFromImageProxy(image: ImageProxy): String? {
        val nv21 = image.toNv21()
        val source = PlanarYUVLuminanceSource(
            nv21,
            image.width,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false
        )
        return runCatching {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        }.getOrNull()
    }
}

private fun decodeQrFromUri(context: Context, uri: Uri): String {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: error("Could not read image.")
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    return reader.decode(BinaryBitmap(HybridBinarizer(source))).text
}

private fun ImageProxy.toNv21(): ByteArray {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)

    val chromaRowStride = planes[1].rowStride
    val chromaPixelStride = planes[1].pixelStride
    val width = width
    val height = height
    var outputOffset = ySize

    val uBytes = ByteArray(uSize).also { uBuffer.get(it) }
    val vBytes = ByteArray(vSize).also { vBuffer.get(it) }

    for (row in 0 until height / 2) {
        for (col in 0 until width / 2) {
            val index = row * chromaRowStride + col * chromaPixelStride
            nv21[outputOffset++] = vBytes[index]
            nv21[outputOffset++] = uBytes[index]
        }
    }
    return nv21
}

private val ImportPlayerResultListSaver: Saver<List<PlayerResult>, Any> = listSaver(
    save = { players ->
        players.map {
            listOf(
                it.name,
                it.score,
                it.isWinner.toString(),
                it.color,
                it.rating,
                it.isNew.toString()
            )
        }
    },
    restore = { restored ->
        restored.map { item ->
            val values = item as List<*>
            PlayerResult(
                name = values.getOrNull(0) as? String ?: "",
                score = values.getOrNull(1) as? String ?: "",
                isWinner = (values.getOrNull(2) as? String).toBoolean(),
                color = values.getOrNull(3) as? String ?: "",
                rating = values.getOrNull(4) as? String ?: "",
                isNew = (values.getOrNull(5) as? String).toBoolean()
            )
        }
    }
)
