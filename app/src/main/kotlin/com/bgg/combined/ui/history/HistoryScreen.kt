package com.bgg.combined.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bgg.combined.AppViewModel
import com.bgg.combined.model.LoggedPlay
import com.bgg.combined.model.Player
import com.bgg.combined.model.PlayerResult

@Composable
fun HistoryScreen(viewModel: AppViewModel) {
    val localPlays    by viewModel.playHistory.collectAsState()
    val bggPlays      by viewModel.bggPlays.collectAsState()
    val bggLoading    by viewModel.bggPlaysLoading.collectAsState()
    val bggError      by viewModel.bggPlaysError.collectAsState()
    val players       by viewModel.players.collectAsState()
    val selectedTab   by viewModel.historySelectedTab.collectAsState()
    val deleteRequest by viewModel.historyDeleteRequest.collectAsState()
    val postingPlayId by viewModel.postingPlayId.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteRequest) {
        if (deleteRequest) { showClearDialog = true; viewModel.clearHistoryDeleteRequest() }
    }
    LaunchedEffect(Unit) {
        viewModel.loadPlayHistory(); viewModel.loadPlayers(); viewModel.loadCachedBggPlays()
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && viewModel.isBggPlaysCacheStale() && bggError == null && !bggLoading)
            viewModel.fetchBggPlays()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text  = { Text("Delete all locally stored play history? This does not affect BGG.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPlayHistory(); showClearDialog = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf("From BGG", "Local").forEachIndexed { i, label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == i) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { viewModel.setHistoryTab(i) }
                        )
                    }
                }
            }
            when (selectedTab) {
                0 -> PlaysContent(plays = bggPlays, players = players, loading = bggLoading,
                    error = bggError, emptyTitle = "No plays found on BGG",
                    emptySubtitle = "Make sure your BGG username is set in Settings",
                    showBggBadge = false, modifier = Modifier.fillMaxSize())
                1 -> PlaysContent(plays = localPlays, players = players, loading = false,
                    error = null, emptyTitle = "No plays logged yet",
                    emptySubtitle = "Log your first play by searching for a game",
                    showBggBadge = true, postingPlayId = postingPlayId,
                    onPostPlay = { viewModel.postSinglePlay(it) },
                    modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun PlaysContent(
    plays: List<LoggedPlay>,
    players: List<Player>,
    loading: Boolean,
    error: String?,
    emptyTitle: String,
    emptySubtitle: String,
    showBggBadge: Boolean,
    postingPlayId: String? = null,
    onPostPlay: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when {
        loading  -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null -> Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        plays.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Text(emptyTitle, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(emptySubtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        else -> LazyColumn(modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)) {
            items(plays) { play ->
                PlayHistoryCard(play, players, showBggBadge, postingPlayId, onPostPlay)
            }
        }
    }
}

@Composable
private fun PlayHistoryCard(
    play: LoggedPlay,
    players: List<Player>,
    showBggBadge: Boolean,
    postingPlayId: String? = null,
    onPostPlay: ((String) -> Unit)? = null
) {
    val metaParts = buildList {
        if (play.durationMinutes > 0) add("${play.durationMinutes} min")
        if (play.location.isNotBlank()) add(play.location)
    }
    ListItem(
        overlineContent = {
            Text(play.date, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(play.gameName, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column {
                play.players.forEach { player ->
                    PlayerRow(player, resolveDisplayName(player.name, players))
                }
                if (metaParts.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(metaParts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showBggBadge && play.postedToBgg) {
                    Spacer(Modifier.height(4.dp))
                    AssistChip(onClick = {},
                        label = { Text("Posted to BGG", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer))
                }
                if (showBggBadge && !play.postedToBgg && onPostPlay != null) {
                    Spacer(Modifier.height(4.dp))
                    val isPosting = postingPlayId == play.id
                    AssistChip(
                        onClick = { if (!isPosting) onPostPlay(play.id) },
                        label = {
                            if (isPosting) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Text("Posting…", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Text("Post to BGG", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        leadingIcon = if (!isPosting) {{ Icon(Icons.Default.CloudUpload,
                            contentDescription = null, modifier = Modifier.size(16.dp)) }} else null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
        }
    )
}

@Composable
private fun PlayerRow(player: PlayerResult, displayName: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (player.isWinner) Icon(Icons.Default.EmojiEvents, contentDescription = "Winner",
            tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        else Spacer(Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(displayName, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (player.isWinner) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f))
        Text(player.score, style = MaterialTheme.typography.bodyMedium,
            color = if (player.isWinner) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun resolveDisplayName(name: String, players: List<Player>): String {
    if (name.isBlank()) return name
    val lower = name.lowercase().trim()
    return players.firstOrNull { p ->
        (listOf(p.displayName) + p.aliases).any { it.lowercase() == lower }
    }?.displayName ?: name
}
