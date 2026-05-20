package cz.nicolsburg.boardflow.ui.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.ui.common.AnimatedDialog
import cz.nicolsburg.boardflow.ui.history.recentPlaysForPlayer
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationDialog
import cz.nicolsburg.boardflow.ui.common.BoardFlowConfirmationKind
import cz.nicolsburg.boardflow.ui.common.BoardFlowCloseGlyph
import cz.nicolsburg.boardflow.ui.common.BoardFlowDestructiveButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowIcons
import cz.nicolsburg.boardflow.ui.common.BoardFlowSecondaryButton
import cz.nicolsburg.boardflow.ui.common.PlayerAvatar
import cz.nicolsburg.boardflow.ui.common.SectionCard
import cz.nicolsburg.boardflow.ui.common.withTabularNumbers
import cz.nicolsburg.boardflow.ui.history.RivalryStat
import cz.nicolsburg.boardflow.ui.history.playerCurrentWinStreak
import cz.nicolsburg.boardflow.ui.history.rivalriesForPlayer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class PlayerStats(
    val totalPlays: Int,
    val wins: Int,
    val lastPlayedDate: String?,
    val favoriteGame: String?,
    val favoriteGameId: Int? = null,
    val currentWinStreak: Int = 0
)

internal val PlayerStats.winRate: Int
    get() = if (totalPlays > 0) wins * 100 / totalPlays else 0

