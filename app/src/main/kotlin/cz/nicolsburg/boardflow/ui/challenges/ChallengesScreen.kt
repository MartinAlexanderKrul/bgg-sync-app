package cz.nicolsburg.boardflow.ui.challenges

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.model.Challenge
import cz.nicolsburg.boardflow.model.ChallengeProgress
import cz.nicolsburg.boardflow.model.ChallengeType
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
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
                        "${progress.currentCount} / ${challenge.targetCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (progress.isComplete)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
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
                    "${challenge.targetCount - progress.currentCount} more to go",
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChallengeDialog(
    collectionItems: List<GameItem>,
    onDismiss: () -> Unit,
    onCreate: (Challenge) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(ChallengeType.PLAY_N_TIMES) }
    var targetCount by rememberSaveable { mutableStateOf("10") }
    var gameQuery by rememberSaveable { mutableStateOf("") }
    var selectedGame by remember { mutableStateOf<GameItem?>(null) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var gameMenuExpanded by remember { mutableStateOf(false) }
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val filteredGames = remember(gameQuery, collectionItems) {
        if (gameQuery.length < 2) emptyList()
        else collectionItems.filter { it.name.contains(gameQuery, ignoreCase = true) }.take(8)
    }

    val target = targetCount.toIntOrNull() ?: 0
    val gameOk = selectedType != ChallengeType.PLAY_SPECIFIC_GAME || selectedGame != null

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
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Goal type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        ChallengeType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    selectedType = type
                                    typeMenuExpanded = false
                                    if (type != ChallengeType.PLAY_SPECIFIC_GAME) {
                                        selectedGame = null
                                        gameQuery = ""
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (selectedType == ChallengeType.PLAY_SPECIFIC_GAME) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = gameMenuExpanded && filteredGames.isNotEmpty(),
                        onExpandedChange = { gameMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedGame?.name ?: gameQuery,
                            onValueChange = {
                                gameQuery = it
                                selectedGame = null
                                gameMenuExpanded = true
                            },
                            label = { Text("Game") },
                            placeholder = { Text("Search your collection...") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = gameMenuExpanded && filteredGames.isNotEmpty(),
                            onDismissRequest = { gameMenuExpanded = false }
                        ) {
                            filteredGames.forEach { game ->
                                DropdownMenuItem(
                                    text = { Text(game.name) },
                                    onClick = {
                                        selectedGame = game
                                        gameQuery = game.name
                                        gameMenuExpanded = false
                                    }
                                )
                            }
                        }
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
                                        gameName = selectedGame?.name
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
                                    startDate = startDate.trim().ifBlank { null },
                                    endDate = endDate.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = target > 0 && gameOk,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}
