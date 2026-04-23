package cz.nicolsburg.boardflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.nicolsburg.boardflow.core.di.AppContainer
import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.ExtractedPlay
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.GameRelations
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.PlayerResult
import cz.nicolsburg.boardflow.ui.theme.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.util.UUID

class AppViewModel(private val container: AppContainer) : ViewModel() {
    companion object {
        private const val CANONICAL_SNAPSHOT_ID = "__canonical_collection__"

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = AppViewModel(container) as T
        }
    }

    val prefs get() = container.securePreferences

    // --- Theme ---
    private val _appTheme = MutableStateFlow(
        try { AppTheme.valueOf(container.securePreferences.appTheme) }
        catch (_: Exception) { AppTheme.DARK }
    )
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    fun setAppTheme(theme: AppTheme) { _appTheme.value = theme; prefs.appTheme = theme.name }

    // --- Settings save callback ---
    var settingsSaveCallback: (() -> Unit)? = null

    // --- Network ---
    fun isOnline(): Boolean = container.isOnline()

    // --- Game search ---
    private val _recentGames = MutableStateFlow<List<BggGame>>(emptyList())
    private val _allGames = MutableStateFlow<List<BggGame>>(emptyList())
    val collection: StateFlow<List<BggGame>> = _allGames.asStateFlow()
    private val _searchResults = MutableStateFlow<List<BggGame>>(emptyList())
    val searchResults: StateFlow<List<BggGame>> = _searchResults.asStateFlow()
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    private val _collectionLoaded = MutableStateFlow(false)
    val collectionLoaded: StateFlow<Boolean> = _collectionLoaded.asStateFlow()

    fun loadRecentGames() {
        _recentGames.value = prefs.getRecentGames()
        if (_allGames.value.isNotEmpty()) return
        viewModelScope.launch {
            container.canonicalCollectionStore.migrateFromLegacySnapshotIfNeeded(prefs, CANONICAL_SNAPSHOT_ID)
            val cachedCollection = container.canonicalCollectionStore.getLightweightGames()
            if (cachedCollection.isNotEmpty()) {
                _allGames.value = cachedCollection
                _searchResults.value = cachedCollection
                _collectionLoaded.value = true
            } else {
                _searchResults.value = _recentGames.value
            }
        }
    }

    fun loadCollection() {
        val username = prefs.bggUsername
        if (username.isBlank()) { _searchError.value = "Please set your BGG username in Settings first"; return }
        viewModelScope.launch {
            _searchLoading.value = true; _searchError.value = null
            val creds = prefs.getCredentials()
            val result = if (creds != null) container.bggRepository.getUserCollectionAuthenticated(creds)
                         else container.bggRepository.getUserCollection(username)
            result.onSuccess { games ->
                _allGames.value = games.sortedBy { it.name }
                _searchResults.value = _allGames.value
                _collectionLoaded.value = true
            }.onFailure { _searchError.value = it.message; _collectionLoaded.value = false }
            _searchLoading.value = false
        }
    }

    fun clearCollection() {
        viewModelScope.launch {
            container.canonicalCollectionStore.clearAllGames()
            prefs.clearCollection()
            _allGames.value = emptyList()
            _collectionLoaded.value = false
            _searchResults.value = _recentGames.value
        }
    }

    fun updateFromCollection(games: List<GameItem>) {
        if (games.isEmpty()) return
        val bggGames = games.mapNotNull { item ->
            val id = item.objectId.toIntOrNull() ?: return@mapNotNull null
            BggGame(
                id = id,
                name = item.identity.name,
                yearPublished = item.yearPublished?.toString(),
                thumbnailUrl = item.thumbnailUrl
            )
        }.sortedBy { it.name }
        if (bggGames.isEmpty()) return
        _allGames.value = bggGames
        _searchResults.value = bggGames
        _collectionLoaded.value = true
    }

    fun filterGames(query: String) {
        _searchResults.value = if (query.isBlank()) {
            if (_collectionLoaded.value) _allGames.value else _recentGames.value
        } else {
            if (_collectionLoaded.value) _allGames.value.filter { it.name.contains(query, ignoreCase = true) }
            else { searchGames(query); emptyList() }
        }
    }

    fun searchGames(query: String) {
        if (query.isBlank()) { _searchResults.value = if (_collectionLoaded.value) _allGames.value else _recentGames.value; return }
        viewModelScope.launch {
            _searchLoading.value = true; _searchError.value = null
            container.bggRepository.searchGames(query)
                .onSuccess { _searchResults.value = it }
                .onFailure { _searchError.value = it.message }
            _searchLoading.value = false
        }
    }

    fun selectGame(game: BggGame) {
        selectedGame = game
        prefs.addRecentGame(game)
        _recentGames.value = prefs.getRecentGames()
        _extractedPlay.value = null; _editablePlayers.value = emptyList()
        _additionalGames.value = emptyList()
        _gameRelations.value = findRelatedGames(game, _allGames.value)
    }

    // --- Game relations ---
    private val _gameRelations = MutableStateFlow<GameRelations?>(null)
    val gameRelations: StateFlow<GameRelations?> = _gameRelations.asStateFlow()

    // --- Additional games ---
    private val _additionalGames = MutableStateFlow<List<BggGame>>(emptyList())
    val additionalGames: StateFlow<List<BggGame>> = _additionalGames.asStateFlow()

    fun toggleAdditionalGame(game: BggGame) {
        val current = _additionalGames.value
        _additionalGames.value = if (current.any { it.id == game.id }) current.filter { it.id != game.id } else current + game
    }

    // --- Scan / extraction ---
    private val _extractedPlay = MutableStateFlow<ExtractedPlay?>(null)
    val extractedPlay: StateFlow<ExtractedPlay?> = _extractedPlay.asStateFlow()
    private val _scanLoading = MutableStateFlow(false)
    val scanLoading: StateFlow<Boolean> = _scanLoading.asStateFlow()
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    var selectedGame: BggGame? = null

    fun extractScores(imageFile: File) {
        viewModelScope.launch {
            _scanLoading.value = true; _scanError.value = null; _extractedPlay.value = null
            container.geminiRepo.extractScoresFromImage(
                imageFile = imageFile, apiKey = prefs.geminiApiKey,
                modelName = prefs.geminiModelEndpoint, availableModels = prefs.getAvailableModels(),
                onModelChanged = { newModel -> prefs.geminiModelEndpoint = newModel }
            ).onSuccess { _extractedPlay.value = it }
             .onFailure { _scanError.value = it.message }
            _scanLoading.value = false
        }
    }

    fun checkAvailableModels(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            container.geminiRepo.listAvailableModels(prefs.geminiApiKey)
                .onSuccess { models -> prefs.saveAvailableModels(models); onResult(models) }
                .onFailure { onResult(emptyList()) }
        }
    }

    // --- Review / edit ---
    private val _editablePlayers = MutableStateFlow<List<PlayerResult>>(emptyList())
    val editablePlayers: StateFlow<List<PlayerResult>> = _editablePlayers.asStateFlow()

    fun initEditablePlayers(players: List<PlayerResult>) { _editablePlayers.value = players.toMutableList() }
    fun updatePlayer(index: Int, updated: PlayerResult) { _editablePlayers.value = _editablePlayers.value.toMutableList().also { it[index] = updated } }
    fun addPlayer() { _editablePlayers.value = _editablePlayers.value + PlayerResult("", "0", false) }
    fun removePlayer(index: Int) { _editablePlayers.value = _editablePlayers.value.toMutableList().also { it.removeAt(index) } }

    // --- Player roster ---
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    fun loadPlayers() { _players.value = prefs.getPlayers() }

    fun getPlayerSuggestions(input: String): List<Player> {
        if (input.length < 2) return emptyList()
        val lower = input.lowercase().trim(); val threshold = maxOf(2, lower.length / 3)
        return _players.value
            .filter { p -> (listOf(p.displayName) + p.aliases).any { levenshtein(lower, it.lowercase()) <= threshold } }
            .sortedBy { p -> (listOf(p.displayName) + p.aliases).minOf { levenshtein(lower, it.lowercase()) } }
            .take(5)
    }

    fun recordPlayerName(name: String) {
        if (name.isBlank()) return
        val lower = name.lowercase().trim(); val threshold = maxOf(2, lower.length / 3)
        val list = _players.value.toMutableList()
        val match = list.firstOrNull { p -> (listOf(p.displayName) + p.aliases).any { levenshtein(lower, it.lowercase()) <= threshold } }
        if (match != null) {
            val alreadyKnown = (listOf(match.displayName) + match.aliases).any { it.lowercase() == lower }
            if (!alreadyKnown) {
                val idx = list.indexOfFirst { it.id == match.id }
                list[idx] = match.copy(aliases = match.aliases + name.trim())
                _players.value = list; prefs.savePlayers(list)
            }
        } else {
            list.add(Player(UUID.randomUUID().toString(), name.trim(), emptyList()))
            _players.value = list; prefs.savePlayers(list)
        }
    }

    fun addNewPlayer(displayName: String) {
        if (displayName.isBlank()) return
        val list = (_players.value + Player(UUID.randomUUID().toString(), displayName.trim(), emptyList())).sortedBy { it.displayName.lowercase() }
        _players.value = list; prefs.savePlayers(list)
    }

    fun updatePlayerDisplayName(id: String, displayName: String) {
        if (displayName.isBlank()) return
        val list = _players.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = list[idx].copy(displayName = displayName.trim())
        _players.value = list.sortedBy { it.displayName.lowercase() }
        prefs.savePlayers(_players.value)
    }

    fun addPlayerAlias(id: String, alias: String) {
        val trimmed = alias.trim(); if (trimmed.isEmpty()) return
        val currentList = _players.value; val idx = currentList.indexOfFirst { it.id == id }; if (idx < 0) return
        val player = currentList[idx]
        if (player.aliases.any { it.lowercase() == trimmed.lowercase() }) return
        val newList = currentList.toMutableList(); newList[idx] = player.copy(aliases = player.aliases + trimmed)
        _players.value = newList.toList(); prefs.savePlayers(_players.value)
    }

    fun removePlayerAlias(id: String, alias: String) {
        val currentList = _players.value; val idx = currentList.indexOfFirst { it.id == id }; if (idx < 0) return
        val player = currentList[idx]
        val newList = currentList.toMutableList(); newList[idx] = player.copy(aliases = player.aliases.filter { it != alias })
        _players.value = newList.toList(); prefs.savePlayers(_players.value)
    }

    fun updatePlayerBggUsername(id: String, bggUsername: String) {
        val list = _players.value.toMutableList(); val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = list[idx].copy(bggUsername = bggUsername.trim())
        _players.value = list.toList(); prefs.savePlayers(_players.value)
    }

    fun deletePlayer(id: String) { _players.value = _players.value.filter { it.id != id }; prefs.savePlayers(_players.value) }

    // --- Play history (local) ---
    private val _playHistory = MutableStateFlow<List<LoggedPlay>>(emptyList())
    val playHistory: StateFlow<List<LoggedPlay>> = _playHistory.asStateFlow()
    private val _bggPlaysCacheAgeMinutes = MutableStateFlow(Long.MAX_VALUE)

    fun loadPlayHistory() {
        viewModelScope.launch {
            container.canonicalCollectionStore.migrateLegacyLoggedPlaysIfNeeded(prefs)
            _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
        }
    }
    fun clearPlayHistory() {
        viewModelScope.launch {
            container.canonicalCollectionStore.clearLoggedPlays()
            prefs.clearLoggedPlays()
            _playHistory.value = emptyList()
        }
    }

    // --- Play history (from BGG) ---
    private val _bggPlays = MutableStateFlow<List<LoggedPlay>>(emptyList())
    val bggPlays: StateFlow<List<LoggedPlay>> = _bggPlays.asStateFlow()
    private val _bggPlaysLoading = MutableStateFlow(false)
    val bggPlaysLoading: StateFlow<Boolean> = _bggPlaysLoading.asStateFlow()
    private val _bggPlaysError = MutableStateFlow<String?>(null)
    val bggPlaysError: StateFlow<String?> = _bggPlaysError.asStateFlow()
    private val _deletingBggPlayId = MutableStateFlow<String?>(null)
    val deletingBggPlayId: StateFlow<String?> = _deletingBggPlayId.asStateFlow()
    val historyPlays: StateFlow<List<LoggedPlay>> = combine(_playHistory, _bggPlays) { local, remote ->
        mergeHistorySources(local, remote)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun fetchBggPlays() {
        viewModelScope.launch {
            _bggPlaysLoading.value = true; _bggPlaysError.value = null
            cz.nicolsburg.boardflow.data.refreshBggPlayCache(prefs, container.canonicalCollectionStore, container.bggRepository)
                .onSuccess {
                    _bggPlays.value = mergeBggPlayLists(it)
                    _bggPlaysCacheAgeMinutes.value = container.canonicalCollectionStore.getBggPlaysCacheAgeMinutes()
                }
                .onFailure { _bggPlaysError.value = it.message }
            _bggPlaysLoading.value = false
        }
    }

    fun loadCachedBggPlays() {
        viewModelScope.launch {
            container.canonicalCollectionStore.migrateLegacyBggPlaysIfNeeded(prefs)
            val cached = container.canonicalCollectionStore.getBggPlaysCache()
            _bggPlaysCacheAgeMinutes.value = container.canonicalCollectionStore.getBggPlaysCacheAgeMinutes()
            if (cached.isNotEmpty()) {
                _bggPlays.value = mergeBggPlayLists(_bggPlays.value, cached)
            }
        }
    }
    fun isBggPlaysCacheStale(): Boolean = _bggPlaysCacheAgeMinutes.value > 4 * 60
    fun bggPlaysCacheAgeLabel(): String {
        val minutes = _bggPlaysCacheAgeMinutes.value
        return when { minutes == Long.MAX_VALUE -> ""; minutes < 60 -> "updated ${minutes}m ago"; else -> "updated ${minutes / 60}h ago" }
    }

    private fun addOptimisticBggPlays(plays: List<LoggedPlay>) {
        if (plays.isEmpty()) return
        viewModelScope.launch {
            _bggPlays.value = mergeBggPlayLists(plays, _bggPlays.value)
            container.canonicalCollectionStore.saveBggPlaysCache(_bggPlays.value)
            _bggPlaysCacheAgeMinutes.value = 0L
        }
    }

    fun deleteBggPlay(playId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isOnline()) { onError("Go online to delete plays from BGG"); return }
        val creds = prefs.getCredentials() ?: run { onError("BGG credentials not set"); return }
        val username = prefs.bggUsername.trim()
        if (username.isBlank()) { onError("BGG username not set"); return }
        _deletingBggPlayId.value = playId
        viewModelScope.launch {
            container.bggRepository.login(creds)
                .onFailure {
                    _deletingBggPlayId.value = null
                    onError(it.message ?: "Login failed")
                    return@launch
                }
            container.bggRepository.deletePlay(playId)
                .onSuccess {
                    container.bggRepository.getPlays(username)
                        .onSuccess { refreshed ->
                            if (refreshed.any { it.id == playId }) {
                                _deletingBggPlayId.value = null
                                onError("BGG did not confirm the delete yet. Please refresh and try again.")
                            } else {
                                _bggPlays.value = refreshed
                                container.canonicalCollectionStore.saveBggPlaysCache(refreshed)
                                _bggPlaysCacheAgeMinutes.value = 0L
                                _deletingBggPlayId.value = null
                                onSuccess()
                            }
                        }
                        .onFailure { error ->
                            _deletingBggPlayId.value = null
                            onError(error.message ?: "Deleted on BGG, but failed to refresh history")
                        }
                }
                .onFailure {
                    _deletingBggPlayId.value = null
                    onError(it.message ?: "Failed to delete play")
                }
        }
    }

    // --- Post to BGG ---
    private val _postLoading = MutableStateFlow(false)
    val postLoading: StateFlow<Boolean> = _postLoading.asStateFlow()
    private val _postResult = MutableStateFlow<String?>(null)
    val postResult: StateFlow<String?> = _postResult.asStateFlow()

    fun postPlay(date: LocalDate, durationMinutes: Int, location: String, comments: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val game = selectedGame ?: run { onError("No game selected"); return }
        val normalizedPlayers = normalizePlayersForPosting(_editablePlayers.value)
        if (!isOnline()) {
            val playersSnapshot = normalizedPlayers
            playersSnapshot.forEach { recordPlayerName(it.name) }
            val mainPlay = LoggedPlay(id = UUID.randomUUID().toString(), gameId = game.id, gameName = game.name, date = date.toString(), players = playersSnapshot, durationMinutes = durationMinutes, location = location, postedToBgg = false, comments = comments)
            viewModelScope.launch {
                container.canonicalCollectionStore.saveLoggedPlay(mainPlay)
                val extras = _additionalGames.value; _additionalGames.value = emptyList()
                extras.forEach { extra ->
                    container.canonicalCollectionStore.saveLoggedPlay(
                        LoggedPlay(
                            id = UUID.randomUUID().toString(),
                            gameId = extra.id,
                            gameName = extra.name,
                            date = date.toString(),
                            players = playersSnapshot,
                            durationMinutes = durationMinutes,
                            location = location,
                            postedToBgg = false,
                            comments = comments
                        )
                    )
                }
                prefs.addRecentGame(game)
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                onSuccess()
            }
            return
        }
        val creds = prefs.getCredentials() ?: run { onError("BGG credentials not set"); return }
        viewModelScope.launch {
            _postLoading.value = true
            container.bggRepository.login(creds).onFailure { _postLoading.value = false; onError(it.message ?: "Login failed"); return@launch }
            container.bggRepository.logPlay(gameId = game.id, date = date, players = normalizedPlayers, playerBggUsernames = buildBggUsernameMap(normalizedPlayers), durationMinutes = durationMinutes, location = location, comments = comments)
                .onSuccess {
                    normalizedPlayers.forEach { recordPlayerName(it.name) }
                    val postedPlays = mutableListOf<LoggedPlay>()
                    val mainPlay = LoggedPlay(
                        id = UUID.randomUUID().toString(),
                        gameId = game.id,
                        gameName = game.name,
                        date = date.toString(),
                        players = normalizedPlayers,
                        durationMinutes = durationMinutes,
                        location = location,
                        postedToBgg = true,
                        comments = comments
                    )
                    container.canonicalCollectionStore.saveLoggedPlay(mainPlay)
                    postedPlays += mainPlay
                    val extras = _additionalGames.value; _additionalGames.value = emptyList()
                    extras.forEach { extra ->
                        container.bggRepository.logPlay(gameId = extra.id, date = date, players = normalizedPlayers, playerBggUsernames = buildBggUsernameMap(normalizedPlayers), durationMinutes = durationMinutes, location = location, comments = comments)
                            .onSuccess {
                                val extraPlay = LoggedPlay(
                                    id = UUID.randomUUID().toString(),
                                    gameId = extra.id,
                                    gameName = extra.name,
                                    date = date.toString(),
                                    players = normalizedPlayers,
                                    durationMinutes = durationMinutes,
                                    location = location,
                                    postedToBgg = true,
                                    comments = comments
                                )
                                container.canonicalCollectionStore.saveLoggedPlay(extraPlay)
                                postedPlays += extraPlay
                            }
                    }
                    _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                    addOptimisticBggPlays(postedPlays)
                    _postLoading.value = false
                    onSuccess()
                }.onFailure { _postLoading.value = false; onError(it.message ?: "Failed to log play") }
        }
    }

    private fun buildBggUsernameMap(players: List<PlayerResult>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        players.forEachIndexed { index, pr ->
            val match = resolveRosterPlayer(pr.name) ?: return@forEachIndexed
            if (match.bggUsername.isNotBlank()) result[index] = match.bggUsername
        }
        return result
    }

    // --- Edit existing play ---
    private val _editPlayLoading = MutableStateFlow(false)
    val editPlayLoading: StateFlow<Boolean> = _editPlayLoading.asStateFlow()

    fun editPlay(
        play: LoggedPlay,
        date: String,
        durationMinutes: Int,
        location: String,
        comments: String,
        players: List<PlayerResult>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _editPlayLoading.value = true
            try {
                val normalizedPlayers = normalizePlayersForPosting(players)
                if (play.postedToBgg) {
                    if (!isOnline()) { onError("No internet connection"); return@launch }
                    val creds = prefs.getCredentials() ?: run { onError("BGG credentials not set"); return@launch }
                    container.bggRepository.login(creds).getOrThrow()
                    container.bggRepository.logPlay(
                        gameId = play.gameId,
                        date = LocalDate.parse(date),
                        players = normalizedPlayers,
                        playerBggUsernames = buildBggUsernameMap(normalizedPlayers),
                        durationMinutes = durationMinutes,
                        location = location,
                        comments = comments,
                        playId = play.id
                    ).getOrThrow()
                }
                container.canonicalCollectionStore.updateLoggedPlay(play.id) {
                    it.copy(
                        date = date,
                        durationMinutes = durationMinutes,
                        location = location,
                        comments = comments,
                        players = normalizedPlayers
                    )
                }
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update play")
            } finally {
                _editPlayLoading.value = false
            }
        }
    }

    // --- Export / Import ---
    fun exportData(includeSensitiveData: Boolean = false): String {
        val collection = runBlocking { container.canonicalCollectionStore.getAllGames() }
        val loggedPlays = runBlocking { container.canonicalCollectionStore.getLoggedPlays() }
        val bggPlays = runBlocking { container.canonicalCollectionStore.getBggPlaysCache() }
        return prefs.exportAll(
            includeSensitiveData = includeSensitiveData,
            collectionSnapshot = collection,
            loggedPlays = loggedPlays,
            cachedBggPlays = bggPlays
        )
    }

    // --- Sync unposted plays ---
    private val _postingPlayId = MutableStateFlow<String?>(null)
    val postingPlayId: StateFlow<String?> = _postingPlayId.asStateFlow()

    fun postSinglePlay(playId: String) {
        if (!isOnline()) return
        val creds = prefs.getCredentials() ?: return
        viewModelScope.launch {
            container.canonicalCollectionStore.migrateLegacyLoggedPlaysIfNeeded(prefs)
            val play = container.canonicalCollectionStore.getLoggedPlays().firstOrNull { it.id == playId } ?: run {
                _postingPlayId.value = null
                return@launch
            }
            _postingPlayId.value = playId
            container.bggRepository.login(creds).onFailure { _postingPlayId.value = null; return@launch }
            val normalizedPlayers = normalizePlayersForPosting(play.players)
            container.bggRepository.logPlay(gameId = play.gameId, date = LocalDate.parse(play.date), players = normalizedPlayers, playerBggUsernames = buildBggUsernameMap(normalizedPlayers), durationMinutes = play.durationMinutes, location = play.location, comments = play.comments)
                .onSuccess {
                    container.canonicalCollectionStore.updateLoggedPlay(play.id) { it.copy(postedToBgg = true, players = normalizedPlayers) }
                    addOptimisticBggPlays(
                        listOf(
                            play.copy(
                                players = normalizedPlayers,
                                postedToBgg = true
                            )
                        )
                    )
                }
            _postingPlayId.value = null; _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
        }
    }

    fun syncUnpostedPlays() {
        if (!isOnline()) return
        val creds = prefs.getCredentials() ?: return
        viewModelScope.launch {
            container.canonicalCollectionStore.migrateLegacyLoggedPlaysIfNeeded(prefs)
            val unposted = container.canonicalCollectionStore.getLoggedPlays().filter { !it.postedToBgg }
            if (unposted.isEmpty()) return@launch
            container.bggRepository.login(creds).onFailure { return@launch }
            val postedPlays = mutableListOf<LoggedPlay>()
            for (play in unposted) {
                val normalizedPlayers = normalizePlayersForPosting(play.players)
                container.bggRepository.logPlay(gameId = play.gameId, date = LocalDate.parse(play.date), players = normalizedPlayers, playerBggUsernames = buildBggUsernameMap(normalizedPlayers), durationMinutes = play.durationMinutes, location = play.location, comments = play.comments)
                    .onSuccess {
                        container.canonicalCollectionStore.updateLoggedPlay(play.id) { it.copy(postedToBgg = true, players = normalizedPlayers) }
                        postedPlays += play.copy(players = normalizedPlayers, postedToBgg = true)
                    }
            }
            addOptimisticBggPlays(postedPlays)
            _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
        }
    }

    fun importData(json: String) {
        viewModelScope.launch {
            val imported = prefs.importAll(json)
            val importedCollection = imported.collectionSnapshot
            if (importedCollection.isEmpty()) {
                container.canonicalCollectionStore.clearAllGames()
                _allGames.value = emptyList()
                _searchResults.value = _recentGames.value
                _collectionLoaded.value = false
            } else {
                container.canonicalCollectionStore.replaceAllGames(importedCollection)
                val lightweightGames = container.canonicalCollectionStore.getLightweightGames()
                _allGames.value = lightweightGames
                _searchResults.value = lightweightGames
                _collectionLoaded.value = true
            }
            container.canonicalCollectionStore.replaceLoggedPlays(imported.loggedPlays)
            _playHistory.value = imported.loggedPlays
            container.canonicalCollectionStore.saveBggPlaysCache(imported.cachedBggPlays)
            _bggPlays.value = mergeBggPlayLists(imported.cachedBggPlays)
            _bggPlaysCacheAgeMinutes.value = container.canonicalCollectionStore.getBggPlaysCacheAgeMinutes()

            try {
                _appTheme.value = AppTheme.valueOf(prefs.appTheme)
            } catch (_: Exception) {
                _appTheme.value = AppTheme.DARK
            }
            loadPlayers()
            loadRecentGames()
        }
    }

    fun setExtractedPlayManual() {
        _extractedPlay.value = ExtractedPlay(players = emptyList(), rawText = "Manual entry", date = java.time.LocalDate.now().toString())
    }

    fun clearExtractedPlay() { _extractedPlay.value = null; _editablePlayers.value = emptyList(); _additionalGames.value = emptyList(); _scanError.value = null }

    private fun normalizePlayersForPosting(players: List<PlayerResult>): List<PlayerResult> {
        return players.map { player ->
            val trimmedName = player.name.trim()
            if (trimmedName.isBlank()) {
                player.copy(name = trimmedName)
            } else {
                val match = resolveRosterPlayer(trimmedName)
                if (match != null) player.copy(name = match.displayName) else player.copy(name = trimmedName)
            }
        }
    }

    private fun resolveRosterPlayer(name: String): Player? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase()
        _players.value.firstOrNull { player ->
            (listOf(player.displayName) + player.aliases).any { it.lowercase().trim() == lower }
        }?.let { return it }

        val threshold = maxOf(2, lower.length / 3)
        return _players.value
            .map { player ->
                val bestDistance = (listOf(player.displayName) + player.aliases)
                    .minOf { alias -> levenshtein(lower, alias.lowercase().trim()) }
                player to bestDistance
            }
            .filter { (_, distance) -> distance <= threshold }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

}