internal fun List<LoggedPlay>.statsForPlayer(player: Player): PlayerStats {
    val names = (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
    val myPlays = filter { play -> play.players.any { it.name.lowercase().trim() in names } }
    val wins = myPlays.count { play -> play.players.any { it.name.lowercase().trim() in names && it.isWinner } }
    val lastDate = myPlays.maxOfOrNull { it.date }?.let { formatPlayDate(it) }
    val favEntry = myPlays
        .groupBy { it.gameId }
        .mapValues { (_, plays) ->
            val name = plays.maxByOrNull { it.date }?.gameName ?: plays.first().gameName
            name to plays.sumOf { it.quantity.coerceAtLeast(1) }
        }
        .maxByOrNull { it.value.second }
    val streak = playerCurrentWinStreak(names)
    return PlayerStats(
        totalPlays = myPlays.size,
        wins = wins,
        lastPlayedDate = lastDate,
        favoriteGame = favEntry?.value?.first,
        favoriteGameId = favEntry?.key,
        currentWinStreak = streak
    )
}

private fun List<LoggedPlay>.lastPlayedDateForPlayer(player: Player): LocalDate? {
    val names = (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
    return filter { play -> play.players.any { it.name.lowercase().trim() in names } }
        .mapNotNull { play -> runCatching { LocalDate.parse(play.date) }.getOrNull() }
        .maxOrNull()
}

private fun List<Player>.sortedByRecentActivity(sourcePlays: List<LoggedPlay>): List<Player> =
    sortedWith(
        compareByDescending<Player> { sourcePlays.lastPlayedDateForPlayer(it) ?: LocalDate.MIN }
            .thenBy { it.displayName.lowercase() }
    )

internal fun formatPlayDate(yyyyMMdd: String): String = try {
    LocalDate.parse(yyyyMMdd).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
} catch (_: Exception) { yyyyMMdd }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayersScreen(viewModel: AppViewModel) {
    val players    by viewModel.players.collectAsState()
    val sourcePlays by viewModel.historyPlays.collectAsState()
    val sortedPlayers = remember(players, sourcePlays) { players.sortedByRecentActivity(sourcePlays) }

    var showAddDialog  by remember { mutableStateOf(false) }
    var editingPlayer  by remember { mutableStateOf<Player?>(null) }
    var viewingPlayer  by remember { mutableStateOf<Player?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadPlayers(); viewModel.loadPlayHistory(); viewModel.loadCachedBggPlays()
    }

    if (showAddDialog) {
        AddPlayerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name -> viewModel.addNewPlayer(name); showAddDialog = false }
        )
    }

    viewingPlayer?.let { vp ->
        val livePlayer = players.find { it.id == vp.id }
        if (livePlayer != null) {
            val stats = remember(sourcePlays, livePlayer) { sourcePlays.statsForPlayer(livePlayer) }
            val rivalries = remember(sourcePlays, livePlayer) { sourcePlays.rivalriesForPlayer(livePlayer) }
            PlayerDetailDialog(
                player = livePlayer,
                stats = stats,
                rivalries = rivalries,
                onDismiss = { viewingPlayer = null },
                onEdit = { editingPlayer = livePlayer; viewingPlayer = null }
            )
        } else { viewingPlayer = null }
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
                onToggleHidden = { viewModel.updatePlayerHidden(livePlayer.id, it) },
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
                    items(sortedPlayers, key = { it.id }) { player ->
                        val stats = remember(sourcePlays, player) { sourcePlays.statsForPlayer(player) }
                        PlayerListItem(player = player, stats = stats,
                            onTap = { viewingPlayer = player })
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerListItem(player: Player, stats: PlayerStats, onTap: () -> Unit = {}) {
    SectionCard(onClick = onTap) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(player.displayName)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        player.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (player.isHidden) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = "Hidden from stats",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
                val infoParts = buildList {
                    if (player.bggUsername.isNotBlank()) add("BGG: ${player.bggUsername}")
                    if (player.aliases.isNotEmpty()) add("Also: ${player.aliases.joinToString(", ")}")
                }
                if (infoParts.isNotEmpty()) {
                    Text(
                        infoParts.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (stats.lastPlayedDate != null) "Last played ${stats.lastPlayedDate}" else "No plays yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (stats.lastPlayedDate != null) 1f else 0.6f
                    )
                )
                stats.favoriteGame?.let { game ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Fav:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            game,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditPlayerDialog(
    player: Player,
    onDismiss: () -> Unit,
    onRenameDisplayName: (String) -> Unit,
    onUpdateBggUsername: (String) -> Unit,
    onAddAlias: (String) -> Unit,
    onRemoveAlias: (String) -> Unit,
    onToggleHidden: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var displayName  by remember { mutableStateOf(player.displayName) }
    var bggUsername  by remember { mutableStateOf(player.bggUsername) }
    var isHidden     by remember { mutableStateOf(player.isHidden) }
    var localAliases by remember { mutableStateOf(player.aliases) }
    var newAlias     by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(player.displayName) { displayName = player.displayName }
    LaunchedEffect(player.bggUsername) { bggUsername = player.bggUsername }
    LaunchedEffect(player.isHidden)    { isHidden    = player.isHidden }

    if (showDeleteConfirm) {
        BoardFlowConfirmationDialog(
            title = "Delete player?",
            message = "Delete \"${player.displayName}\" and all aliases? This cannot be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            kind = BoardFlowConfirmationKind.DESTRUCTIVE,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
        return
    }

    val identityChanged = (displayName.isNotBlank() && displayName != player.displayName)
            || bggUsername.trim() != player.bggUsername
            || isHidden != player.isHidden
            || localAliases != player.aliases

    AnimatedDialog(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerAvatar(player.displayName, size = 48.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            player.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Edit profile, aliases, and visibility",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Identity",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display name") },
                            placeholder = { Text("e.g. Alice") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bggUsername,
                            onValueChange = { bggUsername = it },
                            label = { Text("BGG username") },
                            placeholder = { Text("e.g. boardgamer42") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp).size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Hide from stats",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Excluded from leaderboards, rivalries, and recommendations.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isHidden,
                            onCheckedChange = { isHidden = it },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Aliases",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Other names this player uses across your play logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (localAliases.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                localAliases.forEach { alias ->
                                    InputChip(
                                        selected = false,
                                        onClick = {},
                                        label = { Text(alias) },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { localAliases = localAliases.filter { it != alias } },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                BoardFlowCloseGlyph(
                                                    contentDescription = "Remove $alias",
                                                    modifier = Modifier.size(14.dp),
                                                    iconSize = 14.dp
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        val canAdd = newAlias.isNotBlank() && newAlias.trim() !in localAliases
                        val doAdd = { if (canAdd) { localAliases = localAliases + newAlias.trim(); newAlias = "" } }
                        OutlinedTextField(
                            value = newAlias,
                            onValueChange = { newAlias = it },
                            label = { Text("Add alias") },
                            placeholder = { Text("e.g. Al") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { doAdd() }),
                            trailingIcon = {
                                IconButton(onClick = doAdd, enabled = canAdd) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add alias",
                                        tint = if (canAdd) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BoardFlowDestructiveButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(BoardFlowIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                    BoardFlowButton(
                        onClick = {
                            if (displayName.isNotBlank() && displayName != player.displayName) onRenameDisplayName(displayName)
                            if (bggUsername.trim() != player.bggUsername) onUpdateBggUsername(bggUsername)
                            if (isHidden != player.isHidden) onToggleHidden(isHidden)
                            val toAdd = localAliases - player.aliases.toSet()
                            val toRemove = player.aliases - localAliases.toSet()
                            toAdd.forEach { onAddAlias(it) }
                            toRemove.forEach { onRemoveAlias(it) }
                            onDismiss()
                        },
                        enabled = identityChanged,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(BoardFlowIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerDialog(
    onDismissRequest: () -> Unit,
    title: String,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedDialog(onDismissRequest = onDismissRequest) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
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

@Composable
fun PlayersTabContent(
    players: List<Player>,
    sourcePlays: List<LoggedPlay>,
    onEditPlayer: (Player) -> Unit,
    currentPlayerName: String? = null,
    listState: LazyListState = rememberLazyListState(),
    openPlayerId: String? = null,
    onOpenPlayerConsumed: () -> Unit = {},
    onViewPlayerPlays: (playerName: String, sourcePlayerId: String) -> Unit = { _, _ -> },
    onViewPlayerGame: (gameId: Int, gameName: String, sourcePlayerId: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var viewingPlayer by remember { mutableStateOf<Player?>(null) }
    var viewingRival by remember { mutableStateOf<Player?>(null) }
    var showHiddenSection by remember { mutableStateOf(false) }
    val sortedAll = remember(players, sourcePlays) { players.sortedByRecentActivity(sourcePlays) }
    val sortedPlayers = remember(sortedAll) { sortedAll.filter { !it.isHidden } }
    val hiddenPlayers = remember(sortedAll) { sortedAll.filter { it.isHidden } }

    LaunchedEffect(openPlayerId, players) {
        if (openPlayerId != null && viewingPlayer == null && viewingRival == null) {
            viewingPlayer = players.find { it.id == openPlayerId }
            onOpenPlayerConsumed()
        }
    }

    viewingPlayer?.let { vp ->
        val livePlayer = players.find { it.id == vp.id }
        if (livePlayer != null) {
            val stats = remember(sourcePlays, livePlayer) { sourcePlays.statsForPlayer(livePlayer) }
            val rivalries = remember(sourcePlays, livePlayer) { sourcePlays.rivalriesForPlayer(livePlayer) }
            PlayerDetailDialog(
                player = livePlayer,
                stats = stats,
                rivalries = rivalries,
                sourcePlays = sourcePlays,
                allPlayers = players,
                currentPlayerName = currentPlayerName,
                onDismiss = { viewingPlayer = null },
                onEdit = { onEditPlayer(livePlayer); viewingPlayer = null },
                onViewPlays = { val id = vp.id; viewingPlayer = null; onViewPlayerPlays(livePlayer.displayName, id) },
                onViewGame = { gameId, gameName -> val id = vp.id; viewingPlayer = null; onViewPlayerGame(gameId, gameName, id) },
                onViewRival = { rival -> viewingRival = rival }
            )
        } else { viewingPlayer = null }
    }

    viewingRival?.let { rv ->
        val liveRival = players.find { it.id == rv.id }
        if (liveRival != null) {
            val stats = remember(sourcePlays, liveRival) { sourcePlays.statsForPlayer(liveRival) }
            val rivalries = remember(sourcePlays, liveRival) { sourcePlays.rivalriesForPlayer(liveRival) }
            PlayerDetailDialog(
                player = liveRival,
                stats = stats,
                rivalries = rivalries,
                sourcePlays = sourcePlays,
                allPlayers = players,
                currentPlayerName = currentPlayerName,
                onDismiss = { viewingRival = null },
                onEdit = { onEditPlayer(liveRival); viewingRival = null },
                onViewPlays = { val id = rv.id; viewingRival = null; onViewPlayerPlays(liveRival.displayName, id) },
                onViewGame = { gameId, gameName -> val id = rv.id; viewingRival = null; onViewPlayerGame(gameId, gameName, id) },
                onViewRival = { rival -> viewingRival = rival }
            )
        } else { viewingRival = null }
    }

    if (players.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Person, contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
                Text(
                    "No players yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Players are added automatically when you log plays.\nTap + to add your first player manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(sortedPlayers, key = { it.id }) { player ->
                val stats = remember(sourcePlays, player) { sourcePlays.statsForPlayer(player) }
                PlayerListItem(player = player, stats = stats,
                    onTap = { viewingPlayer = player })
            }

            if (hiddenPlayers.isNotEmpty()) {
                item(key = "hidden-section-header") {
                    HiddenPlayersSectionHeader(
                        count = hiddenPlayers.size,
                        expanded = showHiddenSection,
                        onToggle = { showHiddenSection = !showHiddenSection }
                    )
                }
                if (showHiddenSection) {
                    items(hiddenPlayers, key = { "h-${it.id}" }) { player ->
                        val stats = remember(sourcePlays, player) { sourcePlays.statsForPlayer(player) }
                        PlayerListItem(player = player, stats = stats,
                            onTap = { viewingPlayer = player })
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenPlayersSectionHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        Icon(
            Icons.Default.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            "Hidden · $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse hidden" else "Expand hidden",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
internal fun PlayerDetailDialog(
    player: Player,
    stats: PlayerStats,
    rivalries: List<RivalryStat>,
    sourcePlays: List<LoggedPlay> = emptyList(),
    allPlayers: List<Player> = emptyList(),
    currentPlayerName: String? = null,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onViewPlays: (() -> Unit)? = null,
    onViewGame: ((gameId: Int, gameName: String) -> Unit)? = null,
    onViewRival: ((Player) -> Unit)? = null
) {
    // True when the dialog is showing the app user's own profile — enables "You" framing in rivalries
    val isCurrentPlayer = currentPlayerName != null &&
        (listOf(player.displayName) + player.aliases).any { it.equals(currentPlayerName, ignoreCase = true) }
    PlayerDialog(
        onDismissRequest = onDismiss,
        title = player.displayName,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onViewPlays != null) {
                    BoardFlowSecondaryButton(
                        onClick = onViewPlays,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("All plays")
                    }
                }
                BoardFlowSecondaryButton(
                    onClick = onEdit,
                    modifier = if (onViewPlays != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (player.bggUsername.isNotBlank() || player.aliases.isNotEmpty()) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (player.bggUsername.isNotBlank()) {
                            DetailRow("BGG username", player.bggUsername)
                        }
                        if (player.aliases.isNotEmpty()) {
                            DetailRow("Aliases", player.aliases.joinToString(", "))
                        }
                    }
                }
            }

            if (stats.totalPlays > 0) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Play Stats", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlayerStatCell("Plays", "${stats.totalPlays}", Modifier.weight(1f),
                                onClick = onViewPlays)
                            PlayerStatCell("Wins", "${stats.wins}", Modifier.weight(1f))
                            PlayerStatCell("Win rate", "${stats.winRate}%", Modifier.weight(1f))
                        }
                        if (stats.currentWinStreak >= 2) {
                            DetailRow("Current streak", "${stats.currentWinStreak} in a row 🔥",
                                valueColor = MaterialTheme.colorScheme.primary)
                        }
                        stats.lastPlayedDate?.let { DetailRow("Last played", it) }
                        stats.favoriteGame?.let { gameName ->
                            val gameId = stats.favoriteGameId
                            DetailRow(
                                "Most played", gameName,
                                valueColor = MaterialTheme.colorScheme.primary,
                                onClick = if (onViewGame != null && gameId != null) {
                                    { onViewGame(gameId, gameName) }
                                } else null
                            )
                        }
                    }
                }
            } else {
                Text(
                    "No plays recorded yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            val recentPlays = remember(sourcePlays, player) { sourcePlays.recentPlaysForPlayer(player, 5) }
            if (recentPlays.isNotEmpty()) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Recent Activity", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        recentPlays.forEach { play ->
                            val names = (listOf(player.displayName) + player.aliases).map { it.lowercase().trim() }
                            val won = play.players.any { it.name.lowercase().trim() in names && it.isWinner }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    play.gameName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        formatPlayDate(play.date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                        color = if (won) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            if (won) "W" else "L",
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (won) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (rivalries.isNotEmpty()) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Rivalries", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        rivalries.forEach { rivalry ->
                            val rivalPlayer = allPlayers.firstOrNull { p ->
                                (listOf(p.displayName) + p.aliases).any {
                                    it.equals(rivalry.opponentName, ignoreCase = true)
                                }
                            }
                            RivalryRow(
                                rivalry = rivalry,
                                isCurrentPlayer = isCurrentPlayer,
                                onClick = if (onViewRival != null && rivalPlayer != null) {
                                    { onViewRival(rivalPlayer) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = if (onClick != null)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (onClick != null)
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = if (onClick != null) 2.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor
        )
        if (onClick != null) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun RivalryRow(
    rivalry: RivalryStat,
    isCurrentPlayer: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val winFraction = if (rivalry.playsTogetherCount > 0)
        rivalry.myWins.toFloat() / rivalry.playsTogetherCount else 0f

    // Personalised narrative — only shown when viewing the app user's own rivalries
    val narrative: String? = if (isCurrentPlayer) when {
        rivalry.myWins > rivalry.theirWins ->
            "You lead ${rivalry.opponentName} ${rivalry.myWins}–${rivalry.theirWins}."
        rivalry.theirWins > rivalry.myWins ->
            "${rivalry.opponentName} leads you ${rivalry.theirWins}–${rivalry.myWins}."
        rivalry.myWins > 0 ->
            "Locked at ${rivalry.myWins} each."
        else -> null
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = if (onClick != null) 2.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlayerAvatar(rivalry.opponentName, size = 28.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(rivalry.opponentName, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium)
                Text("${rivalry.playsTogetherCount} plays together",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (narrative != null) {
                Text(
                    narrative,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
            LinearProgressIndicator(
                progress = { winFraction },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Text("${rivalry.myWins}–${rivalry.theirWins}",
                    style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AddPlayerDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    PlayerDialog(
        onDismissRequest = onDismiss,
        title = "New Player",
        actions = {
            BoardFlowButton(
                onClick = { onAdd(newName) },
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
                Text(
                    "Display Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    placeholder = { Text("e.g. Alice") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
