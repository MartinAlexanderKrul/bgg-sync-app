package com.bgg.combined.ui.collection

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bgg.combined.SyncViewModel
import com.bgg.combined.model.GameItem

private enum class SortMode(val label: String) { RATING("Rating"), NAME("Name"), WEIGHT("Weight"), PLAYS("Plays") }
private enum class TabMode(val label: String)  { OWNED("Owned"), WISHLIST("Wishlist") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(syncViewModel: SyncViewModel) {
    val account by syncViewModel.account.collectAsState()
    val spreadsheetId by syncViewModel.spreadsheetId.collectAsState()
    val allGames by syncViewModel.collectionGames.collectAsState()
    val loading by syncViewModel.collectionLoading.collectAsState()
    val error by syncViewModel.collectionError.collectAsState()

    var searchQuery    by remember { mutableStateOf("") }
    var sortMode       by remember { mutableStateOf(SortMode.RATING) }
    var tabMode        by remember { mutableStateOf(TabMode.OWNED) }
    var filterPlayers  by remember { mutableStateOf<Int?>(null) }

    // Auto-load when account + spreadsheet are ready and games are empty
    LaunchedEffect(account, spreadsheetId) {
        val currentAccount = account
        if (currentAccount != null && spreadsheetId.isNotBlank() && allGames.isEmpty() && !loading) {
            syncViewModel.loadCollection(currentAccount)
        }
    }

    val filteredGames = remember(allGames, searchQuery, sortMode, tabMode, filterPlayers) {
        var result = allGames
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter { it.name.lowercase().contains(q) }
        }
        result = when (tabMode) {
            TabMode.OWNED    -> result.filter { it.isOwned || !it.isWishlisted }
            TabMode.WISHLIST -> result.filter { it.isWishlisted }
        }
        filterPlayers?.let { p ->
            result = result.filter { (it.minPlayers ?: 1) <= p && (it.maxPlayers ?: 99) >= p }
        }
        result = when (sortMode) {
            SortMode.NAME   -> result.sortedBy  { it.name.lowercase() }
            SortMode.RATING -> result.sortedByDescending { it.rating  ?: 0.0 }
            SortMode.WEIGHT -> result.sortedByDescending { it.weight  ?: 0.0 }
            SortMode.PLAYS  -> result.sortedByDescending { it.numPlays ?: 0 }
        }
        result
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Strip ─────────────────────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        when {
                            loading -> "Loading collection…"
                            allGames.isEmpty() && account == null -> "Sign in with Google in Settings and set a spreadsheet in Sync"
                            allGames.isEmpty() && spreadsheetId.isBlank() -> "Set a spreadsheet ID in Sync tab"
                            else -> "${filteredGames.size} of ${allGames.size} games"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (account != null && spreadsheetId.isNotBlank()) {
                        IconButton(
                            onClick = { account?.let(syncViewModel::loadCollection) },
                            enabled = !loading,
                            modifier = Modifier.size(24.dp)
                        ) { Icon(Icons.Default.Refresh, contentDescription = "Reload collection",
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Loading collection…", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error!!, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                        if (account != null) {
                            Button(onClick = { account?.let(syncViewModel::loadCollection) }) { Text("Retry") }
                        }
                    }
                }
                allGames.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text("No collection loaded", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (account == null) "Sign in with Google in Settings"
                            else if (spreadsheetId.isBlank()) "Set a Spreadsheet ID in the Sync tab"
                            else "Tap refresh to load your collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        if (account != null && spreadsheetId.isNotBlank()) {
                            Button(onClick = { account?.let(syncViewModel::loadCollection) }) { Text("Load Collection") }
                        }
                    }
                }
                else -> {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search games…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Owned / Wishlist tabs
                    Row(modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabMode.entries.forEach { tab ->
                            FilterChip(
                                selected = tabMode == tab,
                                onClick  = { tabMode = tab },
                                label    = { Text(tab.label) }
                            )
                        }
                    }

                    // Sort chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(SortMode.entries) { mode ->
                            FilterChip(
                                selected = sortMode == mode,
                                onClick  = { sortMode = mode },
                                label    = { Text(mode.label) }
                            )
                        }
                    }

                    // Player count filter chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = filterPlayers == null,
                                onClick  = { filterPlayers = null },
                                label    = { Text("Any players") }
                            )
                        }
                        items((1..6).toList()) { p ->
                            FilterChip(
                                selected = filterPlayers == p,
                                onClick  = { filterPlayers = if (filterPlayers == p) null else p },
                                label    = { Text(if (p == 6) "6+" else "$p") }
                            )
                        }
                    }

                    // Game list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredGames, key = { it.objectId.ifBlank { it.name } }) { game ->
                            GameCard(game)
                        }
                        if (filteredGames.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No games match your filters",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: GameItem) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val url = when {
                    !game.shareUrl.isNullOrBlank() -> game.shareUrl
                    game.objectId.isNotBlank()     -> "https://boardgamegeek.com/boardgame/${game.objectId}"
                    else                           -> null
                }
                url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {

            // Thumbnail
            if (!game.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = game.thumbnailUrl,
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp)
                )
            } else {
                Box(modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)

                // Rank + stats row
                val statParts = buildList {
                    game.rank?.let { add("#$it") }
                    game.rating?.let { add("★ ${"%.1f".format(it)}") }
                    game.weight?.let { add("⚖ ${"%.1f".format(it)}") }
                    game.playingTime?.let { add("⏱ ${it}m") }
                }.joinToString("  ")
                if (statParts.isNotBlank()) {
                    Text(statParts, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Players + year
                val detailParts = buildList {
                    val players = when {
                        game.minPlayers != null && game.maxPlayers != null && game.minPlayers != game.maxPlayers ->
                            "${game.minPlayers}–${game.maxPlayers} players"
                        game.minPlayers != null -> "${game.minPlayers} players"
                        else -> null
                    }
                    players?.let { add(it) }
                    game.yearPublished?.let { add("$it") }
                    if (game.numPlays != null && game.numPlays > 0) add("${game.numPlays} plays")
                }.joinToString("  ·  ")
                if (detailParts.isNotBlank()) {
                    Text(detailParts, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Status badges
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (game.isOwned) {
                        SuggestionChip(onClick = {}, label = { Text("Owned", style = MaterialTheme.typography.labelSmall) })
                    }
                    if (game.isWishlisted) {
                        SuggestionChip(onClick = {}, label = { Text("Wishlist", style = MaterialTheme.typography.labelSmall) })
                    }
                    if (!game.shareUrl.isNullOrBlank()) {
                        SuggestionChip(onClick = {}, label = { Text("Drive ↗", style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    }
}