private fun mergeHistorySources(local: List<LoggedPlay>, remote: List<LoggedPlay>): List<LoggedPlay> {
    val pendingLocal = local.filter { !it.postedToBgg }
    return (pendingLocal + remote).sortedByDescending { it.date }
}

private fun mergeBggPlayLists(vararg lists: List<LoggedPlay>): List<LoggedPlay> {
    val bySignature = linkedMapOf<String, LoggedPlay>()
    lists.asSequence()
        .flatMap { it.asSequence() }
        .forEach { play ->
            val signature = play.signatureKey()
            val existing = bySignature[signature]
            bySignature[signature] = when {
                existing == null -> play
                existing.prefersRemoteIdentityOver(play) -> existing
                play.prefersRemoteIdentityOver(existing) -> play
                else -> existing
            }
        }
    return bySignature.values.sortedByDescending { it.date }
}

private fun LoggedPlay.prefersRemoteIdentityOver(other: LoggedPlay): Boolean {
    val thisRemote = !id.isLikelyLocalUuid()
    val otherRemote = !other.id.isLikelyLocalUuid()
    return when {
        thisRemote && !otherRemote -> true
        !thisRemote && otherRemote -> false
        postedToBgg && !other.postedToBgg -> true
        !postedToBgg && other.postedToBgg -> false
        else -> false
    }
}

