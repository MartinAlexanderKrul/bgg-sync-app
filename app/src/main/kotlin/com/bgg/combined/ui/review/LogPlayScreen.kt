package com.bgg.combined.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bgg.combined.AppViewModel
import com.bgg.combined.model.BggGame
import com.bgg.combined.model.GameRelations
import com.bgg.combined.model.Player as BggPlayer
import com.bgg.combined.model.PlayerResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogPlayScreen(
    viewModel: AppViewModel,
    onPosted: () -> Unit,
    onNavigateBack: () -> Unit,
    onDiscard: () -> Unit = onNavigateBack
) {
    val players         by viewModel.editablePlayers.collectAsState()
    val posting         by viewModel.postLoading.collectAsState()
    val extractedPlay   by viewModel.extractedPlay.collectAsState()
    val gameRelations   by viewModel.gameRelations.collectAsState()
    val additionalGames by viewModel.additionalGames.collectAsState()

    var date           by remember { mutableStateOf(LocalDate.now().toString()) }
    var duration       by remember { mutableStateOf("") }
    var location       by remember { mutableStateOf("") }
    var comments       by remember { mutableStateOf("") }
    var errorMsg       by remember { mutableStateOf<String?>(null) }
    var showAiOutput   by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching {
            LocalDate.parse(LocalDate.now().toString())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val gameName = viewModel.selectedGame?.name ?: "Unknown game"

    LaunchedEffect(extractedPlay?.date) {
        extractedPlay?.date?.takeIf { it.isNotBlank() }?.let { date = it }
    }

    // FAB label updates when additional games are queued
    val online = viewModel.isOnline()
    val totalGames = 1 + additionalGames.size
    val fabLabel = when {
        posting               -> "Posting..."
        !online && totalGames > 1 -> "Save $totalGames plays locally"
        !online               -> "Save Play Locally"
        totalGames > 1        -> "Log $totalGames plays to BGG"
        else                  -> "Log Play to BGG"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(fabLabel) },
                icon = { Icon(Icons.Default.EmojiEvents, null) },
                onClick = {
                    if (!posting) {
                        errorMsg = null
                        viewModel.postPlay(
                            date            = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now()),
                            durationMinutes = duration.toIntOrNull() ?: 0,
                            location        = location,
                            comments        = comments,
                            onSuccess       = { onPosted() },
                            onError         = { errorMsg = it }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Related games banner
            gameRelations?.let { relations ->
                val relatedGames = if (relations.isExpansion) relations.baseGames else relations.expansions
                if (relatedGames.isNotEmpty()) {
                    item {
                        RelatedGamesBanner(
                            relations       = relations,
                            additionalGames = additionalGames,
                            onToggleGame    = { viewModel.toggleAdditionalGame(it) }
                        )
                    }
                }
            }

            // Play details card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Play Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Date + Duration
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Date",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = date,
                                    onValueChange = {},
                                    readOnly = true,
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { showDatePicker = true }) {
                                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(
                                modifier = Modifier.width(100.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Duration (min)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = duration,
                                    onValueChange = { duration = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Location",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                placeholder = { Text("e.g. Game Night HQ") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = comments,
                                onValueChange = { comments = it },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Players section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Players", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (extractedPlay != null) {
                            TextButton(onClick = { showAiOutput = !showAiOutput }) {
                                Text(
                                    if (showAiOutput) "Hide AI output" else "AI output",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.addPlayer() }) {
                            Icon(Icons.Default.Add, "Add player")
                        }
                    }
                }
            }

            // AI output debug card
            if (showAiOutput && extractedPlay != null) {
                item {
                    val clipboardManager = LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Raw AI response",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(extractedPlay!!.rawText))
                                    copied = true
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (copied) "Copied!" else "Copy")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            SelectionContainer {
                                Text(
                                    text = extractedPlay!!.rawText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Player rows
            itemsIndexed(players) { index, player ->
                PlayerRow(
                    player      = player,
                    onUpdate    = { viewModel.updatePlayer(index, it) },
                    onRemove    = { viewModel.removePlayer(index) },
                    suggestions = remember(player.name) { viewModel.getPlayerSuggestions(player.name) }
                )
            }

            // Error card
            if (errorMsg != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text  = errorMsg!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Space for FAB
            item { Spacer(Modifier.height(80.dp)) }
            } // end LazyColumn
        } // end Column
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedGamesBanner(
    relations: GameRelations,
    additionalGames: List<BggGame>,
    onToggleGame: (BggGame) -> Unit
) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    val relatedGames = if (relations.isExpansion) relations.baseGames else relations.expansions
    val label = if (relations.isExpansion) "Expansion - also post for base game?"
                else "Also post for an expansion?"

    val anySelected = relatedGames.any { game -> additionalGames.any { it.id == game.id } }

    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant,
        shape  = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { dismissed = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp)
            ) {
                relatedGames.take(6).forEach { game ->
                    val selected = additionalGames.any { it.id == game.id }
                    FilterChip(
                        selected = selected,
                        onClick  = { onToggleGame(game) },
                        label    = { Text(game.name, style = MaterialTheme.typography.labelMedium) }
                    )
                }
                if (relatedGames.size > 6) {
                    Text(
                        "& ${relatedGames.size - 6} more",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            // Hint shown only when at least one game is ticked
            if (anySelected) {
                Text(
                    "Ticked games will be posted automatically with the same players & scores.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerResult,
    onUpdate: (PlayerResult) -> Unit,
    onRemove: () -> Unit,
    suggestions: List<BggPlayer> = emptyList()
) {
    // Only show suggestions that aren't an exact match for current input
    val activeSuggestions: List<BggPlayer> = remember(suggestions, player.name) {
        suggestions.filter { it.displayName.lowercase() != player.name.lowercase().trim() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Label row aligned to each field's width
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Score",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(90.dp)
                )
                // Spacer to account for the two icon buttons (2 x 48dp)
                Spacer(Modifier.width(96.dp))
            }
            // Input row with icons centered to the text fields
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = player.name,
                    onValueChange = { onUpdate(player.copy(name = it)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = player.score,
                    onValueChange = { onUpdate(player.copy(score = it)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(90.dp)
                )
                IconButton(onClick = { onUpdate(player.copy(isWinner = !player.isWinner)) }) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = "Toggle winner",
                        tint = if (player.isWinner) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Fuzzy-match suggestions as chips
            if (activeSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Match:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    activeSuggestions.forEach { p ->
                        SuggestionChip(
                            onClick = { onUpdate(player.copy(name = p.displayName)) },
                            label = { Text(p.displayName, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
    }
}
