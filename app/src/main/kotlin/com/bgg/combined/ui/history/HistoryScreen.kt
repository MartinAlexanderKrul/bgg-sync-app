package com.bgg.combined.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bgg.combined.AppViewModel
import com.bgg.combined.model.LoggedPlay
import com.bgg.combined.model.Player
import com.bgg.combined.model.PlayerResult

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
                            .padding(horizontal = 16.dp, vertical = 10.dp)
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
        loading && plays.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                play.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                play.gameName,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                play.players.forEach { player ->
                    PlayerRow(player, resolveDisplayName(player.name, players))
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(player: PlayerResult, displayName: String) {
    val scoreText = player.score.takeUnless {
        val normalized = it.trim()
        normalized.isEmpty() || normalized == "0" || normalized == "0.0"
    } ?: "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (player.isWinner) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier.width(56.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                scoreText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (player.isWinner) MaterialTheme.colorScheme.tertiary
                else if (scoreText == "-") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SuggestionChip(onClick = {}, label = { Text("${play.players.size} players", style = MaterialTheme.typography.labelSmall) })
                                if (play.durationMinutes > 0) {
                                    SuggestionChip(onClick = {}, label = { Text("${play.durationMinutes} min", style = MaterialTheme.typography.labelSmall) })
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
                            if (play.comments.isNotBlank()) add("Comment" to play.comments)
                        }
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Players", style = MaterialTheme.typography.titleSmall)
                        play.players.forEach { player ->
                            PlayerRow(player, resolveDisplayName(player.name, players))
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = onDeletePlay,
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
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
                    style = MaterialTheme.typography.bodyMedium,
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