private fun LoggedPlay.signatureKey(): String {
    val normalizedPlayers = players.joinToString("|") { player ->
        listOf(
            player.name.trim().lowercase(),
            player.score.trim(),
            player.isWinner.toString(),
            player.rating.trim()
        ).joinToString("~")
    }
    return listOf(
        gameId.toString(),
        gameName.trim().lowercase(),
        date,
        durationMinutes.toString(),
        location.trim().lowercase(),
        comments.trim().lowercase(),
        quantity.toString(),
        incomplete.toString(),
        nowInStats.toString(),
        normalizedPlayers
    ).joinToString("||")
}

private fun String.isLikelyLocalUuid(): Boolean {
    return Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        .matches(this)
}

private fun findRelatedGames(game: BggGame, collection: List<BggGame>): GameRelations {
    val name = game.name.trim()
    fun separatorIndex(s: String): Int? = listOf(s.indexOf(':').takeIf { it > 0 }, s.indexOf(" \u2013 ").takeIf { it > 0 }, s.indexOf(" \u2014 ").takeIf { it > 0 }, s.indexOf(" - ").takeIf { it > 0 }).filterNotNull().minOrNull()
    fun rootOf(s: String) = separatorIndex(s)?.let { s.substring(0, it).trim() } ?: s.trim()
    val mySepIdx = separatorIndex(name)
    return if (mySepIdx != null) {
        val root = name.substring(0, mySepIdx).trim()
        val baseGames = collection.filter { other -> other.id != game.id && separatorIndex(other.name.trim()) == null && other.name.trim().equals(root, ignoreCase = true) }
        val siblings = collection.filter { other -> other.id != game.id && separatorIndex(other.name.trim()) != null && rootOf(other.name).equals(root, ignoreCase = true) }
        GameRelations(isExpansion = true, baseGames = baseGames, expansions = siblings)
    } else {
        val expansions = collection.filter { other -> other.id != game.id && rootOf(other.name).equals(name, ignoreCase = true) }
        GameRelations(isExpansion = false, baseGames = emptyList(), expansions = expansions)
    }
}

private fun levenshtein(a: String, b: String): Int {
    val m = a.length; val n = b.length
    val dp = Array(m + 1) { i -> IntArray(n + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
    for (i in 1..m) for (j in 1..n) {
        dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
    }
    return dp[m][n]
}
