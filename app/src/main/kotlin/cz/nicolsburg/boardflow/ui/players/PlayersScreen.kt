package cz.nicolsburg.boardflow.ui.players

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.SectionCard
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class PlayerStats(
    val totalPlays: Int,
    val wins: Int,
    val lastPlayedDate: String?,
    val favoriteGame: String?
)

private val PlayerStats.winRate: Int
    get() = if (totalPlays > 0) wins * 100 / totalPlays else 0

private fun List<LoggedPlay>.statsForPlayer(player: Player): PlayerStats {
    val names = (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
    val myPlays = filter { play -> play.players.any { it.name.lowercase().trim() in names } }
    val wins = myPlays.count { play -> play.players.any { it.name.lowercase().trim() in names && it.isWinner } }
    val lastDate = myPlays.maxOfOrNull { it.date }?.let { formatPlayDate(it) }
    val favGame = myPlays.groupingBy { it.gameName }.eachCount().maxByOrNull { it.value }?.key
    return PlayerStats(totalPlays = myPlays.size, wins = wins, lastPlayedDate = lastDate, favoriteGame = favGame)
}

private fun formatPlayDate(yyyyMMdd: String): String = try {
    LocalDate.parse(yyyyMMdd).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (_: Exception) { yyyyMMdd }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayersScreen(viewModel: AppViewModel) {
    val players    by viewModel.players.collectAsState()
    val localPlays by viewModel.playHistory.collectAsState()
    val bggPlays   by viewModel.bggPlays.collectAsState()
    val sourcePlays = if (bggPlays.isNotEmpty()) bggPlays else localPlays

    var showAddDialog  by remember { mutableStateOf(false) }
    var editingPlayer  by remember { mutableStateOf<Player?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadPlayers(); viewModel.loadPlayHistory(); viewModel.loadCachedBggPlays()
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        PlayerDialog(
            onDismissRequest = { showAddDialog = false },
            title = "New Player",
            actions = {
                BoardFlowButton(
                    onClick = {
                        viewModel.addNewPlayer(newName)
                        showAddDialog = false
                    },
                    enabled = newName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Player")
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Add a player manually. They will also continue to be created automatically from logged plays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Display Name", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = newName, onValueChange = { newName = it },
                        placeholder = { Text("e.g. Alice") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    editingPlayer?.let { ep ->
        val livePlayer = players.find { it.id == ep.id }
        if (livePlayer != null) {
            EditPlayerDialog(
                player = livePlayer,
                onDismiss = { editingPlayer = null },
                onRenameDisplayName = { viewModel.updatePlayerDisplayName(livePlayer.id, it) },
                onUpdateBggUsername = { viewModel.updatePlayerBggUsername(livePlayer.id, it) },
                onAddAlias = { viewModel.addPlayerAlias(livePlayer.id, it) },
                onRemoveAlias = { viewModel.removePlayerAlias(livePlayer.id, it) },
                onDelete = { viewModel.deletePlayer(livePlayer.id); editingPlayer = null }
            )
        } else {
            editingPlayer = null
        }
    }

    Scaffold(
        topBar = {},
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add player")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (players.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        Text("No players yet", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Players are added automatically when you log plays.\nTap + to add your first player manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(players, key = { it.id }) { player ->
                        val stats = remember(sourcePlays, player) { sourcePlays.statsForPlayer(player) }
                        PlayerListItem(player = player, stats = stats, onEdit = { editingPlayer = player })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerListItem(player: Player, stats: PlayerStats, onEdit: () -> Unit) {
    SectionCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    player.displayName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                val infoParts = buildList {
                    if (player.bggUsername.isNotBlank()) add("BGG: ${player.bggUsername}")
                    if (player.aliases.isNotEmpty()) add("Aliases: ${player.aliases.joinToString(", ")}")
                }
                if (infoParts.isNotEmpty()) {
                    Text(
                        infoParts.joinToString("  -  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val statLine = if (stats.totalPlays > 0) {
                    buildList {
                        add("${stats.totalPlays} plays")
                        add("${stats.wins} wins (${stats.winRate}%)")
                        stats.lastPlayedDate?.let { add("Last: $it") }
                    }.joinToString("  -  ")
                } else {
                    "No plays yet"
                }
                Text(
                    statLine,
                    style = MaterialTheme.typography.bodySmall.withTabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                stats.favoriteGame?.let {
                    Text(
                        "Favorite: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit player") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPlayerDialog(
    player: Player,
    onDismiss: () -> Unit,
    onRenameDisplayName: (String) -> Unit,
    onUpdateBggUsername: (String) -> Unit,
    onAddAlias: (String) -> Unit,
    onRemoveAlias: (String) -> Unit,
    onDelete: () -> Unit
) {
    var displayName by remember { mutableStateOf(player.displayName) }
    var bggUsername by remember { mutableStateOf(player.bggUsername) }
    var newAlias    by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var aliasToRemove by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(player.displayName) { displayName = player.displayName }
    LaunchedEffect(player.bggUsername) { bggUsername = player.bggUsername }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Player") },
            text  = { Text("Delete \"${player.displayName}\" and all their aliases? This cannot be undone.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
        return
    }

    aliasToRemove?.let { alias ->
        AlertDialog(
            onDismissRequest = { aliasToRemove = null },
            title = { Text("Remove Alias") },
            text = { Text("Remove alias \"$alias\" from ${player.displayName}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAlias(alias)
                        aliasToRemove = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val identityChanged = (displayName.isNotBlank() && displayName != player.displayName)
            || bggUsername.trim() != player.bggUsername

    PlayerDialog(
        onDismissRequest = onDismiss,
        title = "Edit Player",
        actions = {
            BoardFlowButton(
                onClick = {
                    if (displayName.isNotBlank() && displayName != player.displayName) onRenameDisplayName(displayName)
                    if (bggUsername.trim() != player.bggUsername) onUpdateBggUsername(bggUsername)
                },
                enabled = identityChanged,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Display Name", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = displayName, onValueChange = { displayName = it },
                            placeholder = { Text("e.g. Alice") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("BGG Username", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = bggUsername, onValueChange = { bggUsername = it },
                            placeholder = { Text("e.g. boardgamer42") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            HorizontalDivider()
            Text("Aliases", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (player.aliases.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    player.aliases.forEach { alias ->
                        InputChip(selected = false, onClick = {}, label = { Text(alias) },
                            trailingIcon = {
                                IconButton(onClick = { aliasToRemove = alias }, modifier = Modifier.size(18.dp)) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove $alias",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("New Alias", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = newAlias, onValueChange = { newAlias = it },
                        placeholder = { Text("e.g. Al") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
                IconButton(onClick = { onAddAlias(newAlias); newAlias = "" },
                    enabled = newAlias.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = "Add alias")
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Delete Player", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PlayerDialog(
    onDismissRequest: () -> Unit,
    title: String,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedDialog(onDismissRequest = onDismissRequest) {
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
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                item {
                    Column(content = content)
                }
                item {
                    Column(content = actions)
                }
            }
        }
    }
}
