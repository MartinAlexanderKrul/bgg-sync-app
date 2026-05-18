package cz.nicolsburg.boardflow.ui.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import cz.nicolsburg.boardflow.ui.common.BoardFlowSurfaceTokens
import cz.nicolsburg.boardflow.ui.common.SectionCard
import cz.nicolsburg.boardflow.ui.common.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengesTabContent(
    progressList: List<ChallengeProgress>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val activeProgress = remember(progressList) { progressList.filter { !it.isComplete } }
    val completedProgress = remember(progressList) { progressList.filter { it.isComplete } }
    val overallFraction = remember(progressList) {
        if (progressList.isEmpty()) 0f
        else progressList.map { it.fraction }.average().toFloat()
    }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }

    if (progressList.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = BoardFlowSurfaceTokens.ContentCardShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, Color(0xFFF0A500).copy(alpha = 0.22f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF0A500).copy(alpha = 0.14f)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp).size(28.dp),
                            tint = Color(0xFFF0A500)
                        )
                    }
                    Text(
                        "No challenges yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Create a goal for this month, your group, or a favorite game and BoardFlow will keep the pressure on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier.fillMaxSize()
        ) {
            item {
                ChallengesHeroCard(
                    activeCount = activeProgress.size,
                    completeCount = completedProgress.size,
                    overallFraction = overallFraction
                )
            }

            if (activeProgress.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Goals In Motion",
                        subtitle = "Track momentum across your current challenge lineup."
                    )
                }
                items(activeProgress, key = { it.challenge.id }) { progress ->
                    ChallengeCard(
                        progress = progress,
                        onDelete = { onDelete(progress.challenge.id) }
                    )
                }
            } else {
                item {
                    Surface(
                        shape = BoardFlowSurfaceTokens.ContentCardShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color(0xFFF0A500).copy(alpha = 0.16f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "No active challenges",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Everything is wrapped up. Tap + to set a new goal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (completedProgress.isNotEmpty()) {
                item {
                    CompletedChallengesHeader(
                        count = completedProgress.size,
                        expanded = completedExpanded,
                        onToggle = { completedExpanded = !completedExpanded }
                    )
                }
                if (completedExpanded) {
                    items(completedProgress, key = { it.challenge.id }) { progress ->
                        ChallengeCard(
                            progress = progress,
                            onDelete = { onDelete(progress.challenge.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    val accentColor = if (progress.isComplete) MaterialTheme.colorScheme.primary else Color(0xFFF0A500)
    val containerColor = if (progress.isComplete) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val progressPercent = (progress.fraction * 100).toInt().coerceIn(0, 100)
    val typeMeta = challengeTypeMeta(challenge.type)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BoardFlowSurfaceTokens.ContentCardShape,
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (progress.isComplete) 0.24f else 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChallengeBadge(
                            icon = typeMeta.icon,
                            label = typeMeta.label,
                            accentColor = accentColor,
                            highlighted = !progress.isComplete
                        )
                        if (progress.isComplete) {
                            ChallengeBadge(
                                icon = Icons.Default.Flag,
                                label = "Complete",
                                accentColor = MaterialTheme.colorScheme.primary,
                                highlighted = true
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        challengeDescription(challenge),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            ChallengeMetaRow(challenge = challenge, accentColor = accentColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${progress.currentCount} / ${progress.goalCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    Text(
                        if (progress.isComplete) "Wrapped up" else progress.remainingText ?: "Still in progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "$progressPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor.copy(alpha = 0.92f)
                )
            }

            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun CompletedChallengesHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Completed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ChallengesHeroCard(
    activeCount: Int,
    completeCount: Int,
    overallFraction: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BoardFlowSurfaceTokens.ContentCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, Color(0xFFF0A500).copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Challenge Board",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Keep ongoing goals visible and give completed runs a proper finish line.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF0A500).copy(alpha = 0.14f)
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(22.dp),
                        tint = Color(0xFFF0A500)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ChallengeSummaryStat(
                    label = "Active",
                    value = activeCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                ChallengeSummaryStat(
                    label = "Completed",
                    value = completeCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                ChallengeSummaryStat(
                    label = "Avg. progress",
                    value = "${(overallFraction * 100).toInt().coerceIn(0, 100)}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChallengeSummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = BoardFlowSurfaceTokens.Shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF0A500)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class ChallengeTypeMeta(
    val label: String,
    val description: String,
    val icon: ImageVector
)

private fun challengeTypeMeta(type: ChallengeType): ChallengeTypeMeta = when (type) {
    ChallengeType.PLAY_N_TIMES ->
        ChallengeTypeMeta("Play volume", "Set a target for total logged plays.", Icons.Default.EmojiEvents)
    ChallengeType.PLAY_SPECIFIC_GAME ->
        ChallengeTypeMeta("Single game", "Keep one title coming back to the table.", Icons.Default.SportsEsports)
    ChallengeType.PLAY_N_DISTINCT ->
        ChallengeTypeMeta("Variety run", "Push yourself to rotate through more games.", Icons.Default.Flag)
    ChallengeType.PLAYER_WIN_STREAK ->
        ChallengeTypeMeta("Hot streak", "Track a player chasing consecutive wins.", Icons.Default.LocalFireDepartment)
    ChallengeType.PLAY_WITH_GROUP_N_TIMES ->
        ChallengeTypeMeta("Table group", "Follow how often your regular crew gets together.", Icons.Default.Groups)
}

@Composable
private fun ChallengeBadge(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    highlighted: Boolean
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (highlighted) 0.22f else 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = accentColor
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeMetaRow(
    challenge: Challenge,
    accentColor: Color
) {
    val dateLabel = when {
        challenge.startDate != null && challenge.endDate != null -> "${challenge.startDate} to ${challenge.endDate}"
        challenge.startDate != null -> "From ${challenge.startDate}"
        challenge.endDate != null -> "Until ${challenge.endDate}"
        else -> null
    }
    val focusLabel = when (challenge.type) {
        ChallengeType.PLAY_SPECIFIC_GAME -> challenge.gameName
        ChallengeType.PLAYER_WIN_STREAK -> challenge.playerNames.firstOrNull()
        ChallengeType.PLAY_WITH_GROUP_N_TIMES -> challenge.playerNames.takeIf { it.isNotEmpty() }?.let { names ->
            when {
                names.size <= 3 -> names.joinToString(", ")
                else -> names.take(3).joinToString(", ") + " +${names.size - 3}"
            }
        }
        else -> null
    }

    if (dateLabel == null && focusLabel == null) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        focusLabel?.let {
            ChallengeBadge(
                icon = challengeTypeMeta(challenge.type).icon,
                label = it,
                accentColor = accentColor,
                highlighted = false
            )
        }
        dateLabel?.let {
            ChallengeBadge(
                icon = Icons.Default.CalendarMonth,
                label = it,
                accentColor = accentColor,
                highlighted = false
            )
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
                SectionHeader(
                    title = "New Challenge",
                    subtitle = "Build a goal that feels at home in an actual game night routine."
                )
            }

            item {
                SectionCard {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("e.g. Play 10 games this month") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            item {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Goal type",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Pick whether you're chasing reps, variety, a hot hand, or a regular table crew.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ChallengeType.entries.forEach { type ->
                        val isSelected = type == selectedType
                        val meta = challengeTypeMeta(type)
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
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFF0A500).copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFFF0A500).copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Icon(
                                        meta.icon,
                                        contentDescription = null,
                                        modifier = Modifier.padding(10.dp).size(18.dp),
                                        tint = if (isSelected) Color(0xFFF0A500) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFFF0A500)
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        meta.description,
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

            if (selectedType == ChallengeType.PLAY_SPECIFIC_GAME) {
                item {
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            if (requiresSinglePlayer || requiresGroupPlayers) {
                item {
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!requiresSinglePlayer || selectedPlayers.isEmpty()) {
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
                        }
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
            }

            item {
                SectionCard {
                    OutlinedTextField(
                        value = targetCount,
                        onValueChange = { if (it.all { c -> c.isDigit() }) targetCount = it },
                        label = { Text("Target count") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            item {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Date window",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Optional. Keep this goal focused on a month, season, or campaign.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                },
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
                                },
                            )
                        }
                    }
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
