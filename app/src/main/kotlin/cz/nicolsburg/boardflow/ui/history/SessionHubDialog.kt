@file:OptIn(ExperimentalLayoutApi::class)

package cz.nicolsburg.boardflow.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.SessionHub
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowTonalButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowSurfaceTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionHubDialog(
    session: SessionHub,
    players: List<Player>,
    onDismiss: () -> Unit,
    onRenameSession: ((sessionId: String, title: String) -> Unit)? = null,
    onOpenPlay: ((LoggedPlay) -> Unit)? = null,
    onPlayAgain: ((SessionHub) -> Unit)? = null,
    onShareQr: ((SessionHub) -> Unit)? = null
) {
    var isEditingTitle by remember(session.sessionId) { mutableStateOf(false) }
    var draftTitle by remember(session.sessionId, session.title) { mutableStateOf(session.title.orEmpty()) }
    LaunchedEffect(session.title) {
        if (!isEditingTitle) draftTitle = session.title.orEmpty()
    }
    AnimatedDialog(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isEditingTitle && session.sessionId != null && onRenameSession != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draftTitle,
                                onValueChange = { draftTitle = it.take(48) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Session title") },
                                placeholder = { Text(buildSessionTitle(session)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    isEditingTitle = false
                                    draftTitle = session.title.orEmpty()
                                }) {
                                    Text("Cancel")
                                }
                                BoardFlowTonalButton(onClick = {
                                    onRenameSession(session.sessionId, draftTitle)
                                    isEditingTitle = false
                                }) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("Save title", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                buildSessionTitle(session),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                if (onShareQr != null) {
                                    IconButton(onClick = { onShareQr(session) }) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share session",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                if (session.sessionId != null && onRenameSession != null) {
                                    IconButton(onClick = { isEditingTitle = true }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        buildSessionSubtitle(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SessionHubSummaryCard(session = session)
            }

            if (session.moods.isNotEmpty() || session.quotes.isNotEmpty()) {
                item {
                    SessionHubMemoryCard(session = session)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Plays this session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    session.plays.forEach { play ->
                        SessionHubPlayCard(
                            play = play,
                            players = players,
                            onClick = onOpenPlay?.let { callback -> { callback(play) } }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    onPlayAgain?.let { callback ->
                        BoardFlowTonalButton(
                            onClick = { callback(session) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.92f)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Play this session again", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionHubSummaryCard(session: SessionHub) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BoardFlowSurfaceTokens.ContentCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        val totalPoints = session.plays.sumOf { play ->
            play.players.sumOf { p -> p.score.trim().toIntOrNull() ?: 0 }
        }
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryChip(Icons.Default.CalendarMonth, formatSessionDate(session.date))
                SummaryChip(Icons.Default.Group, "${session.uniquePlayerNames.size} players")
                SummaryChip(Icons.Default.EmojiEvents, "${session.totalLoggedPlays} logged")
                if (session.totalDurationMinutes > 0) {
                    SummaryChip(Icons.Default.Schedule, "${session.totalDurationMinutes} min")
                }
                if (totalPoints > 0) {
                    SummaryChip(Icons.Default.Star, "$totalPoints pts")
                }
                if (session.location.isNotBlank()) {
                    SummaryChip(Icons.Default.LocationOn, session.location)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Games played", session.totalLoggedPlays.toString())
                if (session.winners.isNotEmpty()) {
                    SummaryRow(
                        "Standouts",
                        session.winners.take(3).joinToString("  •  ") {
                            if (it.wins == 1) it.playerName else "${it.playerName} (${it.wins})"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionHubMemoryCard(session: SessionHub) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BoardFlowSurfaceTokens.ContentCardShape,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f),
        border = BorderStroke(0.5.dp, Color(0xFFF0A500).copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFF0A500)
                )
                Text(
                    "Session Highlights",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f)
                )
            }
            if (session.moods.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    session.moods.forEach { mood ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                mood,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            session.quotes.take(2).forEach { quote ->
                Text(
                    "— $quote",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun SessionHubPlayCard(
    play: LoggedPlay,
    players: List<Player>,
    onClick: (() -> Unit)?
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        modifier = modifier,
        shape = BoardFlowSurfaceTokens.Shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    play.gameName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (play.quantity > 1) {
                    Text(
                        "×${play.quantity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(start = 8.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SummaryChip(Icons.Default.Group, "${play.players.size} players")
                if (play.durationMinutes > 0) {
                    SummaryChip(Icons.Default.Schedule, "${play.durationMinutes} min")
                }
                play.location.trim().takeIf { it.isNotBlank() }?.let {
                    SummaryChip(Icons.Default.LocationOn, it)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                play.players.forEach { player ->
                    val displayName = resolveSessionDisplayName(player.name, players)
                    val score = player.score.trim().takeIf { it.isNotBlank() && it != "0" }
                    val playerColor = if (player.isWinner) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (player.isWinner) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Winner",
                                tint = playerColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            if (score != null) "$displayName  $score" else displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = playerColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(icon: ImageVector, label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun buildSessionTitle(session: SessionHub): String =
    session.title?.takeIf { it.isNotBlank() } ?: runCatching {
        val dow = LocalDate.parse(session.date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        "${dow}'s session"
    }.getOrDefault("Session")

private fun buildSessionSubtitle(session: SessionHub): String {
    val parts = mutableListOf<String>()
    parts += formatSessionDate(session.date)
    if (session.location.isNotBlank()) parts += session.location
    return parts.joinToString(" • ")
}

private fun formatSessionDate(raw: String): String =
    runCatching {
        LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }.getOrDefault(raw)

private fun resolveSessionDisplayName(name: String, players: List<Player>): String {
    if (name.isBlank()) return name
    val lower = name.lowercase().trim()
    return players.firstOrNull { player ->
        (listOf(player.displayName) + player.aliases).any { it.lowercase().trim() == lower }
    }?.displayName ?: name
}
