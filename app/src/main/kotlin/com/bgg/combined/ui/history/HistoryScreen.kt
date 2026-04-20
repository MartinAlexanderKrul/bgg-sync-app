@file:OptIn(ExperimentalMaterial3Api::class)

package com.bgg.combined.ui.history

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bgg.combined.AppViewModel
import com.bgg.combined.ui.common.AnimatedDialog
import com.bgg.combined.model.LoggedPlay
import com.bgg.combined.model.Player
import com.bgg.combined.model.PlayerResult
import com.bgg.combined.ui.common.withTabularNumbers

@Composable
fun HistoryScreen(viewModel: AppViewModel) {
    val bggPlays by viewModel.bggPlays.collectAsState()
    val bggLoading by viewModel.bggPlaysLoading.collectAsState()
    val bggError by viewModel.bggPlaysError.collectAsState()
    val players by viewModel.players.collectAsState()
    val deletingPlayId by viewModel.deletingBggPlayId.collectAsState()
    var playToDelete by remember { mutableStateOf<LoggedPlay?>(null) }
    var selectedPlay by remember { mutableStateOf<LoggedPlay?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadPlayers()
        viewModel.loadCachedBggPlays()
        if (bggPlays.isEmpty() || viewModel.isBggPlaysCacheStale()) {
            viewModel.fetchBggPlays()
        }
    }

    playToDelete?.let { play ->
        AlertDialog(
            onDismissRequest = { playToDelete = null },
            title = { Text("Delete Play") },
            text = { Text("Delete this play from BGG? This also removes it from the local cached history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBggPlay(
                            playId = play.id,
                            onSuccess = {
                                playToDelete = null
                                deleteError = null
                            },
                            onError = { message ->
                                deleteError = message
                                playToDelete = null
                            }
                        )
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    selectedPlay?.let { play ->
        PlayDetailsDialog(
            play = play,
            players = players,
            isDeleting = deletingPlayId == play.id,
            onDismiss = { selectedPlay = null },
            onDeletePlay = {
                selectedPlay = null
                playToDelete = play
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.fetchBggPlays() },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh history")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            deleteError?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            PlaysContent(
                plays = bggPlays,
                players = players,
                loading = bggLoading,
                error = bggError,
                onOpenPlay = { selectedPlay = it },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PlaysContent(
    plays: List<LoggedPlay>,
    players: List<Player>,
    loading: Boolean,
    error: String?,
    onOpenPlay: (LoggedPlay) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        loading && plays.isEmpty() -> LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(5) { ShimmerPlayCard() }
        }

        error != null && plays.isEmpty() -> Box(
            modifier.padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        plays.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
                Text(
                    "No plays found on BGG",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Set your BGG username in Settings and refresh to sync your play history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        else -> LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(plays, key = { it.id }) { play ->
                PlayHistoryCard(
                    play = play,
                    players = players,
                    onClick = { onOpenPlay(play) }
                )
            }
        }
    }
}

@Composable
private fun PlayHistoryCard(
    play: LoggedPlay,
    players: List<Player>,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(CardDefaults.shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    play.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (play.durationMinutes > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                "${play.durationMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    if (play.location.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                play.location,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    play.gameName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (play.quantity > 1) {
                    PlayBadge("×${play.quantity}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (play.incomplete) {
                    PlayBadge("partial", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                play.players.forEach { player ->
                    PlayerRow(player, resolveDisplayName(player.name, players))
                }
            }
        }
    }
}

@Composable
private fun ShimmerPlayCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(72.dp).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).background(shimmer, RoundedCornerShape(4.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(Modifier.fillMaxWidth(0.4f).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
                        Box(Modifier.width(32.dp).height(10.dp).background(shimmer, RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayBadge(label: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PlayerColorDot(colorName: String) {
    val knownColors = mapOf(
        "red" to Color(0xFFE53935), "blue" to Color(0xFF1E88E5), "green" to Color(0xFF43A047),
        "yellow" to Color(0xFFFDD835), "orange" to Color(0xFFFB8C00), "purple" to Color(0xFF8E24AA),
        "white" to Color(0xFFF5F5F5), "black" to Color(0xFF212121), "pink" to Color(0xFFE91E63),
        "brown" to Color(0xFF6D4C41), "gray" to Color(0xFF757575), "grey" to Color(0xFF757575),
        "cyan" to Color(0xFF00ACC1), "teal" to Color(0xFF00897B), "lime" to Color(0xFF7CB342)
    )
    val parsed = knownColors[colorName.lowercase().trim()]
        ?: runCatching { Color(android.graphics.Color.parseColor(colorName)) }.getOrNull()
    if (parsed != null) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(parsed, CircleShape)
        )
    }
}

@Composable
private fun PlayerRow(player: PlayerResult, displayName: String) {
    val scoreText = player.score.takeUnless {
        val normalized = it.trim()
        normalized.isEmpty() || normalized == "0" || normalized == "0.0"
    } ?: "—"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (player.color.isNotBlank()) {
            PlayerColorDot(player.color)
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (player.isWinner) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (player.isNew) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "New player",
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Row(
            modifier = Modifier.width(56.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                scoreText,
                style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                color = if (player.isWinner) MaterialTheme.colorScheme.tertiary
                else if (scoreText == "—") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (player.isWinner) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Winner",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PlayDetailsDialog(
    play: LoggedPlay,
    players: List<Player>,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDeletePlay: () -> Unit
) {
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                play.gameName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                play.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SuggestionChip(onClick = {}, label = { Text("${play.players.size} players", style = MaterialTheme.typography.labelSmall) })
                                if (play.durationMinutes > 0) {
                                    SuggestionChip(onClick = {}, label = { Text("${play.durationMinutes} min", style = MaterialTheme.typography.labelSmall) })
                                }
                                if (play.quantity > 1) {
                                    SuggestionChip(onClick = {}, label = { Text("×${play.quantity}", style = MaterialTheme.typography.labelSmall) })
                                }
                                if (play.incomplete) {
                                    SuggestionChip(onClick = {}, label = { Text("partial", style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        }
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
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
                    DetailSection(
                        rows = buildList {
                            add("Date" to play.date)
                            if (play.durationMinutes > 0) add("Duration" to "${play.durationMinutes} min")
                            if (play.location.isNotBlank()) add("Location" to play.location)
                            if (play.quantity > 1) add("Quantity" to "×${play.quantity}")
                            if (play.incomplete) add("Status" to "Incomplete play")
                            if (!play.nowInStats) add("Stats" to "Excluded from stats")
                            if (play.comments.isNotBlank()) add("Comment" to play.comments)
                        }
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Players", style = MaterialTheme.typography.titleSmall)
                        play.players.forEach { player ->
                            Column {
                                PlayerRow(player, resolveDisplayName(player.name, players))
                                val meta = buildList {
                                    if (player.color.isNotBlank()) add(player.color)
                                    if (player.rating.isNotBlank() && player.rating != "N/A") add("rated ${player.rating}")
                                    if (player.isNew) add("first play")
                                }
                                if (meta.isNotEmpty()) {
                                    Text(
                                        meta.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDeletePlay,
                        enabled = !isDeleting,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Delete play from BGG")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp)
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun resolveDisplayName(name: String, players: List<Player>): String {
    if (name.isBlank()) return name
    val lower = name.lowercase().trim()
    return players.firstOrNull { player ->
        (listOf(player.displayName) + player.aliases).any { it.lowercase().trim() == lower }
    }?.displayName ?: name
}
