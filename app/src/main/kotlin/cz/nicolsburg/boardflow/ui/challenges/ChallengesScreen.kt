package cz.nicolsburg.boardflow.ui.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.model.Challenge
import cz.nicolsburg.boardflow.model.ChallengeProgress
import cz.nicolsburg.boardflow.model.ChallengeType
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationKind
import cz.nicolsburg.boardflow.ui.common.BoardFlowFilterChip
import cz.nicolsburg.boardflow.ui.common.BoardFlowIconButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowOutlinedButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Composable
fun ChallengesTabContent(
    progressList: List<ChallengeProgress>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    if (progressList.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No challenges yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap + to set a goal and track progress against your play history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(progressList, key = { it.challenge.id }) { progress ->
                ChallengeCard(
                    progress = progress,
                    onDelete = { onDelete(progress.challenge.id) }
                )
            }
        }
    }
}

@Composable
private fun ChallengeCard(
    progress: ChallengeProgress,
    onDelete: () -> Unit
) {
    val challenge = progress.challenge
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        BoardFlowConfirmationDialog(
            title = "Delete challenge?",
            message = "\"${challenge.title}\" will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.DESTRUCTIVE,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (progress.isComplete)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        challenge.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        challengeDescription(challenge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${progress.currentCount} / ${progress.goalCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (progress.isComplete)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (progress.isComplete)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            if (progress.isComplete) {
                Text(
                    "Complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    progress.remainingText ?: "${progress.goalCount - progress.currentCount} more to go",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun challengeDescription(challenge: Challenge): String = when (challenge.type) {
    ChallengeType.PLAY_N_TIMES -> "Play ${challenge.targetCount} times"
    ChallengeType.PLAY_SPECIFIC_GAME ->
        "Play ${challenge.gameName ?: "a game"} ${challenge.targetCount} times"
    ChallengeType.PLAY_N_DISTINCT ->
        "Play ${challenge.targetCount} different games"
    ChallengeType.PLAYER_WIN_STREAK -> {
        val playerLabel = challenge.playerNames.firstOrNull()?.takeIf { it.isNotBlank() } ?: "a player"
        "Reach a ${challenge.targetCount}-win streak with $playerLabel"
    }
    ChallengeType.PLAY_WITH_GROUP_N_TIMES -> {
        val names = challenge.playerNames.filter { it.isNotBlank() }
        val label = when {
            names.isEmpty() -> "this group"
            names.size <= 3 -> names.joinToString(", ")
            else -> names.take(3).joinToString(", ") + " +${names.size - 3}"
        }
        "Play ${challenge.targetCount} times with $label"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateChallengeDialog(
    collectionItems: List<GameItem>,
    players: List<Player>,
    onDismiss: () -> Unit,
    onCreate: (Challenge) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(ChallengeType.PLAY_N_TIMES) }
    var targetCount by rememberSaveable { mutableStateOf("10") }
    var gameQuery by rememberSaveable { mutableStateOf("") }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    var playerQuery by rememberSaveable { mutableStateOf("") }
    var selectedPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var debouncedQuery by remember { mutableStateOf("") }
    var debouncedPlayerQuery by remember { mutableStateOf("") }

    LaunchedEffect(gameQuery) {
        delay(400)
        debouncedQuery = gameQuery
    }

    LaunchedEffect(playerQuery) {
        delay(250)
        debouncedPlayerQuery = playerQuery
    }

    val filteredGames = remember(debouncedQuery, selectedGame, collectionItems) {
        if (debouncedQuery.length < 2 || selectedGame != null) emptyList()
        else collectionItems.filter { it.name.contains(debouncedQuery, ignoreCase = true) }.take(8)
    }

    val target = targetCount.toIntOrNull() ?: 0
    val gameOk = selectedType != ChallengeType.PLAY_SPECIFIC_GAME || selectedGame != null
    val requiresSinglePlayer = selectedType == ChallengeType.PLAYER_WIN_STREAK
    val requiresGroupPlayers = selectedType == ChallengeType.PLAY_WITH_GROUP_N_TIMES

    val filteredPlayers = remember(debouncedPlayerQuery, players, selectedPlayers) {
        val query = debouncedPlayerQuery.trim()
        if (query.length < 2 || (requiresSinglePlayer && selectedPlayers.isNotEmpty())) emptyList()
        else players.filter { player ->
            selectedPlayers.none { it.id == player.id } &&
                (player.displayName.contains(query, ignoreCase = true) ||
                    player.aliases.any { it.contains(query, ignoreCase = true) })
        }.take(8)
    }
    val playerOk = when {
        requiresSinglePlayer -> selectedPlayers.size == 1
        requiresGroupPlayers -> selectedPlayers.size >= 2
        else -> true
    }

    fun String.toInitialMillis(): Long = runCatching {
        LocalDate.parse(this).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    if (showStartDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate.toInitialMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    if (showEndDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate.toInitialMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        endDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "New Challenge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Play 10 games this month") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Goal type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                    ChallengeType.entries.forEach { type ->
                        val isSelected = type == selectedType
                        Card(
                            onClick = {
                                selectedType = type
                                if (type != ChallengeType.PLAY_SPECIFIC_GAME) {
                                    selectedGame = null
                                    gameQuery = ""
                                }
                                if (type != ChallengeType.PLAYER_WIN_STREAK && type != ChallengeType.PLAY_WITH_GROUP_N_TIMES) {
                                    selectedPlayers = emptyList()
                                    playerQuery = ""
                                } else if (type == ChallengeType.PLAYER_WIN_STREAK && selectedPlayers.size > 1) {
                                    selectedPlayers = selectedPlayers.take(1)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    Color(0xFFF0A500).copy(alpha = 0.10f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFF0A500).copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFF0A500)
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            if (selectedType == ChallengeType.PLAY_SPECIFIC_GAME) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = selectedGame?.name ?: gameQuery,
                            onValueChange = {
                                gameQuery = it
                                selectedGame = null
                            },
                            label = { Text("Game") },
                            placeholder = { Text("Search your collection...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        filteredGames.forEach { game ->
                            Card(
                                onClick = {
                                    selectedGame = game
                                    gameQuery = game.name
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF0A500).copy(alpha = 0.08f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    Color(0xFFF0A500).copy(alpha = 0.30f)
                                )
                            ) {
                                Text(
                                    text = game.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (requiresSinglePlayer || requiresGroupPlayers) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (requiresSinglePlayer) selectedPlayers.firstOrNull()?.displayName ?: playerQuery else playerQuery,
                            onValueChange = { playerQuery = it },
                            label = { Text(if (requiresSinglePlayer) "Player" else "Players") },
                            placeholder = {
                                Text(
                                    if (requiresSinglePlayer) "Search your roster..."
                                    else "Build a regular table group..."
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (selectedPlayers.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedPlayers.forEach { player ->
                                    BoardFlowFilterChip(
                                        selected = true,
                                        onClick = {
                                            selectedPlayers = selectedPlayers.filterNot { it.id == player.id }
                                        },
                                        label = { Text(player.displayName) }
                                    )
                                }
                            }
                        }
                        filteredPlayers.forEach { player ->
                            Card(
                                onClick = {
                                    selectedPlayers = if (requiresSinglePlayer) listOf(player)
                                    else (selectedPlayers + player).distinctBy { it.id }
                                    playerQuery = if (requiresSinglePlayer) player.displayName else ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF0A500).copy(alpha = 0.08f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    Color(0xFFF0A500).copy(alpha = 0.30f)
                                )
                            ) {
                                Text(
                                    text = player.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                        Text(
                            if (requiresSinglePlayer) {
                                "Streaks use the best run of consecutive wins in the selected date window."
                            } else {
                                "A group play counts when every selected player appears in the same logged play."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = targetCount,
                    onValueChange = { if (it.all { c -> c.isDigit() }) targetCount = it },
                    label = { Text("Target count") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            BoardFlowIconButton(
                                onClick = { showStartDatePicker = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick start date", modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            BoardFlowIconButton(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick end date", modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoardFlowOutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    BoardFlowButton(
                        onClick = {
                            val effectiveTitle = title.trim().ifBlank {
                                challengeDescription(
                                    Challenge(
                                        id = "", title = "",
                                        type = selectedType,
                                        targetCount = target,
                                        gameId = selectedGame?.objectId?.toIntOrNull(),
                                        gameName = selectedGame?.name,
                                        playerIds = selectedPlayers.map { it.id },
                                        playerNames = selectedPlayers.map { it.displayName }
                                    )
                                )
                            }
                            onCreate(
                                Challenge(
                                    id = UUID.randomUUID().toString(),
                                    title = effectiveTitle,
                                    type = selectedType,
                                    targetCount = target,
                                    gameId = selectedGame?.objectId?.toIntOrNull(),
                                    gameName = selectedGame?.name,
                                    playerIds = selectedPlayers.map { it.id },
                                    playerNames = selectedPlayers.map { it.displayName },
                                    startDate = startDate.trim().ifBlank { null },
                                    endDate = endDate.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = target > 0 &&
                            gameOk &&
                            playerOk,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}
