package cz.nicolsburg.boardflow

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.nicolsburg.boardflow.BuildConfig
import cz.nicolsburg.boardflow.core.di.AppContainer
import cz.nicolsburg.boardflow.data.BggApiClient
import cz.nicolsburg.boardflow.data.GameRecognitionEngine
import cz.nicolsburg.boardflow.data.PlayerRecognitionEngine
import cz.nicolsburg.boardflow.data.chronicle.ChronicleAiConfig
import cz.nicolsburg.boardflow.data.normalizeForRecognition
import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.Challenge
import cz.nicolsburg.boardflow.model.ChallengeProgress
import cz.nicolsburg.boardflow.model.ChallengeType
import cz.nicolsburg.boardflow.model.ExtractedPlay
import cz.nicolsburg.boardflow.model.GameCandidate
import cz.nicolsburg.boardflow.model.GameRecognitionHint
import cz.nicolsburg.boardflow.model.PlayerRecognitionHint
import cz.nicolsburg.boardflow.model.ScanRecognitionResult
import cz.nicolsburg.boardflow.model.GameItem
import cz.nicolsburg.boardflow.model.GameRelations
import cz.nicolsburg.boardflow.model.LogPlayPrefill
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.PlaySession
import cz.nicolsburg.boardflow.model.Player
import cz.nicolsburg.boardflow.model.RecommendationLane
import cz.nicolsburg.boardflow.model.RecommendationPick
import cz.nicolsburg.boardflow.model.PlayerResult
import cz.nicolsburg.boardflow.model.RecordMoment
import cz.nicolsburg.boardflow.model.SessionContext
import cz.nicolsburg.boardflow.model.SessionMemory
import cz.nicolsburg.boardflow.model.SleeveManufacturer
import cz.nicolsburg.boardflow.model.trimMemorySuffix
import cz.nicolsburg.boardflow.util.toFlexibleLocalDateOrNull
import cz.nicolsburg.boardflow.model.StatsPlayScope
import cz.nicolsburg.boardflow.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

