package com.bgg.combined.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bgg.combined.AppViewModel
import com.bgg.combined.model.BggGame
import com.bgg.combined.ui.common.GameSearchField
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPlayScreen(
    viewModel: AppViewModel,
    onGameSelected: (BggGame) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    val error   by viewModel.searchError.collectAsState()
    val collectionLoaded by viewModel.collectionLoaded.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadRecentGames() }

    LaunchedEffect(query) {
        delay(300)
        viewModel.filterGames(query)
    }

    val stripText = when {
        collectionLoaded && query.isBlank() -> "${results.size} games loaded · tap a game to start"
        collectionLoaded -> "${results.size} result${if (results.size == 1) "" else "s"} · tap to start logging"
        results.isNotEmpty() -> "Recently used · tap to log"
        else -> "Search by title, or load your BGG collection →"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Narrow strip — same style as Games / History
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                stripText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            GameSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search games...",
                modifier = Modifier.fillMaxWidth()
            )

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            if (collectionLoaded) "Searching…" else "Loading collection…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                error != null -> Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(error!!, color = MaterialTheme.colorScheme.onErrorContainer)
                            if (error!!.contains("private") || error!!.contains("401")) {
                                Text(
                                    "Tip: Make your BGG profile public in account settings, or use search mode.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { viewModel.loadRecentGames() }) {
                        Text("Use recent games instead")
                    }
                }

                results.isEmpty() && query.isNotBlank() -> Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No games found for \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                results.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                        Text(
                            "Search for a game above\nor load your BGG collection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    items(results) { game ->
                        GameRow(game = game, onClick = {
                            viewModel.selectGame(game)
                            onGameSelected(game)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: BggGame, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(game.name, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            game.yearPublished?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Log play",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