class AppViewModel(private val container: AppContainer) : ViewModel() {
    companion object {
        private const val CANONICAL_SNAPSHOT_ID = "__canonical_collection__"
        private const val TAG_SCAN = "QuickScan"
        private const val TAG_AUTO_SWITCH = "AutoSwitch"
        private const val TAG_PLAYER = "PlayerRecognition"

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

    // --- Sleeve preferred manufacturer ---
    private val _sleevePreferredManufacturer = MutableStateFlow(
        try { SleeveManufacturer.valueOf(container.securePreferences.sleevePreferredManufacturer) }
        catch (_: Exception) { SleeveManufacturer.AUTO }
    )
    val sleevePreferredManufacturer: StateFlow<SleeveManufacturer> = _sleevePreferredManufacturer.asStateFlow()

    fun setSleevePreferredManufacturer(manufacturer: SleeveManufacturer) {
        _sleevePreferredManufacturer.value = manufacturer
        prefs.sleevePreferredManufacturer = manufacturer.name
    }

    // --- History stats source ---
    private val _statsPlayScope = MutableStateFlow(
        try { StatsPlayScope.valueOf(container.securePreferences.statsPlayScope) }
        catch (_: Exception) { StatsPlayScope.ALL_PLAYS }
    )
    val statsPlayScope: StateFlow<StatsPlayScope> = _statsPlayScope.asStateFlow()

    fun setStatsPlayScope(scope: StatsPlayScope) {
        _statsPlayScope.value = scope
        prefs.statsPlayScope = scope.name
    }

    private val _recommendationsEnabled = MutableStateFlow(prefs.recommendationsEnabled)
    val recommendationsEnabled: StateFlow<Boolean> = _recommendationsEnabled.asStateFlow()

    fun setRecommendationsEnabled(enabled: Boolean) {
        _recommendationsEnabled.value = enabled
        prefs.recommendationsEnabled = enabled
    }

    private val _chronicleEnabled = MutableStateFlow(prefs.chronicleEnabled)
    val chronicleEnabled: StateFlow<Boolean> = _chronicleEnabled.asStateFlow()

    fun setChronicleEnabled(enabled: Boolean) {
        _chronicleEnabled.value = enabled
        prefs.chronicleEnabled = enabled
        if (!enabled) {
            synchronized(chronicleGenerationLock) {
                chronicleJobs.values.forEach { it.cancel() }
                chronicleJobs.clear()
                chronicleInFlightSourceKeys.clear()
                _chroniclePendingPlayIds.value = emptySet()
            }
        }
    }

    // --- Settings save callback ---
    var settingsSaveCallback: (() -> Unit)? = null

    // --- Network ---
    fun isOnline(): Boolean = container.isOnline()

    // --- Pending players (upfront selection before game pick) ---
    private val _pendingPlayers = MutableStateFlow<List<String>>(emptyList())
    val pendingPlayers: StateFlow<List<String>> = _pendingPlayers.asStateFlow()

    fun addPendingPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || _pendingPlayers.value.any { it.equals(trimmed, ignoreCase = true) }) return
        _pendingPlayers.value = _pendingPlayers.value + trimmed
    }

    fun removePendingPlayer(name: String) {
        _pendingPlayers.value = _pendingPlayers.value.filter { !it.equals(name, ignoreCase = true) }
    }

    // --- Game search ---
    private val _recentGames = MutableStateFlow<List<BggGame>>(emptyList())
    private val _allGames = MutableStateFlow<List<BggGame>>(emptyList())
    val collection: StateFlow<List<BggGame>> = _allGames.asStateFlow()
    private val _collectionItems = MutableStateFlow<List<GameItem>>(emptyList())
    val collectionItems: StateFlow<List<GameItem>> = _collectionItems.asStateFlow()
    private val _searchResults = MutableStateFlow<List<BggGame>>(emptyList())
    val searchResults: StateFlow<List<BggGame>> = _searchResults.asStateFlow()
    private var isBggSearchActive = false
    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()
    private val _collectionLoaded = MutableStateFlow(false)
    val collectionLoaded: StateFlow<Boolean> = _collectionLoaded.asStateFlow()

    // Owned-only games for Log Play — excludes wishlist items
    private val _ownedGames = MutableStateFlow<List<BggGame>>(emptyList())
    private val _logPlaySearchResults = MutableStateFlow<List<BggGame>>(emptyList())
    val logPlaySearchResults: StateFlow<List<BggGame>> = _logPlaySearchResults.asStateFlow()
    private var isLogPlayBggSearchActive = false
    private val _logPlayHasUnsavedChanges = MutableStateFlow(false)
    val logPlayHasUnsavedChanges: StateFlow<Boolean> = _logPlayHasUnsavedChanges.asStateFlow()

    private val _logPlayPostSaveShowing = MutableStateFlow(false)
    val logPlayPostSaveShowing: StateFlow<Boolean> = _logPlayPostSaveShowing.asStateFlow()
    fun setLogPlayPostSaveShowing(showing: Boolean) { _logPlayPostSaveShowing.value = showing }

    fun loadRecentGames() {
        _recentGames.value = prefs.getRecentGames()
        if (_allGames.value.isNotEmpty()) return
        viewModelScope.launch {
            val cachedCollection = container.canonicalCollectionStore.getAllGames()
            if (cachedCollection.isNotEmpty()) {
                updateFromCollection(cachedCollection)
            } else {
                if (!isBggSearchActive) _searchResults.value = _recentGames.value
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
                if (!isBggSearchActive) _searchResults.value = _allGames.value
                _collectionLoaded.value = true
            }.onFailure { _searchError.value = it.message; _collectionLoaded.value = false }
            _searchLoading.value = false
        }
    }

    fun updateFromCollection(games: List<GameItem>) {
        _collectionItems.value = games
        if (games.isEmpty()) {
            _allGames.value = emptyList()
            _ownedGames.value = emptyList()
            if (!isBggSearchActive) _searchResults.value = _recentGames.value
            if (!isLogPlayBggSearchActive) _logPlaySearchResults.value = _recentGames.value
            _collectionLoaded.value = false
            return
        }
        val bggGames = games.toSearchGames()
        if (bggGames.isEmpty()) return
        _allGames.value = bggGames
        if (!isBggSearchActive) _searchResults.value = bggGames
        val ownedGames = games.filter { it.isOwned }.toSearchGames()
        _ownedGames.value = ownedGames
        if (!isLogPlayBggSearchActive) _logPlaySearchResults.value = ownedGames.ifEmpty { _recentGames.value }
        _collectionLoaded.value = true
    }

    private fun List<GameItem>.toSearchGames(): List<BggGame> =
        mapNotNull { item ->
            val id = item.objectId.toIntOrNull() ?: return@mapNotNull null
            BggGame(
                id = id,
                name = item.name,
                yearPublished = item.yearPublished?.toString(),
                thumbnailUrl = item.thumbnailUrl
            )
        }.sortedBy { it.name }

    fun filterGames(query: String) {
        if (query.isBlank()) {
            isBggSearchActive = false
            _searchError.value = null
            _searchResults.value = if (_collectionLoaded.value) _allGames.value else _recentGames.value
            return
        }

        if (_collectionLoaded.value) {
            val localMatches = _allGames.value.filter { it.name.contains(query, ignoreCase = true) }
            if (localMatches.isNotEmpty()) {
                _searchError.value = null
                _searchResults.value = localMatches
                return
            }
        }

        _searchResults.value = emptyList()
        searchGames(query)
    }

    fun searchGames(query: String) {
        if (query.isBlank()) { _searchResults.value = if (_collectionLoaded.value) _allGames.value else _recentGames.value; return }
        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            val exactResult = container.bggRepository.searchGames(query, BuildConfig.BGG_XML_API_TOKEN, exact = true)
            if (exactResult.isSuccess && exactResult.getOrNull()?.isNotEmpty() == true) {
                isBggSearchActive = true
                _searchResults.value = exactResult.getOrNull()!!
                _searchLoading.value = false
                return@launch
            }
            container.bggRepository.searchGames(query, BuildConfig.BGG_XML_API_TOKEN, exact = false)
                .onSuccess { isBggSearchActive = true; _searchResults.value = it }
                .onFailure { _searchError.value = it.message }
            _searchLoading.value = false
        }
    }

    fun loadLogPlayGames() {
        _recentGames.value = prefs.getRecentGames()
        if (_ownedGames.value.isNotEmpty()) {
            if (!isLogPlayBggSearchActive) _logPlaySearchResults.value = _ownedGames.value
            return
        }
        viewModelScope.launch {
            val cachedCollection = container.canonicalCollectionStore.getAllGames()
            if (cachedCollection.isNotEmpty()) {
                updateFromCollection(cachedCollection)
            } else {
                _ownedGames.value = emptyList()
                if (!isLogPlayBggSearchActive) {
                    _logPlaySearchResults.value = _recentGames.value
                }
            }
        }
    }

    fun filterLogPlayGames(query: String) {
        if (query.isBlank()) {
            isLogPlayBggSearchActive = false
            _searchError.value = null
            _logPlaySearchResults.value = _ownedGames.value.ifEmpty { _recentGames.value }
            return
        }
        val localMatches = _ownedGames.value.filter { it.name.contains(query, ignoreCase = true) }
        if (localMatches.isNotEmpty()) {
            _searchError.value = null
            _logPlaySearchResults.value = localMatches
            return
        }
        _logPlaySearchResults.value = emptyList()
        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            val exactResult = container.bggRepository.searchGames(query, BuildConfig.BGG_XML_API_TOKEN, exact = true)
            if (exactResult.isSuccess && exactResult.getOrNull()?.isNotEmpty() == true) {
                isLogPlayBggSearchActive = true
                _logPlaySearchResults.value = exactResult.getOrNull()!!
                _searchLoading.value = false
                return@launch
            }
            container.bggRepository.searchGames(query, BuildConfig.BGG_XML_API_TOKEN, exact = false)
                .onSuccess { isLogPlayBggSearchActive = true; _logPlaySearchResults.value = it }
                .onFailure { _searchError.value = it.message }
            _searchLoading.value = false
        }
    }

    fun selectGame(game: BggGame) {
        isBggSearchActive = false
        selectedGame = game
        _logPlayHasUnsavedChanges.value = false
        prefs.addRecentGame(game)
        _recentGames.value = prefs.getRecentGames()
        _additionalGames.value = emptyList()
        _gameRelations.value = findRelatedGames(game, _allGames.value)

        // In correction mode applyDetectedGameCorrection runs immediately after and
        // handles _extractedPlay / _editablePlayers itself — don't clear them here.
        if (_quickScanCorrectionMode.value) return

        _extractedPlay.value = null

        val pending = _changeGameSession?.takeIf { it.isActive() }
        _changeGameSession = null
        _changeGameSessionActive.value = false
        when {
            pending != null -> {
                _editablePlayers.value = pending.players.map { it.copy(score = "0", isWinner = false) }
                _logPlayPrefill = LogPlayPrefill(location = pending.location)
            }
            _pendingPlayers.value.isNotEmpty() -> {
                _editablePlayers.value = _pendingPlayers.value.map { PlayerResult(name = it, score = "0", isWinner = false) }
            }
            _sessionContext.value?.isRecent() == true && _sessionContext.value?.gameId == game.id -> {
                val session = _sessionContext.value!!
                _editablePlayers.value = session.players.map { it.copy(score = "0", isWinner = false) }
                _logPlayPrefill = LogPlayPrefill(location = session.location)
            }
            else -> _editablePlayers.value = emptyList()
        }
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
    private var sessionModel: String? = null
    private var sessionModelExpiry: Long = 0L
    private fun effectiveModel(): String {
        val now = System.currentTimeMillis()
        return if (sessionModel != null && now < sessionModelExpiry) sessionModel!! else prefs.geminiModelEndpoint
    }

    private val _extractedPlay = MutableStateFlow<ExtractedPlay?>(null)
    val extractedPlay: StateFlow<ExtractedPlay?> = _extractedPlay.asStateFlow()
    private val _scanLoading = MutableStateFlow(false)
    val scanLoading: StateFlow<Boolean> = _scanLoading.asStateFlow()
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val recognitionEngine = GameRecognitionEngine()
    private val _gameCandidates = MutableStateFlow<List<GameCandidate>>(emptyList())
    val gameCandidates: StateFlow<List<GameCandidate>> = _gameCandidates.asStateFlow()
    private val _scanRecognitionResult = MutableStateFlow<ScanRecognitionResult?>(null)
    val scanRecognitionResult: StateFlow<ScanRecognitionResult?> = _scanRecognitionResult.asStateFlow()
    private var _retryJob: Job? = null
    private val _scanRetryResult = MutableStateFlow<ExtractedPlay?>(null)
    val scanRetryResult: StateFlow<ExtractedPlay?> = _scanRetryResult.asStateFlow()
    private val _quickScanCorrectionMode = MutableStateFlow(false)
    val quickScanCorrectionMode: StateFlow<Boolean> = _quickScanCorrectionMode.asStateFlow()
    private val _pendingWidgetQuickScan = MutableStateFlow(false)
    val pendingWidgetQuickScan: StateFlow<Boolean> = _pendingWidgetQuickScan.asStateFlow()
    private val _pendingWidgetOpenGameId = MutableStateFlow<Int?>(null)
    val pendingWidgetOpenGameId: StateFlow<Int?> = _pendingWidgetOpenGameId.asStateFlow()
    /** True when the scan was started after a game was explicitly pre-selected (id != 0). */
    private val _scanStartedWithGame = MutableStateFlow(false)
    val scanStartedWithGame: StateFlow<Boolean> = _scanStartedWithGame.asStateFlow()

    var selectedGame: BggGame? = null

    /**
     * Switches the active game for an in-progress scan without disturbing the
     * extracted play data (players/scores/date). Used by game-detection auto-switch
     * and user-confirmed suggestion acceptance.
     */
    private fun applyDetectedGame(game: BggGame) {
        selectedGame = game
        _gameRelations.value = findRelatedGames(game, _allGames.value)
        _logPlayHasUnsavedChanges.value = false
        prefs.addRecentGame(game)
        _recentGames.value = prefs.getRecentGames()
    }

    private fun saveHintForGame(game: BggGame, extracted: ExtractedPlay) {
        val normTitle = extracted.detectedGameTitle?.let { normalizeForRecognition(it) }
        val normCats = extracted.detectedScoringCategories.map { normalizeForRecognition(it) }.filter { it.isNotBlank() }
        val titles = listOfNotNull(normalizeForRecognition(game.name).takeIf { it.isNotBlank() }, normTitle).distinct()
        prefs.saveGameRecognitionHint(
            GameRecognitionHint(
                gameObjectId = game.id.toString(),
                gameName = game.name,
                normalizedTitles = titles,
                normalizedCategories = normCats,
                confirmedAt = System.currentTimeMillis(),
                timesConfirmed = 1
            )
        )
    }

    /** User confirmed a game suggestion from the detected candidates list. */
    fun acceptGameSuggestion(game: BggGame) {
        _extractedPlay.value?.let { saveHintForGame(game, it) }
        applyDetectedGame(game)
        _gameCandidates.value = emptyList()
    }

    fun clearGameRecognitionHints() { prefs.clearGameRecognitionHints() }
    fun deleteGameRecognitionHint(gameObjectId: String) { prefs.deleteGameRecognitionHint(gameObjectId) }
    fun replaceGameRecognitionHint(hint: GameRecognitionHint) { prefs.replaceGameRecognitionHint(hint) }

    /** User dismissed the game suggestion banner without accepting any candidate. */
    fun dismissGameSuggestion() {
        _gameCandidates.value = emptyList()
    }

    fun dismissScanRecognitionResult() {
        _scanRecognitionResult.value = null
        clearQuickScanCorrectionMode("recognition result dismissed by user")
    }

    private fun cancelBackgroundRetry() {
        _retryJob?.cancel()
        _retryJob = null
        _scanRetryResult.value = null
    }

    fun acceptRetryResult() {
        val retried = _scanRetryResult.value ?: return
        _scanRetryResult.value = null
        _extractedPlay.value = retried
        if (retried.players.isNotEmpty()) initEditablePlayers(retried.players)
    }

    fun dismissRetryResult() {
        _scanRetryResult.value = null
        _retryJob?.cancel()
        _retryJob = null
    }

    /** Enters the mode where the next game selection from NewPlayScreen returns to LogPlay without a new scan. */
    fun enterQuickScanCorrectionMode() {
        Log.d(TAG_SCAN, "Entering correction mode; extractedPlay preserved=${_extractedPlay.value != null}")
        _quickScanCorrectionMode.value = true
    }

    /** Called by AppShell when the user presses back from NewPlayScreen while correction mode is active. */
    fun exitQuickScanCorrectionMode() {
        clearQuickScanCorrectionMode("back pressed from NewPlay without game selection")
    }

    /** Signal from MainActivity that the widget tapped — AppShell will consume and navigate. */
    fun requestWidgetQuickScan() {
        _pendingWidgetQuickScan.value = true
    }

    fun consumeWidgetQuickScan() {
        _pendingWidgetQuickScan.value = false
    }

    fun requestWidgetOpenPlay(gameId: Int) {
        _pendingWidgetOpenGameId.value = gameId
    }

    fun consumeWidgetOpenPlay() {
        _pendingWidgetOpenGameId.value = null
    }

    /** Called when the user selects a correction game. Applies it without clearing extracted scan data. */
    fun applyDetectedGameCorrection(game: BggGame) {
        val extracted = _extractedPlay.value
        Log.d(TAG_SCAN, "Correction game selected: ${game.name}; extractedDataPreserved=${extracted != null} players=${extracted?.players?.size ?: 0}")
        extracted?.let { saveHintForGame(game, it) }
        if (extracted != null && extracted.players.isNotEmpty()) {
            initEditablePlayers(extracted.players)
            Log.d(TAG_SCAN, "Re-initialized ${extracted.players.size} player(s) from extracted play")
        }
        applyDetectedGame(game)
        _gameCandidates.value = emptyList()
        _scanRecognitionResult.value = null
        clearQuickScanCorrectionMode("game selection confirmed")
    }

    private fun clearQuickScanCorrectionMode(reason: String) {
        if (_quickScanCorrectionMode.value) {
            Log.d(TAG_SCAN, "Correction mode cleared: $reason")
            _quickScanCorrectionMode.value = false
        }
    }

    fun extractScores(imageFile: File) {
        viewModelScope.launch {
            _scanStartedWithGame.value = (selectedGame?.id ?: 0) != 0
            Log.d(TAG_AUTO_SWITCH, "Scan started; preselectedGame=${selectedGame?.name ?: "none"} scanStartedWithGame=${_scanStartedWithGame.value}")
            _scanLoading.value = true; _scanError.value = null; _extractedPlay.value = null
            _gameCandidates.value = emptyList(); _scanRecognitionResult.value = null
            clearQuickScanCorrectionMode("new scan started")
            container.geminiRepo.extractScoresFromImage(
                imageFile = imageFile, apiKey = prefs.geminiApiKey,
                modelName = effectiveModel(), availableModels = prefs.getAvailableModels(),
                availableApiKeys = prefs.getGeminiExtraApiKeys(),
                onModelChanged = { newModel ->
                    sessionModel = newModel
                    sessionModelExpiry = System.currentTimeMillis() + 5 * 60 * 1000L
                },
                onModelExhausted = { exhaustedModel ->
                    prefs.removeAvailableModel(exhaustedModel)
                    Log.d(TAG_SCAN, "Removed exhausted model from available list: $exhaustedModel")
                }
            ).onSuccess { extracted ->
                _extractedPlay.value = extracted
                val hints = prefs.loadGameRecognitionHints()
                val candidates = recognitionEngine.rankCandidates(extracted, _collectionItems.value, hints)
                val geminiConfidence = extracted.detectedGameConfidence ?: 0f
                val top = candidates.firstOrNull()
                val second = candidates.getOrNull(1)
                val margin = if (top != null && second != null) top.score - second.score else top?.score ?: 0f
                val condMargin = second == null || margin >= 0.15f
                val condTitlePresent = !extracted.detectedGameTitle.isNullOrBlank()

                // TITLE_GATE: classic path — title drove the match, high score required.
                val condTitleScore = top != null && top.score >= 0.90f
                // Strong-title lowered threshold: allow 90% Gemini confidence when the local
                // title match is unambiguous (topScore >= 98%, margin >= 25%).
                val isStrongTitleMatch = top != null
                    && top.primarySignal == "title"
                    && condTitlePresent
                    && top.score >= 0.98f
                    && margin >= 0.25f
                val requiredGeminiConfForTitle = if (isStrongTitleMatch) 0.90f else 0.95f
                val condConfidenceTitle = geminiConfidence >= requiredGeminiConfForTitle
                val titleGate = top != null && condConfidenceTitle && condTitleScore && condMargin && condTitlePresent
                    && top.primarySignal != "category-template"

                // TEMPLATE_CATEGORY_GATE: category template drove the match — lower score
                // threshold compensates for the fact that category-only scores peak at 0.75.
                val condTemplateScore = top != null && top.score >= 0.75f
                val condTemplateOverlap = top != null && top.templateOverlap >= 3
                val condConfidenceTemplate = geminiConfidence >= 0.95f
                val templateCategoryGate = top != null && condConfidenceTemplate && condTemplateScore
                    && condMargin && condTemplateOverlap
                    && top.primarySignal == "category-template"

                val autoSwitch = titleGate || templateCategoryGate
                val gateUsed = when {
                    titleGate            -> if (isStrongTitleMatch) "TITLE_GATE(strong)" else "TITLE_GATE"
                    templateCategoryGate -> "TEMPLATE_CATEGORY_GATE"
                    else                 -> "BLOCKED"
                }
                Log.d(TAG_AUTO_SWITCH, buildString {
                    append("gate=$gateUsed ")
                    val reqPct = if (titleGate || !templateCategoryGate) (requiredGeminiConfForTitle * 100).toInt() else 95
                    append("geminiConf=${(geminiConfidence * 100).toInt()}%(need>=$reqPct strongTitle=$isStrongTitleMatch ok=${titleGate || templateCategoryGate}) ")
                    append("topScore=${top?.let { (it.score * 100).toInt() } ?: "none"}% ")
                    append("margin=${(margin * 100).toInt()}%(need>=15 ok=$condMargin) ")
                    append("primarySignal=${top?.primarySignal ?: "n/a"} ")
                    append("templateOverlap=${top?.templateOverlap ?: 0}(need>=3 ok=$condTemplateOverlap) ")
                    append("titlePresent=$condTitlePresent ")
                    append("top='${top?.game?.name}' second='${second?.game?.name}' ")
                    append("-> autoSwitch=$autoSwitch")
                })
                if (autoSwitch) {
                    // top is guaranteed non-null by the autoSwitch condition above
                    if (selectedGame?.id != top!!.game.id) applyDetectedGame(top.game)
                    saveHintForGame(top.game, extracted)
                    _gameCandidates.value = emptyList()
                    _scanRecognitionResult.value = ScanRecognitionResult.AutoSwitched(top.game.name)
                } else {
                    _gameCandidates.value = candidates
                    if (candidates.isEmpty()) {
                        val detectedTitle = extracted.detectedGameTitle
                        _scanRecognitionResult.value = if (!detectedTitle.isNullOrBlank()) {
                            ScanRecognitionResult.NoCollectionMatch(detectedTitle)
                        } else {
                            ScanRecognitionResult.LowConfidence
                        }
                    } else {
                        // Suggestion banner handles this case; no extra banner needed.
                        _scanRecognitionResult.value = null
                    }
                }

                if (extracted.isMalformed) {
                    _retryJob?.cancel()
                    _retryJob = viewModelScope.launch {
                        container.geminiRepo.extractScoresFromImage(
                            imageFile = imageFile,
                            apiKey = prefs.geminiApiKey,
                            modelName = effectiveModel(),
                            availableModels = prefs.getAvailableModels(),
                            availableApiKeys = prefs.getGeminiExtraApiKeys(),
                            onModelChanged = { newModel ->
                                sessionModel = newModel
                                sessionModelExpiry = System.currentTimeMillis() + 5 * 60 * 1000L
                            },
                            onModelExhausted = { exhaustedModel ->
                                prefs.removeAvailableModel(exhaustedModel)
                                Log.d(TAG_SCAN, "Removed exhausted model from available list: $exhaustedModel")
                            }
                        ).onSuccess { retried ->
                            if (!retried.isMalformed) _scanRetryResult.value = retried
                        }
                    }
                }
            }.onFailure {
                Log.e(TAG_AUTO_SWITCH, "Scan failed: ${it.message}")
                _scanError.value = it.message
            }
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
    // Raw scanned names recorded at init time; used to build player hints on successful log.
    private var _originalScannedNames: List<String> = emptyList()

    fun initEditablePlayers(players: List<PlayerResult>) {
        _originalScannedNames = players.map { it.name }
        val hints = prefs.loadPlayerRecognitionHints()
        val roster = _players.value
        val resolved = players.map { pr ->
            val match = PlayerRecognitionEngine.resolve(pr.name, roster, hints)
            if (match != null && match.confidence >= 0.70f && match.source != "fuzzy") {
                pr.copy(name = match.player.displayName)
            } else pr
        }
        _editablePlayers.value = resolved.toMutableList()
    }
    fun updatePlayer(index: Int, updated: PlayerResult) { _editablePlayers.value = _editablePlayers.value.toMutableList().also { it[index] = updated } }
    fun addPlayer() { _editablePlayers.value = _editablePlayers.value + PlayerResult("", "", false) }
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
        val lower = name.lowercase().trim()
        val list = _players.value.toMutableList()
        val alreadyKnown = list.any { p ->
            (listOf(p.displayName) + p.aliases).any { it.lowercase().trim() == lower }
        }
        if (!alreadyKnown) {
            list.add(Player(UUID.randomUUID().toString(), name.trim(), emptyList()))
            _players.value = list
            prefs.savePlayers(list)
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

    // --- Challenges ---
    private val _challenges = MutableStateFlow<List<Challenge>>(emptyList())
    val challenges: StateFlow<List<Challenge>> = _challenges.asStateFlow()

    fun loadChallenges() {
        _challenges.value = prefs.getChallenges()
        ensureMonthlyChallenge()
    }

    private fun ensureMonthlyChallenge() {
        val today = LocalDate.now()
        val monthId = "auto_monthly_${today.year}-${today.monthValue.toString().padStart(2, '0')}"
        if (_challenges.value.none { it.id == monthId }) {
            val monthName = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            addChallenge(
                Challenge(
                    id = monthId,
                    title = "Play 10 games in $monthName",
                    type = ChallengeType.PLAY_N_TIMES,
                    targetCount = 10,
                    startDate = today.withDayOfMonth(1).toString(),
                    endDate = today.withDayOfMonth(today.lengthOfMonth()).toString()
                )
            )
        }
    }

    fun addChallenge(challenge: Challenge) {
        val updated = _challenges.value + challenge
        _challenges.value = updated
        prefs.saveChallenges(updated)
    }

    fun deleteChallenge(id: String) {
        val updated = _challenges.value.filter { it.id != id }
        _challenges.value = updated
        prefs.saveChallenges(updated)
    }

    fun getChallengeProgressList(): List<ChallengeProgress> {
        val history = _playHistory.value
        val roster = _players.value
        return _challenges.value.map { challenge ->
            val plays = history.filter { play ->
                val afterStart = challenge.startDate == null || play.date >= challenge.startDate
                val beforeEnd = challenge.endDate == null || play.date <= challenge.endDate
                afterStart && beforeEnd
            }
            val progress = when (challenge.type) {
                ChallengeType.PLAY_N_TIMES -> {
                    val count = plays.sumOf { it.quantity.coerceAtLeast(1) }
                    ChallengeProgress(challenge = challenge, currentCount = count, goalCount = challenge.targetCount)
                }
                ChallengeType.PLAY_SPECIFIC_GAME -> {
                    val count = plays.filter { it.gameId == challenge.gameId }.sumOf { it.quantity.coerceAtLeast(1) }
                    ChallengeProgress(challenge = challenge, currentCount = count, goalCount = challenge.targetCount)
                }
                ChallengeType.PLAY_N_DISTINCT -> {
                    val count = plays.map { it.gameId }.distinct().size
                    ChallengeProgress(challenge = challenge, currentCount = count, goalCount = challenge.targetCount)
                }
                ChallengeType.PLAYER_WIN_STREAK -> {
                    val playerNames = challenge.resolveTrackedPlayerNames(roster)
                    val bestStreak = plays.bestWinStreak(playerNames)
                    val remainingText = when {
                        playerNames.isEmpty() -> "Pick a roster player for this goal"
                        bestStreak >= challenge.targetCount -> "Win streak reached"
                        else -> "${challenge.targetCount - bestStreak} more consecutive wins to go"
                    }
                    ChallengeProgress(
                        challenge = challenge,
                        currentCount = bestStreak,
                        goalCount = challenge.targetCount,
                        remainingText = remainingText
                    )
                }
                ChallengeType.PLAY_WITH_GROUP_N_TIMES -> {
                    val playerTargets = challenge.resolveTrackedPlayers(roster)
                    val count = plays.sumOf { play ->
                        if (play.matchesTrackedGroup(playerTargets)) play.quantity.coerceAtLeast(1) else 0
                    }
                    val remainingText = when {
                        playerTargets.isEmpty() -> "Pick at least two roster players for this goal"
                        count >= challenge.targetCount -> "Group goal complete"
                        else -> "${challenge.targetCount - count} more group plays to go"
                    }
                    ChallengeProgress(
                        challenge = challenge,
                        currentCount = count,
                        goalCount = challenge.targetCount,
                        remainingText = remainingText
                    )
                }
                ChallengeType.PLAY_STREAK -> {
                    val period = challenge.streakPeriod ?: "WEEKLY"
                    val best = plays.bestPlayStreak(period)
                    val periodLabel = when (period) {
                        "DAILY" -> "day"
                        "MONTHLY" -> "month"
                        else -> "week"
                    }
                    val remainingText = if (best >= challenge.targetCount)
                        "${challenge.targetCount}-$periodLabel streak reached"
                    else
                        "${challenge.targetCount - best} more consecutive ${periodLabel}s to go"
                    ChallengeProgress(
                        challenge = challenge,
                        currentCount = best,
                        goalCount = challenge.targetCount,
                        remainingText = remainingText
                    )
                }
                ChallengeType.PLAY_N_UNPLAYED -> {
                    val ownedIds = _collectionItems.value
                        .filter { it.isOwned }
                        .mapNotNull { it.objectId.toIntOrNull() }
                        .toSet()
                    val effectiveStart = challenge.startDate
                        ?: LocalDate.ofEpochDay(challenge.createdAt / 86_400_000L).toString()
                    val playedBefore = history.filter { it.date < effectiveStart }.map { it.gameId }.toSet()
                    val count = plays.map { it.gameId }.distinct()
                        .count { it in ownedIds && it !in playedBefore }
                    ChallengeProgress(challenge = challenge, currentCount = count, goalCount = challenge.targetCount)
                }
            }

            progress
        }
    }

    private data class ChallengeTrackedPlayer(
        val id: String? = null,
        val names: Set<String>
    )

    private fun Challenge.resolveTrackedPlayers(roster: List<Player>): List<ChallengeTrackedPlayer> {
        val byId = roster.associateBy { it.id }
        val resolved = mutableListOf<ChallengeTrackedPlayer>()

        playerIds.forEachIndexed { index, playerId ->
            val rosterPlayer = byId[playerId]
            val storedName = playerNames.getOrNull(index)?.trim().orEmpty()
            val names = buildSet {
                rosterPlayer?.let { player ->
                    add(player.displayName.trim().lowercase())
                    player.aliases.mapTo(this) { it.trim().lowercase() }
                    player.bggUsername.trim().takeIf { it.isNotBlank() }?.lowercase()?.let(::add)
                }
                storedName.takeIf { it.isNotBlank() }?.lowercase()?.let(::add)
            }.filter { it.isNotBlank() }.toSet()
            if (names.isNotEmpty()) resolved += ChallengeTrackedPlayer(id = playerId, names = names)
        }

        playerNames.forEach { rawName ->
            val normalized = rawName.trim().lowercase()
            if (normalized.isBlank()) return@forEach
            val alreadyCovered = resolved.any { normalized in it.names }
            if (!alreadyCovered) {
                val rosterPlayer = roster.firstOrNull { player ->
                    player.displayName.trim().lowercase() == normalized ||
                        player.aliases.any { it.trim().lowercase() == normalized } ||
                        player.bggUsername.trim().lowercase() == normalized
                }
                val names = buildSet {
                    add(normalized)
                    rosterPlayer?.let { player ->
                        add(player.displayName.trim().lowercase())
                        player.aliases.mapTo(this) { it.trim().lowercase() }
                        player.bggUsername.trim().takeIf { it.isNotBlank() }?.lowercase()?.let(::add)
                    }
                }.filter { it.isNotBlank() }.toSet()
                resolved += ChallengeTrackedPlayer(id = rosterPlayer?.id, names = names)
            }
        }

        return resolved.distinctBy { it.id ?: it.names.sorted().joinToString("|") }
    }

    private fun Challenge.resolveTrackedPlayerNames(roster: List<Player>): Set<String> =
        resolveTrackedPlayers(roster).flatMapTo(linkedSetOf()) { it.names }

    private fun LoggedPlay.matchesTrackedGroup(targets: List<ChallengeTrackedPlayer>): Boolean {
        if (targets.isEmpty()) return false
        val playNames = players.mapNotNull { player ->
            player.name.trim().lowercase().takeIf { it.isNotBlank() }
        }.toSet()
        return targets.all { tracked -> tracked.names.any(playNames::contains) }
    }

    private fun List<LoggedPlay>.bestWinStreak(playerNames: Set<String>): Int {
        if (playerNames.isEmpty()) return 0
        val relevantPlays = filter { play ->
            play.players.any { it.name.trim().lowercase() in playerNames }
        }.sortedWith(compareBy<LoggedPlay>({ it.date }, { it.playedAt ?: 0L }, { it.id }))

        var best = 0
        var current = 0
        relevantPlays.forEach { play ->
            val won = play.players.any { it.name.trim().lowercase() in playerNames && it.isWinner }
            if (won) {
                current += 1
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    private fun List<LoggedPlay>.bestPlayStreak(period: String): Int {
        if (isEmpty()) return 0
        val keys = mapNotNull { play ->
            runCatching { LocalDate.parse(play.date) }.getOrNull()?.let { date ->
                when (period) {
                    "DAILY" -> play.date
                    "MONTHLY" -> "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
                    else -> {
                        val weekYear = date.get(WeekFields.ISO.weekBasedYear())
                        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
                        "$weekYear-${week.toString().padStart(2, '0')}"
                    }
                }
            }
        }.distinct().sorted()
        if (keys.isEmpty()) return 0
        var best = 1
        var current = 1
        for (i in 1 until keys.size) {
            if (areConsecutivePeriods(keys[i - 1], keys[i], period)) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }

    private fun areConsecutivePeriods(a: String, b: String, period: String): Boolean {
        if (period == "DAILY") {
            val da = runCatching { LocalDate.parse(a) }.getOrNull() ?: return false
            val db = runCatching { LocalDate.parse(b) }.getOrNull() ?: return false
            return ChronoUnit.DAYS.between(da, db) == 1L
        }
        val (ay, an) = a.split("-").map { it.toInt() }
        val (by, bn) = b.split("-").map { it.toInt() }
        return if (period == "MONTHLY") {
            (by * 12 + bn) - (ay * 12 + an) == 1
        } else {
            when {
                ay == by -> bn - an == 1
                by == ay + 1 && bn == 1 -> {
                    val lastWeek = LocalDate.of(ay, 12, 28).get(WeekFields.ISO.weekOfWeekBasedYear())
                    an == lastWeek
                }
                else -> false
            }
        }
    }

    private fun stampPlayerLastPlayed(players: List<PlayerResult>, playedAt: Long) {
        val names = players.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val updated = _players.value.map { roster ->
            val matches = (listOf(roster.displayName) + roster.aliases).any { it.trim().lowercase() in names }
            if (matches && (roster.lastPlayedAt == null || playedAt > roster.lastPlayedAt)) {
                roster.copy(lastPlayedAt = playedAt)
            } else roster
        }
        if (updated != _players.value) {
            _players.value = updated
            prefs.savePlayers(updated)
        }
    }

    // --- Custom moods ---
    private val _customMoods = MutableStateFlow(prefs.getCustomMoods())
    val customMoods: StateFlow<List<String>> = _customMoods.asStateFlow()
    private val _moodUsageOrder = MutableStateFlow(prefs.getMoodUsageOrder())
    val moodUsageOrder: StateFlow<List<String>> = _moodUsageOrder.asStateFlow()
    private val _chroniclePendingPlayIds = MutableStateFlow<Set<String>>(emptySet())
    val chroniclePendingPlayIds: StateFlow<Set<String>> = _chroniclePendingPlayIds.asStateFlow()
    private val chronicleJobs = mutableMapOf<String, Job>()
    private val chronicleInFlightSourceKeys = mutableMapOf<String, String>()
    private val chronicleGenerationLock = Any()

    private fun addCustomMoodIfNew(mood: String, presets: List<String>) {
        if (mood.isBlank() || presets.any { it.equals(mood, ignoreCase = true) }) return
        val current = _customMoods.value
        if (current.any { it.equals(mood, ignoreCase = true) }) return
        val updated = (current + mood).distinct()
        _customMoods.value = updated
        prefs.saveCustomMoods(updated)
    }

    fun deleteCustomMood(mood: String) {
        val updated = _customMoods.value.filter { !it.equals(mood, ignoreCase = true) }
        _customMoods.value = updated
        prefs.saveCustomMoods(updated)
        viewModelScope.launch {
            container.canonicalCollectionStore.removeMoodFromAllMemories(mood)
            _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
            val cached = container.canonicalCollectionStore.getBggPlaysCache()
            if (cached.isNotEmpty()) _bggPlays.value = cached
        }
    }

    // --- Play history (local) ---
    private val _playHistory = MutableStateFlow<List<LoggedPlay>>(emptyList())
    val playHistory: StateFlow<List<LoggedPlay>> = _playHistory.asStateFlow()
    private val _bggPlaysCacheAgeMinutes = MutableStateFlow(Long.MAX_VALUE)
    val bggPlaysCacheAgeMinutes: StateFlow<Long> = _bggPlaysCacheAgeMinutes.asStateFlow()

    fun loadPlayHistory() {
        viewModelScope.launch {
            _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
            backfillPlayerLastPlayed()
            loadHistoryThumbnailCache()
            fetchMissingHistoryThumbnails()
        }
    }

    private fun backfillPlayerLastPlayed() {
        val roster = _players.value.takeIf { it.isNotEmpty() } ?: return
        if (roster.none { it.lastPlayedAt == null }) return
        val history = _playHistory.value.takeIf { it.isNotEmpty() } ?: return

        val latestByName = mutableMapOf<String, Long>()
        history.forEach { play ->
            val ts = play.playedAt
                ?: play.date.toLocalDateOrNull()?.toEpochDay()?.times(86400000L)
                ?: return@forEach
            play.players.forEach { pr ->
                val key = pr.name.trim().lowercase()
                if (key.isNotBlank()) latestByName[key] = maxOf(latestByName[key] ?: 0L, ts)
            }
        }

        val updated = roster.map { player ->
            if (player.lastPlayedAt != null) return@map player
            val names = (listOf(player.displayName) + player.aliases).map { it.trim().lowercase() }
            val ts = names.mapNotNull { latestByName[it] }.maxOrNull() ?: return@map player
            player.copy(lastPlayedAt = ts)
        }
        if (updated != roster) {
            _players.value = updated
            prefs.savePlayers(updated)
        }
    }
    fun clearPlayHistory() {
        viewModelScope.launch {
            container.canonicalCollectionStore.clearLoggedPlays()
            prefs.clearLegacyLoggedPlayArtifacts()
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
    private val _bggDeleteError = MutableStateFlow<String?>(null)
    val bggDeleteError: StateFlow<String?> = _bggDeleteError.asStateFlow()
    fun clearBggDeleteError() { _bggDeleteError.value = null }
    private val _bggEditError = MutableStateFlow<String?>(null)
    val bggEditError: StateFlow<String?> = _bggEditError.asStateFlow()
    fun clearBggEditError() { _bggEditError.value = null }
    val historyPlays: StateFlow<List<LoggedPlay>> = combine(_playHistory, _bggPlays) { local, remote ->
        mergeHistorySources(local, remote)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun fetchBggPlays() {
        viewModelScope.launch {
            _bggPlaysLoading.value = true; _bggPlaysError.value = null
            cz.nicolsburg.boardflow.data.refreshBggPlayCache(prefs, container.canonicalCollectionStore, container.bggRepository)
                .onSuccess { rawPlays ->
                    val reconciled = reconcileSessionMetadataForRemote(rawPlays)
                    container.canonicalCollectionStore.saveBggPlaysCache(reconciled)
                    val withMemory = container.canonicalCollectionStore.getBggPlaysCache()
                    _bggPlays.value = mergeBggPlayLists(withMemory)
                    reconcilePendingLocalPlays(_bggPlays.value)
                    pruneLocalPlaysDeletedOnBgg(rawPlays)
                    _bggPlaysCacheAgeMinutes.value = container.canonicalCollectionStore.getBggPlaysCacheAgeMinutes()
                    fetchMissingHistoryThumbnails()
                }
                .onFailure { _bggPlaysError.value = it.message }
            _bggPlaysLoading.value = false
        }
    }

    fun loadCachedBggPlays() {
        viewModelScope.launch {
            val cached = container.canonicalCollectionStore.getBggPlaysCache()
            val reconciled = reconcileSessionMetadataForRemote(cached)
            if (reconciled != cached) {
                container.canonicalCollectionStore.saveBggPlaysCache(reconciled)
            }
            _bggPlaysCacheAgeMinutes.value = container.canonicalCollectionStore.getBggPlaysCacheAgeMinutes()
            if (reconciled.isNotEmpty()) {
                _bggPlays.value = mergeBggPlayLists(_bggPlays.value, reconciled)
                reconcilePendingLocalPlays(_bggPlays.value)
                fetchMissingHistoryThumbnails()
            }
        }
    }
    fun isBggPlaysCacheStale(): Boolean = _bggPlaysCacheAgeMinutes.value > 4 * 60
    fun bggPlaysCacheAgeLabel(): String {
        val minutes = _bggPlaysCacheAgeMinutes.value
        return when { minutes == Long.MAX_VALUE -> ""; minutes < 60 -> "updated ${minutes}m ago"; else -> "updated ${minutes / 60}h ago" }
    }

    // --- Thumbnail cache for non-collection games in play history ---
    private val _historyThumbnailCache = MutableStateFlow<Map<Int, String?>>(emptyMap())
    val historyThumbnailCache: StateFlow<Map<Int, String?>> = _historyThumbnailCache.asStateFlow()

    fun loadHistoryThumbnailCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = container.canonicalCollectionStore.getThumbnailCache()
            _historyThumbnailCache.value = cached.mapValues { it.value.second }
        }
    }

    fun fetchMissingHistoryThumbnails() {
        if (!container.isOnline()) return
        viewModelScope.launch(Dispatchers.IO) {
            val collectionIds = _allGames.value.map { it.id }.toSet()
            val cachedIds = container.canonicalCollectionStore.getThumbnailCacheGameIds().toSet()
            val missingIds = (_playHistory.value + _bggPlays.value)
                .map { it.gameId }
                .filter { it != 0 && it !in collectionIds && it !in cachedIds }
                .distinct()
                .map { it.toString() }
            if (missingIds.isEmpty()) return@launch
            try {
                val client = BggApiClient(BuildConfig.BGG_XML_API_TOKEN)
                val details = client.fetchThingDetails(missingIds)
                val toSave = details.entries
                    .mapNotNull { (idStr, detail) -> idStr.toIntOrNull()?.let { id -> id to (detail.name to detail.thumbnailUrl) } }
                    .toMap()
                container.canonicalCollectionStore.saveThumbnailCache(toSave)
                val updated = container.canonicalCollectionStore.getThumbnailCache()
                _historyThumbnailCache.value = updated.mapValues { it.value.second }
            } catch (e: Exception) {
                Log.w("AppViewModel", "fetchMissingHistoryThumbnails failed: ${e.message}")
            }
        }
    }

    private fun addOptimisticBggPlays(plays: List<LoggedPlay>) {
        if (plays.isEmpty()) return
        viewModelScope.launch {
            val reconciled = reconcileSessionMetadataForRemote(plays)
            _bggPlays.value = mergeBggPlayLists(reconciled, _bggPlays.value)
            container.canonicalCollectionStore.saveBggPlaysCache(_bggPlays.value)
            reconcilePendingLocalPlays(_bggPlays.value)
            _bggPlaysCacheAgeMinutes.value = 0L
        }
    }

    private suspend fun reconcileSessionMetadataForRemote(remote: List<LoggedPlay>): List<LoggedPlay> {
        if (remote.isEmpty()) return remote
        val localKnown = container.canonicalCollectionStore.getLoggedPlays()
            .filter { !it.sessionId.isNullOrBlank() || it.playedAt != null }
        val cachedKnown = container.canonicalCollectionStore.getBggPlaysCache()
            .filter { !it.sessionId.isNullOrBlank() || it.playedAt != null }
        val inMemoryKnown = _bggPlays.value.filter { !it.sessionId.isNullOrBlank() || it.playedAt != null }
        val known = (localKnown + cachedKnown + inMemoryKnown)
            .distinctBy { "${it.id}||${it.signatureKey()}" }

        if (known.isEmpty()) return remote

        val signatureMatches = known.groupBy { it.signatureKey() }
        val correlationMatches = known.groupBy { it.historyCorrelationKey() }

        fun preferredCandidate(candidates: List<LoggedPlay>): LoggedPlay? =
            candidates.maxWithOrNull(
                compareBy<LoggedPlay>(
                    { if (!it.sessionId.isNullOrBlank()) 1 else 0 },
                    { if (it.playedAt != null) 1 else 0 },
                    { if (it.postedToBgg) 1 else 0 },
                    { if (!it.id.isLikelyLocalUuid()) 1 else 0 }
                )
            )

        return remote.map { play ->
            val candidate = preferredCandidate(signatureMatches[play.signatureKey()].orEmpty())
                ?: preferredCandidate(correlationMatches[play.historyCorrelationKey()].orEmpty())
                ?: return@map play
            if (play.sessionId == candidate.sessionId && play.playedAt == candidate.playedAt) {
                play
            } else {
                play.copy(
                    sessionId = play.sessionId ?: candidate.sessionId,
                    playedAt = play.playedAt ?: candidate.playedAt
                )
            }
        }
    }

    private suspend fun reconcilePendingLocalPlays(remote: List<LoggedPlay>) {
        if (remote.isEmpty()) return
        val local = container.canonicalCollectionStore.getLoggedPlays()
        val pendingMatches = local.mapNotNull { play ->
            if (play.postedToBgg) return@mapNotNull null
            val remoteMatch = remote.firstOrNull {
                it.signatureKey() == play.signatureKey() ||
                    it.historyCorrelationKey() == play.historyCorrelationKey()
            } ?: return@mapNotNull null
            play to remoteMatch
        }
        if (pendingMatches.isEmpty()) return
        pendingMatches.forEach { (play, remoteMatch) ->
            promoteLoggedPlayToPosted(play, remoteMatch.id, play.players)
        }
        _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
    }

    // After a full BGG play refresh, remove local plays that carry a real BGG ID but no
    // longer exist on BGG — they were deleted externally (e.g. via the BGG website).
    private suspend fun pruneLocalPlaysDeletedOnBgg(freshBggPlays: List<LoggedPlay>) {
        val bggIds = freshBggPlays.mapTo(hashSetOf()) { it.id }
        val local = container.canonicalCollectionStore.getLoggedPlays()
        val pruned = local.filter { play ->
            play.postedToBgg && !play.id.isLikelyLocalUuid() && play.id !in bggIds
        }
        if (pruned.isEmpty()) return
        pruned.forEach { container.canonicalCollectionStore.deleteLoggedPlay(it.id) }
        _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
    }

    fun deleteBggPlay(play: LoggedPlay, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isOnline()) { onError("Go online to delete plays from BGG"); return }
        val creds = prefs.getCredentials() ?: run { onError("BGG credentials not set"); return }
        val username = prefs.bggUsername.trim()
        if (username.isBlank()) { onError("BGG username not set"); return }

        // Optimistic removal: remove from both in-memory state flows immediately so the UI
        // updates without waiting for the BGG network round-trip.
        val deletedPlay = _bggPlays.value.firstOrNull {
            it.id == play.id || it.signatureKey() == play.signatureKey()
        } ?: play
        _bggPlays.value = _bggPlays.value.filterNot {
            it.id == play.id || it.signatureKey() == play.signatureKey()
        }
        _playHistory.value = _playHistory.value.filter { local ->
            local.id != play.id && local.signatureKey() != deletedPlay.signatureKey()
        }
        clearActiveSessionIfMatchingPlay(deletedPlay)
        onSuccess()

        viewModelScope.launch {
            suspend fun restore() {
                deletedPlay?.let { _bggPlays.value = listOf(it) + _bggPlays.value }
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
            }

            container.bggRepository.login(creds).onFailure {
                restore()
                _bggDeleteError.value = it.message ?: "Login failed"
                return@launch
            }

            val remotePlayId = if (play.id.isLikelyLocalUuid()) {
                resolveBggPlayIdForEdit(play)
            } else {
                play.id
            }
            if (remotePlayId == null) {
                restore()
                _bggDeleteError.value =
                    "BoardFlow couldn't find the matching BGG play to delete. Refresh BGG history and try again."
                return@launch
            }

            val deleteResult = container.bggRepository.deletePlay(remotePlayId)

            // If delete "failed" with an unexpected-response error, the play may have already
            // been deleted on BGG externally. Verify by re-fetching and treat as success if gone.
            if (deleteResult.isFailure) {
                val mightBeAlreadyGone = deleteResult.exceptionOrNull()?.message
                    ?.contains("Unexpected BGG confirm-delete") == true
                if (mightBeAlreadyGone) {
                    container.bggRepository.getPlays(username)
                        .onSuccess { refreshed ->
                            if (!refreshed.any { it.id == remotePlayId }) {
                                val reconciled = reconcileSessionMetadataForRemote(refreshed)
                                container.canonicalCollectionStore.saveBggPlaysCache(reconciled)
                                _bggPlays.value = container.canonicalCollectionStore.getBggPlaysCache()
                                removeLocalCopyOfDeletedBggPlay(remotePlayId, deletedPlay)
                                pruneLocalPlaysDeletedOnBgg(refreshed)
                                _bggPlaysCacheAgeMinutes.value = 0L
                                return@launch
                            }
                        }
                }
                restore()
                _bggDeleteError.value = deleteResult.exceptionOrNull()?.message ?: "Failed to delete play"
                return@launch
            }

            // Delete accepted — refresh cache and clean up local DB copies.
            container.bggRepository.getPlays(username)
                .onSuccess { refreshed ->
                    val reconciled = reconcileSessionMetadataForRemote(refreshed)
                    container.canonicalCollectionStore.saveBggPlaysCache(reconciled)
                    _bggPlays.value = container.canonicalCollectionStore.getBggPlaysCache()
                    removeLocalCopyOfDeletedBggPlay(remotePlayId, deletedPlay)
                    pruneLocalPlaysDeletedOnBgg(refreshed)
                    _bggPlaysCacheAgeMinutes.value = 0L
                }
        }
    }

    fun savePlayMemory(
        play: LoggedPlay,
        memory: SessionMemory,
        presetMoods: List<String>,
        onMemoryUpdated: (SessionMemory) -> Unit = {}
    ) {
        viewModelScope.launch {
            val plan = container.chronicleService.plan(play, memory, play.memory)
            reconcileChronicleGeneration(play.id, if (plan.needsGeneration) plan.sourceKey else null)
            val plannedMemory = plan.memory
            persistMemoryUpdate(play, plannedMemory, presetMoods)
            onMemoryUpdated(plannedMemory)
            if (_chronicleEnabled.value && plan.needsGeneration) {
                if (tryReserveChronicleGeneration(play.id, plan.sourceKey)) {
                    launchChronicleGeneration(
                        play = play.copy(memory = plannedMemory),
                        plan = plan,
                        onMemoryUpdated = onMemoryUpdated
                    )
                }
            }
        }
    }

    fun ensureChronicleForPlay(
        play: LoggedPlay,
        onMemoryUpdated: (SessionMemory) -> Unit = {}
    ) {
        if (!_chronicleEnabled.value) return
        val memory = play.memory ?: return

        val plan = container.chronicleService.plan(play, memory, play.memory)
        if (!plan.needsGeneration || plan.sourceKey == null) return
        if (!tryReserveChronicleGeneration(play.id, plan.sourceKey)) return
        launchChronicleGeneration(play = play, plan = plan, onMemoryUpdated = onMemoryUpdated)
    }

    private suspend fun persistMemoryUpdate(
        play: LoggedPlay,
        memory: SessionMemory,
        presetMoods: List<String>
    ) {
        persistMemoryOverlay(play.id, memory)
        memory.moods.forEach { addCustomMoodIfNew(it, presetMoods) }
        if (memory.moods.isNotEmpty()) {
            prefs.recordMoodsUsed(memory.moods)
            _moodUsageOrder.value = prefs.getMoodUsageOrder()
        }

        val moodText = memory.moods.filter { it.isNotBlank() }.joinToString(", ")
        val newComments = buildMemoryComments(play.comments, moodText, memory.quote.trim())
        if (newComments != play.comments) {
            container.canonicalCollectionStore.updateLoggedPlay(play.id) { it.copy(comments = newComments) }
            if (play.postedToBgg) syncMoodToBgg(play, newComments)
        }

        _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
        val cached = container.canonicalCollectionStore.getBggPlaysCache()
        if (cached.isNotEmpty()) _bggPlays.value = cached
    }

    private suspend fun persistMemoryOverlay(playId: String, memory: SessionMemory) {
        container.canonicalCollectionStore.savePlayMemory(playId, memory)
    }

    private fun reconcileChronicleGeneration(playId: String, desiredSourceKey: String?) {
        synchronized(chronicleGenerationLock) {
            val currentSourceKey = chronicleInFlightSourceKeys[playId]
            if (desiredSourceKey == currentSourceKey) return
            chronicleJobs.remove(playId)?.cancel()
            chronicleInFlightSourceKeys.remove(playId)
            _chroniclePendingPlayIds.value = _chroniclePendingPlayIds.value - playId
        }
    }

    private fun tryReserveChronicleGeneration(playId: String, sourceKey: String?): Boolean {
        if (sourceKey == null) return false
        synchronized(chronicleGenerationLock) {
            val currentSourceKey = chronicleInFlightSourceKeys[playId]
            // Reject if this sourceKey is already reserved or in-flight, regardless of whether
            // the job coroutine has launched yet (prevents the race between ensureChronicle and savePlayMemory).
            if (currentSourceKey == sourceKey) return false
            chronicleInFlightSourceKeys[playId] = sourceKey
            return true
        }
    }

    private fun launchChronicleGeneration(
        play: LoggedPlay,
        plan: cz.nicolsburg.boardflow.data.chronicle.ChroniclePlan,
        onMemoryUpdated: (SessionMemory) -> Unit
    ) {
        chronicleJobs[play.id] = viewModelScope.launch {
            persistMemoryOverlay(play.id, plan.memory)
            onMemoryUpdated(plan.memory)
            _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
            val cachedBeforeCompose = container.canonicalCollectionStore.getBggPlaysCache()
            if (cachedBeforeCompose.isNotEmpty()) _bggPlays.value = cachedBeforeCompose

            _chroniclePendingPlayIds.value = _chroniclePendingPlayIds.value + play.id
            val aiConfig = if (isOnline() && prefs.hasGeminiKey()) {
                ChronicleAiConfig(
                    apiKey = prefs.geminiApiKey,
                    modelName = prefs.geminiModelEndpoint,
                    availableModels = prefs.getAvailableModels(),
                    availableApiKeys = prefs.getGeminiExtraApiKeys(),
                    onModelExhausted = { exhaustedModel -> prefs.removeAvailableModel(exhaustedModel) }
                )
            } else {
                null
            }
            val composedMemory = runCatching {
                container.chronicleService.compose(play, plan, aiConfig)
            }.getOrElse { plan.memory }
            persistMemoryOverlay(play.id, composedMemory)
            onMemoryUpdated(composedMemory)
            _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
            val cached = container.canonicalCollectionStore.getBggPlaysCache()
            if (cached.isNotEmpty()) _bggPlays.value = cached
        }.also { job ->
            job.invokeOnCompletion {
                _chroniclePendingPlayIds.value = _chroniclePendingPlayIds.value - play.id
                synchronized(chronicleGenerationLock) {
                    chronicleJobs.remove(play.id)
                    chronicleInFlightSourceKeys.remove(play.id)
                }
            }
        }
    }

    private fun buildMemoryComments(existingComments: String, moodText: String, quoteText: String): String {
        val stripped = existingComments.trimMemorySuffix()
        val lines = buildList {
            if (moodText.isNotBlank()) add("\$\$mood: $moodText")
            if (quoteText.isNotBlank()) add("\$\$quote: $quoteText")
        }
        if (lines.isEmpty()) return stripped
        val block = lines.joinToString("\n")
        return if (stripped.isBlank()) block else "$stripped\n\n$block"
    }

    private suspend fun syncMoodToBgg(play: LoggedPlay, newComments: String) {
        runCatching {
            if (!isOnline()) return
            val creds = prefs.getCredentials() ?: return
            container.bggRepository.login(creds).getOrThrow()
            val bggPlayId = resolveBggPlayIdForEdit(play) ?: return
            container.bggRepository.logPlay(
                gameId = play.gameId,
                date = LocalDate.parse(play.date),
                players = play.players,
                playerBggUsernames = buildBggUsernameMap(play.players),
                durationMinutes = play.durationMinutes,
                location = play.location,
                comments = newComments,
                quantity = play.quantity,
                incomplete = play.incomplete,
                nowInStats = play.nowInStats,
                playId = bggPlayId
            ).getOrThrow()
        }
    }

    fun deleteLocalPlay(playId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val deletedPlay = _playHistory.value.firstOrNull { it.id == playId }
                container.canonicalCollectionStore.deleteLoggedPlay(playId)
                _playHistory.value = _playHistory.value.filter { it.id != playId }
                clearActiveSessionIfMatchingPlay(deletedPlay)
            }.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Failed to delete local play") }
        }
    }

    private fun clearActiveSessionIfMatchingPlay(play: LoggedPlay?) {
        val activeSessionId = _sessionContext.value?.sessionId?.takeIf { it.isNotBlank() } ?: return
        val deletedSessionId = play?.sessionId?.takeIf { it.isNotBlank() } ?: return
        if (activeSessionId == deletedSessionId) {
            clearSession()
        }
    }

    private suspend fun removeLocalCopyOfDeletedBggPlay(playId: String, deletedPlay: LoggedPlay?) {
        val deletedSignature = deletedPlay?.signatureKey()
        val toRemove = _playHistory.value.filter { localPlay ->
            localPlay.id == playId || (deletedSignature != null && localPlay.signatureKey() == deletedSignature)
        }
        toRemove.forEach { container.canonicalCollectionStore.deleteLoggedPlay(it.id) }
        if (toRemove.isNotEmpty()) {
            val removedIds = toRemove.mapTo(hashSetOf()) { it.id }
            _playHistory.value = _playHistory.value.filter { it.id !in removedIds }
        }
    }

    // --- Post to BGG ---
    private val _postLoading = MutableStateFlow(false)
    val postLoading: StateFlow<Boolean> = _postLoading.asStateFlow()
    private val _postResult = MutableStateFlow<String?>(null)
    val postResult: StateFlow<String?> = _postResult.asStateFlow()
    private val _pendingImportedPlay = MutableStateFlow<LoggedPlay?>(null)
    val pendingImportedPlay: StateFlow<LoggedPlay?> = _pendingImportedPlay.asStateFlow()

    fun postPlay(date: LocalDate, durationMinutes: Int, location: String, comments: String, quantity: Int = 1, incomplete: Boolean = false, nowInStats: Boolean = true, onSuccess: (LoggedPlay, List<ChallengeProgress>) -> Unit, onError: (String) -> Unit) {
        val game = selectedGame ?: run { onError("No game selected"); return }
        val normalizedPlayers = normalizePlayersForPosting(_editablePlayers.value)
        if (!isOnline()) {
            val playersSnapshot = normalizedPlayers
            playersSnapshot.forEach { recordPlayerName(it.name) }
            viewModelScope.launch {
                val playedAt = System.currentTimeMillis()
                val session = resolveSessionForNewPlay(date, location, playedAt)
                val mainPlay = LoggedPlay(
                    id = UUID.randomUUID().toString(),
                    gameId = game.id,
                    gameName = game.name,
                    date = date.toString(),
                    playedAt = playedAt,
                    sessionId = session.id,
                    players = playersSnapshot,
                    durationMinutes = durationMinutes,
                    location = location,
                    postedToBgg = false,
                    comments = comments,
                    quantity = quantity,
                    incomplete = incomplete,
                    nowInStats = nowInStats
                )
                container.canonicalCollectionStore.saveLoggedPlay(mainPlay)
                val extras = _additionalGames.value; _additionalGames.value = emptyList()
                extras.forEach { extra ->
                    container.canonicalCollectionStore.saveLoggedPlay(
                        LoggedPlay(
                            id = UUID.randomUUID().toString(),
                            gameId = extra.id,
                            gameName = extra.name,
                            date = date.toString(),
                            playedAt = playedAt,
                            sessionId = session.id,
                            players = playersSnapshot,
                            durationMinutes = durationMinutes,
                            location = location,
                            postedToBgg = false,
                            comments = comments,
                            quantity = quantity,
                            incomplete = incomplete,
                            nowInStats = nowInStats
                        )
                    )
                }
                saveSession(session.id, session.startedAt, game, playersSnapshot, location, playedAt, session.title)
                prefs.addRecentGame(game)
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                val challengeProgressAfter = getChallengeProgressList()
                savePlayerHintsFromCurrentPlay()
                stampPlayerLastPlayed(playersSnapshot, playedAt)
                cancelBackgroundRetry()
                _logPlayHasUnsavedChanges.value = false
                onSuccess(mainPlay, challengeProgressAfter)
            }
            return
        }
        val creds = prefs.getCredentials() ?: run { onError("BGG credentials not set"); return }
        viewModelScope.launch {
            _postLoading.value = true
            container.bggRepository.login(creds).onFailure { _postLoading.value = false; onError(it.message ?: "Login failed"); return@launch }
            val playerBggUsernames = buildBggUsernameMap(normalizedPlayers)
            container.bggRepository.logPlay(gameId = game.id, date = date, players = normalizedPlayers, playerBggUsernames = playerBggUsernames, durationMinutes = durationMinutes, location = location, comments = comments, quantity = quantity, incomplete = incomplete, nowInStats = nowInStats)
                .onSuccess { savedPlayId ->
                    val playedAt = System.currentTimeMillis()
                    val session = resolveSessionForNewPlay(date, location, playedAt)
                    normalizedPlayers.forEach { recordPlayerName(it.name) }
                    val postedPlays = mutableListOf<LoggedPlay>()
                    val locallySavedExtras = mutableListOf<LoggedPlay>()
                    val mainPlay = LoggedPlay(
                        id = savedPlayId ?: UUID.randomUUID().toString(),
                        gameId = game.id,
                        gameName = game.name,
                        date = date.toString(),
                        playedAt = playedAt,
                        sessionId = session.id,
                        players = normalizedPlayers,
                        durationMinutes = durationMinutes,
                        location = location,
                        postedToBgg = true,
                        comments = comments,
                        quantity = quantity,
                        incomplete = incomplete,
                        nowInStats = nowInStats
                    )
                    container.canonicalCollectionStore.saveLoggedPlay(mainPlay)
                    postedPlays += mainPlay
                    val extras = _additionalGames.value
                    extras.forEach { extra ->
                        container.bggRepository.logPlay(gameId = extra.id, date = date, players = normalizedPlayers, playerBggUsernames = playerBggUsernames, durationMinutes = durationMinutes, location = location, comments = comments, quantity = quantity, incomplete = incomplete, nowInStats = nowInStats)
                            .onSuccess { extraPlayId ->
                                val extraPlay = LoggedPlay(
                                    id = extraPlayId ?: UUID.randomUUID().toString(),
                                    gameId = extra.id,
                                    gameName = extra.name,
                                    date = date.toString(),
                                    playedAt = playedAt,
                                    sessionId = session.id,
                                    players = normalizedPlayers,
                                    durationMinutes = durationMinutes,
                                    location = location,
                                    postedToBgg = true,
                                    comments = comments,
                                    quantity = quantity,
                                    incomplete = incomplete,
                                    nowInStats = nowInStats
                                )
                                container.canonicalCollectionStore.saveLoggedPlay(extraPlay)
                                postedPlays += extraPlay
                            }
                            .onFailure {
                                val localExtraPlay = LoggedPlay(
                                    id = UUID.randomUUID().toString(),
                                    gameId = extra.id,
                                    gameName = extra.name,
                                    date = date.toString(),
                                    playedAt = playedAt,
                                    sessionId = session.id,
                                    players = normalizedPlayers,
                                    durationMinutes = durationMinutes,
                                    location = location,
                                    postedToBgg = false,
                                    comments = comments,
                                    quantity = quantity,
                                    incomplete = incomplete,
                                    nowInStats = nowInStats
                                )
                                container.canonicalCollectionStore.saveLoggedPlay(localExtraPlay)
                                locallySavedExtras += localExtraPlay
                            }
                    }
                    _additionalGames.value = emptyList()
                    saveSession(session.id, session.startedAt, game, normalizedPlayers, location, playedAt, session.title)
                    prefs.addRecentGame(game)
                    _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                    val challengeProgressAfter = getChallengeProgressList()
                    addOptimisticBggPlays(postedPlays)
                    if (locallySavedExtras.isNotEmpty()) {
                        _postResult.value = "Logged main play. Saved ${locallySavedExtras.size} extra game(s) locally for later BGG sync."
                    }
                    savePlayerHintsFromCurrentPlay()
                    stampPlayerLastPlayed(normalizedPlayers, playedAt)
                    cancelBackgroundRetry()
                    _logPlayHasUnsavedChanges.value = false
                    _postLoading.value = false
                    onSuccess(mainPlay, challengeProgressAfter)
                }.onFailure { _postLoading.value = false; onError(it.message ?: "Failed to log play") }
        }
    }

    private suspend fun resolveSessionForNewPlay(date: LocalDate, location: String, playedAt: Long): PlaySession {
        val activeContext = _sessionContext.value?.takeIf { it.isActive() }
        val sessionId = activeContext?.sessionId ?: UUID.randomUUID().toString()
        val startedAt = activeContext?.startedAt ?: playedAt
        val session = PlaySession(
            id = sessionId,
            startedAt = startedAt,
            endedAt = playedAt,
            sessionDate = date.toString(),
            location = location,
            title = activeContext?.title.orEmpty()
        )
        container.canonicalCollectionStore.savePlaySession(session)
        return session
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
        val normalizedPlayers = normalizePlayersForPosting(players)
        val resolvedDate = date.toFlexibleLocalDateOrNull()
            ?: play.date.toFlexibleLocalDateOrNull()
            ?: LocalDate.now()
        val normalizedDate = resolvedDate.toString()
        val memory = play.memory
        val moodText = memory?.moods?.filter { it.isNotBlank() }?.joinToString(", ") ?: ""
        val quoteText = memory?.quote?.trim() ?: ""
        val finalComments = buildMemoryComments(comments, moodText, quoteText)
        val updatedPlay = play.copy(
            date = normalizedDate,
            durationMinutes = durationMinutes,
            location = location,
            comments = finalComments,
            players = normalizedPlayers
        )

        // Optimistic in-memory update so UI reflects changes immediately.
        _bggPlays.value = _bggPlays.value.map {
            if (it.id == play.id || it.signatureKey() == play.signatureKey()) updatedPlay else it
        }
        _playHistory.value = _playHistory.value.map { if (it.id == play.id) updatedPlay else it }

        viewModelScope.launch {
            // Persist to local DB.
            runCatching {
                container.canonicalCollectionStore.updateLoggedPlay(play.id) {
                    it.copy(
                        date = normalizedDate,
                        durationMinutes = durationMinutes,
                        location = location,
                        comments = finalComments,
                        players = normalizedPlayers
                    )
                }
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                normalizedPlayers.forEach { recordPlayerName(it.name) }
            }.onFailure { e ->
                // Revert optimistic update on local DB failure.
                _bggPlays.value = _bggPlays.value.map { if (it.id == updatedPlay.id) play else it }
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                onError(e.message ?: "Failed to update play")
                return@launch
            }

            onSuccess()

            // BGG sync runs in the background — the dialog is already closed.
            if (!play.postedToBgg) return@launch
            if (!isOnline()) {
                _bggEditError.value = "Play updated locally. Connect to BGG to sync."
                return@launch
            }
            val creds = prefs.getCredentials() ?: run {
                _bggEditError.value = "BGG credentials not set. Local changes saved."
                return@launch
            }
            runCatching {
                container.bggRepository.login(creds).getOrThrow()
                val bggPlayId = resolveBggPlayIdForEdit(play)
                if (bggPlayId == null) {
                    _bggEditError.value =
                        "Play updated locally, but BoardFlow couldn't find the matching BGG play to sync. Refresh BGG history and try again."
                    return@launch
                }
                val savedPlayId = container.bggRepository.logPlay(
                    gameId = play.gameId,
                    date = resolvedDate,
                    players = normalizedPlayers,
                    playerBggUsernames = buildBggUsernameMap(normalizedPlayers),
                    durationMinutes = durationMinutes,
                    location = location,
                    comments = finalComments,
                    quantity = play.quantity,
                    incomplete = play.incomplete,
                    nowInStats = play.nowInStats,
                    playId = bggPlayId
                ).getOrThrow()
                updateCachedBggPlay(play, updatedPlay.copy(id = savedPlayId ?: bggPlayId, postedToBgg = true))
            }.onFailure { e ->
                _bggEditError.value = e.message ?: "BGG sync failed. Local changes saved."
            }
        }
    }

    private suspend fun resolveBggPlayIdForEdit(play: LoggedPlay): String? {
        if (!play.id.isLikelyLocalUuid()) return play.id

        val originalSignature = play.signatureKey()
        val originalCorrelation = play.historyCorrelationKey()
        val cachedMatch = (_bggPlays.value + container.canonicalCollectionStore.getBggPlaysCache())
            .firstOrNull {
                !it.id.isLikelyLocalUuid() && (
                    it.signatureKey() == originalSignature ||
                        it.historyCorrelationKey() == originalCorrelation
                    )
            }
        if (cachedMatch != null) return cachedMatch.id

        val username = prefs.bggUsername.trim()
        if (username.isBlank()) return null

        val refreshed = container.bggRepository.getPlays(username).getOrNull() ?: return null
        if (refreshed.isNotEmpty()) {
            val reconciled = reconcileSessionMetadataForRemote(refreshed)
            container.canonicalCollectionStore.saveBggPlaysCache(reconciled)
            val withMemory = container.canonicalCollectionStore.getBggPlaysCache()
            _bggPlays.value = mergeBggPlayLists(withMemory, _bggPlays.value)
            _bggPlaysCacheAgeMinutes.value = 0L
        }

        return refreshed.firstOrNull {
            !it.id.isLikelyLocalUuid() && (
                it.signatureKey() == originalSignature ||
                    it.historyCorrelationKey() == originalCorrelation
                )
        }?.id
    }

    private suspend fun updateCachedBggPlay(originalPlay: LoggedPlay, updatedPlay: LoggedPlay) {
        val originalSignature = originalPlay.signatureKey()
        val cached = container.canonicalCollectionStore.getBggPlaysCache()
        val remoteWithoutOriginal = _bggPlays.value.filterNot {
            it.id == originalPlay.id || it.signatureKey() == originalSignature
        }
        val cachedWithoutOriginal = cached.filterNot {
            it.id == originalPlay.id || it.signatureKey() == originalSignature
        }
        val merged = mergeBggPlayLists(listOf(updatedPlay), remoteWithoutOriginal, cachedWithoutOriginal)
        val reconciled = reconcileSessionMetadataForRemote(merged)
        _bggPlays.value = reconciled
        container.canonicalCollectionStore.saveBggPlaysCache(reconciled)
        _bggPlaysCacheAgeMinutes.value = 0L
    }

    private suspend fun promoteLoggedPlayToPosted(
        originalPlay: LoggedPlay,
        remotePlayId: String?,
        normalizedPlayers: List<PlayerResult>
    ) {
        val updatedPlay = originalPlay.copy(
            id = remotePlayId ?: originalPlay.id,
            players = normalizedPlayers,
            postedToBgg = true
        )
        container.canonicalCollectionStore.saveLoggedPlay(updatedPlay)
        if (updatedPlay.id != originalPlay.id) {
            container.canonicalCollectionStore.deleteLoggedPlay(originalPlay.id)
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
    private val _syncingUnpostedPlays = MutableStateFlow(false)
    val syncingUnpostedPlays: StateFlow<Boolean> = _syncingUnpostedPlays.asStateFlow()

    fun postSinglePlay(playId: String) {
        if (!isOnline()) return
        val creds = prefs.getCredentials() ?: return
        viewModelScope.launch {
            val play = container.canonicalCollectionStore.getLoggedPlays().firstOrNull { it.id == playId } ?: run {
                _postingPlayId.value = null
                return@launch
            }
            _postingPlayId.value = playId
            container.bggRepository.login(creds).onFailure { _postingPlayId.value = null; return@launch }
            val normalizedPlayers = normalizePlayersForPosting(play.players)
            container.bggRepository.logPlay(gameId = play.gameId, date = LocalDate.parse(play.date), players = normalizedPlayers, playerBggUsernames = buildBggUsernameMap(normalizedPlayers), durationMinutes = play.durationMinutes, location = play.location, comments = play.comments, quantity = play.quantity, incomplete = play.incomplete, nowInStats = play.nowInStats)
                .onSuccess { savedPlayId ->
                    promoteLoggedPlayToPosted(play, savedPlayId, normalizedPlayers)
                    addOptimisticBggPlays(
                        listOf(
                            play.copy(
                                id = savedPlayId ?: play.id,
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
            _syncingUnpostedPlays.value = true
            val unposted = container.canonicalCollectionStore.getLoggedPlays().filter { !it.postedToBgg }
            try {
                if (unposted.isEmpty()) return@launch
                Log.i(TAG_AUTO_SWITCH, "Syncing ${unposted.size} unposted play(s) to BGG")
                container.bggRepository.login(creds).onFailure { return@launch }
                val postedPlays = mutableListOf<LoggedPlay>()
                for (play in unposted) {
                    val normalizedPlayers = normalizePlayersForPosting(play.players)
                    container.bggRepository.logPlay(gameId = play.gameId, date = LocalDate.parse(play.date), players = normalizedPlayers, playerBggUsernames = buildBggUsernameMap(normalizedPlayers), durationMinutes = play.durationMinutes, location = play.location, comments = play.comments, quantity = play.quantity, incomplete = play.incomplete, nowInStats = play.nowInStats)
                        .onSuccess { savedPlayId ->
                            promoteLoggedPlayToPosted(play, savedPlayId, normalizedPlayers)
                            postedPlays += play.copy(id = savedPlayId ?: play.id, players = normalizedPlayers, postedToBgg = true)
                        }
                }
                Log.i(TAG_AUTO_SWITCH, "Sync complete: ${postedPlays.size}/${unposted.size} play(s) posted to BGG")
                addOptimisticBggPlays(postedPlays)
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
            } finally {
                _syncingUnpostedPlays.value = false
            }
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
                updateFromCollection(importedCollection)
            }
            container.canonicalCollectionStore.replaceLoggedPlays(imported.loggedPlays)
            _playHistory.value = imported.loggedPlays
            container.canonicalCollectionStore.saveBggPlaysCache(imported.cachedBggPlays)
            container.canonicalCollectionStore.replaceAllPlayMemories(imported.loggedPlays + imported.cachedBggPlays)
            _bggPlays.value = mergeBggPlayLists(imported.cachedBggPlays)
            _bggPlaysCacheAgeMinutes.value = container.canonicalCollectionStore.getBggPlaysCacheAgeMinutes()

            try {
                _appTheme.value = AppTheme.valueOf(prefs.appTheme)
            } catch (_: Exception) {
                _appTheme.value = AppTheme.DARK
            }
            try {
                _statsPlayScope.value = StatsPlayScope.valueOf(prefs.statsPlayScope)
            } catch (_: Exception) {
                _statsPlayScope.value = StatsPlayScope.ALL_PLAYS
            }
            _recommendationsEnabled.value = prefs.recommendationsEnabled
            _chronicleEnabled.value = prefs.chronicleEnabled
            try {
                _sleevePreferredManufacturer.value = SleeveManufacturer.valueOf(prefs.sleevePreferredManufacturer)
            } catch (_: Exception) {
                _sleevePreferredManufacturer.value = SleeveManufacturer.AUTO
            }
            _moodUsageOrder.value = prefs.getMoodUsageOrder()
            loadPlayers()
            loadRecentGames()
            loadChallenges()
        }
    }

    fun setExtractedPlayManual() {
        _extractedPlay.value = ExtractedPlay(players = emptyList(), rawText = "Manual entry", date = java.time.LocalDate.now().toString())
    }

    fun clearExtractedPlay() {
        cancelBackgroundRetry()
        _extractedPlay.value = null
        _editablePlayers.value = emptyList()
        _additionalGames.value = emptyList()
        _scanError.value = null
        _gameCandidates.value = emptyList()
        _scanRecognitionResult.value = null
        _originalScannedNames = emptyList()
        clearQuickScanCorrectionMode("log play flow cleared")
    }

    fun setPendingImportedPlay(play: LoggedPlay) {
        _pendingImportedPlay.value = play
    }

    fun clearPendingImportedPlay() {
        _pendingImportedPlay.value = null
    }

    fun saveImportedPlay(play: LoggedPlay, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val normalizedPlayers = normalizePlayersForPosting(play.players)
                normalizedPlayers.forEach { recordPlayerName(it.name) }
                container.canonicalCollectionStore.saveLoggedPlay(
                    play.copy(
                        id = UUID.randomUUID().toString(),
                        players = normalizedPlayers,
                        postedToBgg = false
                    )
                )
                _playHistory.value = container.canonicalCollectionStore.getLoggedPlays()
                _pendingImportedPlay.value = null
            }.onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Failed to save imported play") }
        }
    }

    fun clearLogPlayFlow() {
        selectedGame = null
        _gameRelations.value = null
        _logPlayHasUnsavedChanges.value = false
        _logPlayPrefill = null
        clearExtractedPlay()
    }

    fun setLogPlayHasUnsavedChanges(hasChanges: Boolean) {
        _logPlayHasUnsavedChanges.value = hasChanges
    }

    // --- Session context ---
    private val _sessionContext = MutableStateFlow<SessionContext?>(null)
    val sessionContext: StateFlow<SessionContext?> = _sessionContext.asStateFlow()

    private val _sessionBannerDismissed = MutableStateFlow(false)
    val sessionBannerVisible: StateFlow<Boolean> = combine(_sessionContext, _sessionBannerDismissed) { ctx, dismissed ->
        ctx != null && ctx.isActive() && !dismissed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Consumed by selectGame to pre-populate players when user changes game mid-session.
    private var _changeGameSession: SessionContext? = null
    private val _changeGameSessionActive = MutableStateFlow(false)
    val changeGameSessionActive: StateFlow<Boolean> = _changeGameSessionActive.asStateFlow()

    // Consumed once by LogPlayScreen on first composition for location/duration prefill.
    private var _logPlayPrefill: LogPlayPrefill? = null

    // Captured before postPlay so detectRecord can compare against prior history.
    private var _historySnapshot: List<LoggedPlay>? = null

    fun loadSessionContext() {
        val ctx = prefs.loadSessionContext()
        _sessionContext.value = if (ctx != null && ctx.isActive()) ctx else null
    }

    fun saveSession(sessionId: String, sessionStartedAt: Long, game: BggGame, players: List<PlayerResult>, location: String, lastPlayTimestamp: Long = System.currentTimeMillis(), title: String = "") {
        val ctx = SessionContext(
            sessionId          = sessionId,
            gameId            = game.id,
            gameName          = game.name,
            players           = players,
            location          = location,
            title             = title,
            startedAt         = sessionStartedAt,
            lastPlayTimestamp = lastPlayTimestamp
        )
        _sessionContext.value = ctx
        _sessionBannerDismissed.value = false
        prefs.saveSessionContext(ctx)
    }

    suspend fun getSessionTitle(sessionId: String?): String? {
        if (sessionId.isNullOrBlank()) return null
        return container.canonicalCollectionStore.getPlaySession(sessionId)?.title?.trim()?.ifBlank { null }
    }

    fun renameSession(sessionId: String, newTitle: String, onSuccess: (String) -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val session = container.canonicalCollectionStore.getPlaySession(sessionId)
                    ?: throw IllegalStateException("Session not found")
                val trimmed = newTitle.trim()
                container.canonicalCollectionStore.savePlaySession(session.copy(title = trimmed))
                if (_sessionContext.value?.sessionId == sessionId) {
                    _sessionContext.value = _sessionContext.value?.copy(title = trimmed)
                    _sessionContext.value?.let { prefs.saveSessionContext(it) }
                }
                trimmed
            }.onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Failed to rename session") }
        }
    }

    fun clearSession() {
        _sessionContext.value = null
        _sessionBannerDismissed.value = false
        _changeGameSession = null
        _changeGameSessionActive.value = false
        _pendingPlayers.value = emptyList()
        prefs.clearSessionContext()
    }

    fun dismissSessionBannerForSession() {
        _sessionBannerDismissed.value = true
    }

    fun takePrefill(): LogPlayPrefill? {
        val result = _logPlayPrefill
        _logPlayPrefill = null
        return result
    }

    fun setupPlayAgain(ctx: SessionContext) {
        val game = BggGame(ctx.gameId, ctx.gameName, null, null)
        selectedGame = game
        _editablePlayers.value = ctx.players.map { it.copy(score = "0", isWinner = false) }
        _extractedPlay.value = null
        _additionalGames.value = emptyList()
        _gameRelations.value = findRelatedGames(game, _allGames.value)
        _logPlayPrefill = LogPlayPrefill(location = ctx.location)
        _logPlayHasUnsavedChanges.value = false
    }

    fun setupChangeGameSession(ctx: SessionContext) {
        _changeGameSession = ctx
        _changeGameSessionActive.value = true
    }

    fun setupPlayAgainFromSession(plays: List<LoggedPlay>) {
        val first = plays.firstOrNull() ?: return
        val primaryGame = BggGame(first.gameId, first.gameName, null, null)
        selectedGame = primaryGame
        _editablePlayers.value = first.players.map { it.copy(score = "0", isWinner = false) }
        _extractedPlay.value = null
        _additionalGames.value = plays.drop(1)
            .distinctBy { it.gameId }
            .map { BggGame(it.gameId, it.gameName, null, null) }
        _gameRelations.value = findRelatedGames(primaryGame, _allGames.value)
        _logPlayPrefill = LogPlayPrefill(location = first.location)
        _logPlayHasUnsavedChanges.value = false
    }

    fun setupPlayAgainFromPlay(play: LoggedPlay) = setupPlayAgainFromSession(listOf(play))

    fun setupLogPlayById(gameId: Int, gameName: String, thumbnailUrl: String?) {
        val game = BggGame(gameId, gameName, null, thumbnailUrl)
        selectedGame = game
        _editablePlayers.value = emptyList()
        _extractedPlay.value = null
        _additionalGames.value = emptyList()
        _gameRelations.value = findRelatedGames(game, _allGames.value)
        _logPlayPrefill = null
        _logPlayHasUnsavedChanges.value = false
    }

    // ── Pending cross-tab navigation ─────────────────────────────────────────
    data class PendingHistoryNavigation(
        val gameId: Int? = null,
        val playerFilter: String? = null,
        val showPlayersTab: Boolean = false
    )

    private val _pendingHistoryNavigation = MutableStateFlow<PendingHistoryNavigation?>(null)
    val pendingHistoryNavigation: StateFlow<PendingHistoryNavigation?> = _pendingHistoryNavigation.asStateFlow()

    fun setPendingHistoryFilter(gameId: Int? = null, playerFilter: String? = null, showPlayersTab: Boolean = false) {
        _pendingHistoryNavigation.value = PendingHistoryNavigation(gameId, playerFilter, showPlayersTab)
    }
    fun consumePendingHistoryFilter() { _pendingHistoryNavigation.value = null }

    fun addPlayerFromRoster(player: Player) {
        _editablePlayers.value = _editablePlayers.value + PlayerResult(player.displayName, "0", false)
    }

    fun captureHistorySnapshot() {
        _historySnapshot = historyPlays.value.toList()
    }

    fun detectRecord(gameId: Int, gameName: String, savedPlayers: List<PlayerResult>): RecordMoment? {
        val snapshot = _historySnapshot ?: return null
        val gamePlays = snapshot.filter { it.gameId == gameId }

        // Priority 1: first win (player had 0 wins in snapshot → this is their first)
        for (pr in savedPlayers) {
            if (!pr.isWinner) continue
            val lower = pr.name.trim().lowercase()
            val hadPriorWin = gamePlays.any { play ->
                play.players.any { it.name.trim().lowercase() == lower && it.isWinner }
            }
            if (!hadPriorWin) return RecordMoment.FirstWin(pr.name.trim(), gameName)
        }

        // Priority 2: new high score (score > prior max in snapshot)
        for (pr in savedPlayers) {
            val newScore = pr.score.trim().toDoubleOrNull() ?: continue
            if (newScore <= 0) continue
            val lower = pr.name.trim().lowercase()
            val priorMax = gamePlays
                .flatMap { it.players }
                .filter { it.name.trim().lowercase() == lower }
                .mapNotNull { it.score.trim().toDoubleOrNull() }
                .maxOrNull()
            if (priorMax == null || newScore > priorMax) {
                return RecordMoment.NewHighScore(pr.name.trim(), gameName)
            }
        }

        // Priority 3: win streak of 2+ (prior consecutive wins + current win)
        for (pr in savedPlayers) {
            if (!pr.isWinner) continue
            val lower = pr.name.trim().lowercase()
            val playerGamingHistory = gamePlays
                .filter { play -> play.players.any { it.name.trim().lowercase() == lower } }
                .sortedByDescending { it.date }
            var priorStreak = 0
            for (play in playerGamingHistory) {
                val p = play.players.firstOrNull { it.name.trim().lowercase() == lower }
                if (p?.isWinner == true) priorStreak++ else break
            }
            val totalStreak = priorStreak + 1  // +1 for the current win
            if (totalStreak >= 2) return RecordMoment.WinStreak(pr.name.trim(), totalStreak)
        }

        return null
    }

    fun getRecentPlayers(excludeNames: Set<String>): List<Player> {
        val lowerExclude = excludeNames.map { it.lowercase().trim() }.toSet()
        val seen = hashSetOf<String>()
        return historyPlays.value
            .take(20)
            .flatMap { it.players }
            .mapNotNull { pr ->
                val trimmed = pr.name.trim()
                if (trimmed.isBlank() || trimmed.lowercase() in lowerExclude) return@mapNotNull null
                resolveRosterPlayer(trimmed)
            }
            .filter { seen.add(it.id) }
            .take(4)
    }

    fun getFrequentPlayers(gameId: Int, excludeNames: Set<String>): List<Player> {
        val gamePlays = (historyPlays.value).filter { it.gameId == gameId }
        if (gamePlays.isEmpty()) return emptyList()

        val counts = mutableMapOf<String, Int>()
        gamePlays.forEach { play ->
            play.players.forEach { pr ->
                val trimmed = pr.name.trim()
                if (trimmed.isNotBlank()) counts[trimmed] = (counts[trimmed] ?: 0) + 1
            }
        }
        val lowerExclude = excludeNames.map { it.lowercase().trim() }.toSet()
        return counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (name, _) ->
                if (name.lowercase().trim() in lowerExclude) return@mapNotNull null
                resolveRosterPlayer(name)
            }
            .distinctBy { it.id }
            .take(5)
    }

    fun getLogPlayRecommendations(): List<RecommendationLane> {
        val owned = _collectionItems.value.filter { it.isOwned }
        if (owned.isEmpty()) return emptyList()

        val pendingNames = _pendingPlayers.value
        val effectivePlayers: List<String> = when {
            pendingNames.isNotEmpty() -> pendingNames
            else -> _sessionContext.value?.players?.map { it.name }.orEmpty()
        }
        val playerCount = effectivePlayers.size.takeIf { it > 0 }
        val groupNames = effectivePlayers.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val history = historyPlays.value
        val gameById = _allGames.value.associateBy { it.id }
        val lanes = buildList {
            buildBestForTonightLane(owned, gameById, playerCount)?.let(::add)
            buildGroupFavoritesLane(owned, gameById, groupNames, playerCount)?.let(::add)
            buildNeglectedFavoritesLane(owned, gameById, history, playerCount)?.let(::add)
            buildQuickOptionLane(owned, gameById, playerCount)?.let(::add)
        }
        return lanes.filter { it.picks.isNotEmpty() }
    }

    fun getPostSaveRecommendations(anchorPlay: LoggedPlay, limit: Int = 3): List<RecommendationPick> {
        val owned = _collectionItems.value.filter { it.isOwned && it.objectId.toIntOrNull() != anchorPlay.gameId }
        if (owned.isEmpty()) return emptyList()
        val gameById = _allGames.value.associateBy { it.id }
        val groupNames = anchorPlay.players.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val playerCount = anchorPlay.players.size.takeIf { it > 0 }
        val history = historyPlays.value

        val exactGroup = scoreGamesForGroup(owned, groupNames, playerCount)
            .filter { it.score > 0.85 }
            .sortedByDescending { it.score }
        val rematch = owned.mapNotNull { item ->
            val id = item.objectId.toIntOrNull() ?: return@mapNotNull null
            if (!fitsPlayerCount(item, playerCount)) return@mapNotNull null
            val rematchScore = scoreRematchCandidate(anchorPlay, item, history)
            if (rematchScore <= 0.0) return@mapNotNull null
            val game = gameById[id] ?: BggGame(id, item.name, item.yearPublished?.toString(), item.thumbnailUrl)
            ScoredRecommendation(game, rematchScore, "Good rematch for this table")
        }.sortedByDescending { it.score }

        return (exactGroup + rematch)
            .distinctBy { it.game.id }
            .take(limit)
            .map { RecommendationPick(it.game, it.reason) }
    }

    private data class ScoredRecommendation(
        val game: BggGame,
        val score: Double,
        val reason: String
    )

    private fun buildBestForTonightLane(
        owned: List<GameItem>,
        gameById: Map<Int, BggGame>,
        playerCount: Int?
    ): RecommendationLane? {
        if (playerCount == null) return null
        val picks = owned.mapNotNull { item ->
            val id = item.objectId.toIntOrNull() ?: return@mapNotNull null
            val fit = playerCountFitScore(item, playerCount)
            if (fit <= 0.0) return@mapNotNull null
            val recencyPenalty = recentlyPlayedPenalty(id)
            val score = fit + recencyPenalty + (item.numPlays ?: 0).coerceAtMost(12) * 0.04
            val game = gameById[id] ?: BggGame(id, item.name, item.yearPublished?.toString(), item.thumbnailUrl)
            ScoredRecommendation(game, score, recommendationReasonForPlayerCount(item, playerCount))
        }.sortedByDescending { it.score }
            .take(3)
            .map { RecommendationPick(it.game, it.reason) }
        if (picks.isEmpty()) return null
        return RecommendationLane("best_tonight", "Best For Tonight", "$playerCount players at the table", picks)
    }

    private fun buildGroupFavoritesLane(
        owned: List<GameItem>,
        gameById: Map<Int, BggGame>,
        groupNames: Set<String>,
        playerCount: Int?
    ): RecommendationLane? {
        if (groupNames.isEmpty()) return null
        val picks = scoreGamesForGroup(owned, groupNames, playerCount)
            .sortedByDescending { it.score }
            .take(3)
            .map { RecommendationPick(it.game, it.reason) }
        if (picks.isEmpty()) return null
        return RecommendationLane("group_favorites", "Great With This Group", "Based on your shared history", picks)
    }

    private fun buildNeglectedFavoritesLane(
        owned: List<GameItem>,
        gameById: Map<Int, BggGame>,
        history: List<LoggedPlay>,
        playerCount: Int?
    ): RecommendationLane? {
        val playsByGame = history.groupBy { it.gameId }
        val today = LocalDate.now()
        val picks = owned.mapNotNull { item ->
            val id = item.objectId.toIntOrNull() ?: return@mapNotNull null
            if (!fitsPlayerCount(item, playerCount)) return@mapNotNull null
            val plays = playsByGame[id].orEmpty()
            if (plays.size < 2) return@mapNotNull null
            val lastPlayed = plays.mapNotNull { it.date.toLocalDateOrNull() }.maxOrNull() ?: return@mapNotNull null
            val daysSince = ChronoUnit.DAYS.between(lastPlayed, today).toInt()
            if (daysSince < 21) return@mapNotNull null
            val score = plays.size * 0.45 + (daysSince / 14.0)
            val game = gameById[id] ?: BggGame(id, item.name, item.yearPublished?.toString(), item.thumbnailUrl)
            ScoredRecommendation(game, score, "You play it often, but not lately")
        }.sortedByDescending { it.score }
            .take(3)
            .map { RecommendationPick(it.game, it.reason) }
        if (picks.isEmpty()) return null
        return RecommendationLane("neglected_favorites", "Neglected Favorites", "Loved before, due for a return", picks)
    }

    private fun buildQuickOptionLane(
        owned: List<GameItem>,
        gameById: Map<Int, BggGame>,
        playerCount: Int?
    ): RecommendationLane? {
        val picks = owned.mapNotNull { item ->
            val id = item.objectId.toIntOrNull() ?: return@mapNotNull null
            if (!fitsPlayerCount(item, playerCount)) return@mapNotNull null
            val time = item.playingTime ?: return@mapNotNull null
            if (time <= 0 || time > 60) return@mapNotNull null
            val score = (70 - time).coerceAtLeast(0) / 10.0 + playerCountFitScore(item, playerCount)
            val game = gameById[id] ?: BggGame(id, item.name, item.yearPublished?.toString(), item.thumbnailUrl)
            ScoredRecommendation(game, score, "Quick to table at about $time min")
        }.sortedByDescending { it.score }
            .take(3)
            .map { RecommendationPick(it.game, it.reason) }
        if (picks.isEmpty()) return null
        return RecommendationLane("quick_option", "Quick To Table", "Shorter picks for this group size", picks)
    }

    private fun scoreGamesForGroup(
        owned: List<GameItem>,
        groupNames: Set<String>,
        playerCount: Int?
    ): List<ScoredRecommendation> {
        val history = historyPlays.value
        val gameById = _allGames.value.associateBy { it.id }
        return owned.mapNotNull { item ->
            val id = item.objectId.toIntOrNull() ?: return@mapNotNull null
            if (!fitsPlayerCount(item, playerCount)) return@mapNotNull null
            val matchingPlays = history.filter { play ->
                if (play.gameId != id) return@filter false
                val playNames = play.players.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                val overlap = playNames.intersect(groupNames).size
                overlap >= groupOverlapThreshold(groupNames.size, playNames.size)
            }
            if (matchingPlays.isEmpty()) return@mapNotNull null
            val avgOverlap = matchingPlays.map { play ->
                val playNames = play.players.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                playNames.intersect(groupNames).size.toDouble()
            }.average()
            val score = matchingPlays.size * 0.7 + avgOverlap + recentlyPlayedPenalty(id)
            val game = gameById[id] ?: BggGame(id, item.name, item.yearPublished?.toString(), item.thumbnailUrl)
            ScoredRecommendation(game, score, "Played ${matchingPlays.size}x with this group")
        }.filter { it.score > 0.0 }
    }

    private fun scoreRematchCandidate(anchorPlay: LoggedPlay, item: GameItem, history: List<LoggedPlay>): Double {
        val relationBonus = when {
            item.name.equals(anchorPlay.gameName, ignoreCase = true) -> 1.5
            item.name.contains(anchorPlay.gameName, ignoreCase = true) ||
                anchorPlay.gameName.contains(item.name, ignoreCase = true) -> 1.0
            else -> 0.0
        }
        val sameGroupPlays = history.count { play ->
            val anchorNames = anchorPlay.players.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            val playNames = play.players.map { it.name.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            play.gameId == item.objectId.toIntOrNull() && playNames.intersect(anchorNames).size >= groupOverlapThreshold(anchorNames.size, playNames.size)
        }
        return relationBonus + sameGroupPlays * 0.35
    }

    private fun recentlyPlayedPenalty(gameId: Int): Double {
        val lastPlayed = historyPlays.value
            .filter { it.gameId == gameId }
            .mapNotNull { it.date.toLocalDateOrNull() }
            .maxOrNull() ?: return 0.0
        val days = ChronoUnit.DAYS.between(lastPlayed, LocalDate.now()).toInt()
        return when {
            days < 7 -> -1.4
            days < 21 -> -0.6
            days > 90 -> 0.9
            else -> 0.0
        }
    }

    private fun fitsPlayerCount(item: GameItem, playerCount: Int?): Boolean =
        playerCount == null || playerCountFitScore(item, playerCount) > 0.0

    private fun playerCountFitScore(item: GameItem, playerCount: Int?): Double {
        if (playerCount == null) return 0.5
        fun String?.containsCount(n: Int) =
            this?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.contains(n) == true
        if (item.notRecommendedPlayers.containsCount(playerCount)) return 0.0
        val inBest = item.bestPlayers.containsCount(playerCount)
        val inRecommended = item.recommendedPlayers.containsCount(playerCount)
        val min = item.minPlayers
        val max = item.maxPlayers
        val inOfficialRange = (min == null || playerCount >= min) && (max == null || playerCount <= max)
        return when {
            inBest -> 2.0
            inRecommended -> 1.6
            inOfficialRange -> 1.2
            else -> 0.0
        }
    }

    private fun recommendationReasonForPlayerCount(item: GameItem, playerCount: Int): String =
        when {
            item.bestPlayers?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.contains(playerCount) == true ->
                "Best at $playerCount ${if (playerCount == 1) "player" else "players"}"
            item.recommendedPlayers?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.contains(playerCount) == true ->
                "Recommended for $playerCount ${if (playerCount == 1) "player" else "players"}"
            item.minPlayers != null && item.maxPlayers != null ->
                "Fits ${item.minPlayers}-${item.maxPlayers} players"
            else -> "Good fit for $playerCount players"
        }

    private fun groupOverlapThreshold(groupSize: Int, playSize: Int): Int = when {
        groupSize <= 1 || playSize <= 1 -> 1
        minOf(groupSize, playSize) <= 2 -> 2
        else -> 3
    }

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
        return _players.value.firstOrNull { player ->
            (listOf(player.displayName) + player.aliases).any { it.lowercase().trim() == lower }
        }
    }

    private fun savePlayerHintsFromCurrentPlay() {
        val originals = _originalScannedNames
        if (originals.isEmpty()) return
        val finalPlayers = _editablePlayers.value
        val now = System.currentTimeMillis()
        finalPlayers.forEachIndexed { i, pr ->
            val originalName = originals.getOrNull(i) ?: return@forEachIndexed
            val rosterPlayer = resolveRosterPlayer(pr.name) ?: return@forEachIndexed
            val normalizedOriginal = normalizeForRecognition(originalName)
            val normalizedFinal = normalizeForRecognition(rosterPlayer.displayName)
            if (normalizedOriginal != normalizedFinal) {
                val hint = PlayerRecognitionHint(
                    scannedNameNormalized   = normalizedOriginal,
                    confirmedRosterPlayerId = rosterPlayer.id,
                    playerDisplayName       = rosterPlayer.displayName,
                    timesConfirmed          = 1,
                    lastConfirmedAt         = now
                )
                prefs.savePlayerRecognitionHint(hint)
                Log.d(TAG_PLAYER, "saved hint '${originalName}' -> '${rosterPlayer.displayName}'")
            }
        }
        _originalScannedNames = emptyList()
    }

    fun getPlayerRecognitionHintCount(): Int = prefs.loadPlayerRecognitionHints().size

    fun clearPlayerRecognitionHints() {
        prefs.clearPlayerRecognitionHints()
        Log.d(TAG_PLAYER, "all player recognition hints cleared")
    }

}

private fun mergeHistorySources(local: List<LoggedPlay>, remote: List<LoggedPlay>): List<LoggedPlay> {
    val remoteIds = remote.mapTo(hashSetOf()) { it.id }
    val remoteCorrelationKeys = remote.mapTo(hashSetOf()) { it.historyCorrelationKey() }
    val localOnly = local.filterNot { play ->
        play.id in remoteIds || (play.id.isLikelyLocalUuid() && play.historyCorrelationKey() in remoteCorrelationKeys)
    }
    val combined = (localOnly + remote).sortedWith(compareByDescending<LoggedPlay> { it.date }.thenByDescending { it.id })
    // Guarantee unique IDs — LazyColumn keys must not repeat
    val seenIds = hashSetOf<String>()
    return combined.filter { seenIds.add(it.id) }
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
    // Secondary dedup by ID: two plays can share an ID with different signatures
    // (e.g. BGG re-uses an ID, or normalization diverges). Keep the remote/posted one.
    val byId = linkedMapOf<String, LoggedPlay>()
    bySignature.values.forEach { play ->
        val existing = byId[play.id]
        byId[play.id] = when {
            existing == null -> play
            existing.prefersRemoteIdentityOver(play) -> existing
            play.prefersRemoteIdentityOver(existing) -> play
            else -> existing
        }
    }
    return byId.values.sortedWith(compareByDescending<LoggedPlay> { it.date }.thenByDescending { it.id })
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

private fun LoggedPlay.historyCorrelationKey(): String {
    val normalizedPlayers = players.joinToString("|") { player ->
        listOf(
            player.name.trim().lowercase(),
            player.score.trim(),
            player.isWinner.toString(),
            player.color.trim().lowercase()
        ).joinToString("~")
    }
    return listOf(
        gameId.toString(),
        date,
        durationMinutes.toString(),
        quantity.toString(),
        incomplete.toString(),
        location.trim().lowercase(),
        normalizedPlayers
    ).joinToString("||")
}

private fun String.toLocalDateOrNull(): LocalDate? =
    toFlexibleLocalDateOrNull()

private fun String.isLikelyLocalUuid(): Boolean {
    return Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        .matches(this)
}

private fun findRelatedGames(game: BggGame, collection: List<BggGame>): GameRelations {
    val name = game.name.trim()
    fun separatorIndex(s: String): Int? = listOf(
        s.indexOf(':').takeIf { it > 0 },
        s.indexOf(" \u2013 ").takeIf { it > 0 },
        s.indexOf(" \u2014 ").takeIf { it > 0 },
        s.indexOf(" - ").takeIf { it > 0 }
    ).filterNotNull().minOrNull()

    // True when `candidate` is an expansion of `root` \u2014 either via separator ("Root: Sub")
    // or via space-separated prefix ("Root Sub" where Sub is not the root itself).
    fun isExpansionOf(candidate: String, root: String): Boolean {
        val c = candidate.trim(); val r = root.trim()
        if (c.equals(r, ignoreCase = true)) return false
        val sep = separatorIndex(c)
        return if (sep != null) c.substring(0, sep).trim().equals(r, ignoreCase = true)
        else c.lowercase().startsWith(r.lowercase() + " ")
    }

    val mySepIdx = separatorIndex(name)
    return if (mySepIdx != null) {
        // Separator present \u2014 this is an expansion.
        val root = name.substring(0, mySepIdx).trim()
        val baseGames = collection.filter { other ->
            other.id != game.id && separatorIndex(other.name.trim()) == null &&
            other.name.trim().equals(root, ignoreCase = true)
        }
        val siblings = collection.filter { other -> other.id != game.id && isExpansionOf(other.name, root) }
        GameRelations(isExpansion = true, baseGames = baseGames, expansions = siblings)
    } else {
        // No separator \u2014 could be a base game or a space-separated expansion ("Wingspan Expansion").
        val lowerName = name.lowercase()
        val possibleBase = collection.firstOrNull { other ->
            other.id != game.id && separatorIndex(other.name.trim()) == null &&
            lowerName.startsWith(other.name.trim().lowercase() + " ")
        }
        if (possibleBase != null) {
            // Space-separated expansion detected.
            val siblings = collection.filter { other -> other.id != game.id && isExpansionOf(other.name, possibleBase.name) }
            GameRelations(isExpansion = true, baseGames = listOf(possibleBase), expansions = siblings)
        } else {
            // Base game \u2014 find all expansions (separator-based and space-separated).
            val expansions = collection.filter { other -> other.id != game.id && isExpansionOf(other.name, name) }
            GameRelations(isExpansion = false, baseGames = emptyList(), expansions = expansions)
        }
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
