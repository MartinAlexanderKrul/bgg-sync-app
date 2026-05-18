package cz.nicolsburg.boardflow.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import cz.nicolsburg.boardflow.model.Challenge
import cz.nicolsburg.boardflow.model.ChallengeProgress
import cz.nicolsburg.boardflow.model.PlayerResult
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.nicolsburg.boardflow.AppViewModel
import cz.nicolsburg.boardflow.model.BggGame
import cz.nicolsburg.boardflow.model.GameCandidate
import cz.nicolsburg.boardflow.model.GameRelations
import cz.nicolsburg.boardflow.model.LoggedPlay
import cz.nicolsburg.boardflow.model.RecordMoment
import cz.nicolsburg.boardflow.model.RecommendationPick
import cz.nicolsburg.boardflow.model.ScanRecognitionResult
import cz.nicolsburg.boardflow.model.SessionContext
import cz.nicolsburg.boardflow.model.deriveSessionHub
import cz.nicolsburg.boardflow.ui.history.SessionHubDialog
import cz.nicolsburg.boardflow.model.Player as BggPlayer
import cz.nicolsburg.boardflow.ui.common.BoardFlowButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowCloseGlyph
import cz.nicolsburg.boardflow.ui.common.BoardFlowIconButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowIcons
import cz.nicolsburg.boardflow.ui.common.BoardFlowInlineAction
import cz.nicolsburg.boardflow.ui.common.BoardFlowSecondaryButton
import cz.nicolsburg.boardflow.ui.common.BoardFlowSurfaceTokens
import cz.nicolsburg.boardflow.ui.common.PlayerResultEditorCard
import cz.nicolsburg.boardflow.util.toFlexibleLocalDateOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private data class ChallengeAdvance(
    val challenge: Challenge,
    val from: Int,
    val to: Int,
    val goal: Int
) {
    val isNewlyComplete: Boolean get() = to >= goal && from < goal
}

private data class PostSaveInfo(
    val sessionContext: SessionContext,
    val record: RecordMoment?,
    val anchorPlay: LoggedPlay,
    val challengeAdvances: List<ChallengeAdvance> = emptyList(),
    val stillActive: List<ChallengeProgress> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogPlayScreen(
    viewModel: AppViewModel,
    onPosted: () -> Unit,
    onChangeGame: () -> Unit,
    onNavigateBack: () -> Unit,
    onDiscard: () -> Unit = onNavigateBack,
    onChooseGame: () -> Unit = {},
    onPickRecommendation: (BggGame) -> Unit = {}
) {
    val players         by viewModel.editablePlayers.collectAsState()
    val posting         by viewModel.postLoading.collectAsState()
    val extractedPlay   by viewModel.extractedPlay.collectAsState()
    val gameRelations   by viewModel.gameRelations.collectAsState()
    val additionalGames by viewModel.additionalGames.collectAsState()
    val rosterPlayers   by viewModel.players.collectAsState()
    val historyPlays    by viewModel.historyPlays.collectAsState()
    val recommendationsEnabled by viewModel.recommendationsEnabled.collectAsState()
    val gameCandidates        by viewModel.gameCandidates.collectAsState()
    val scanRecognitionResult by viewModel.scanRecognitionResult.collectAsState()
    val scanStartedWithGame   by viewModel.scanStartedWithGame.collectAsState()
    val scanRetryResult       by viewModel.scanRetryResult.collectAsState()

    // Read prefill once on first composition (consumed from ViewModel).
    val prefill = remember { viewModel.takePrefill() }

    var date           by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var duration       by rememberSaveable { mutableStateOf(prefill?.durationSuggestion ?: "") }
    var location       by rememberSaveable { mutableStateOf(prefill?.location ?: "") }
    var comments       by rememberSaveable { mutableStateOf("") }
    var quantity       by rememberSaveable { mutableStateOf(1) }
    var incomplete     by rememberSaveable { mutableStateOf(false) }
    var nowInStats     by rememberSaveable { mutableStateOf(true) }
    var showAdvanced   by rememberSaveable { mutableStateOf(false) }
    var errorMsg          by remember { mutableStateOf<String?>(null) }
    var showAiOutput      by rememberSaveable { mutableStateOf(false) }
    var showDatePicker    by rememberSaveable { mutableStateOf(false) }
    var focusFirstScore   by remember { mutableStateOf(false) }
    var collapsedPlayers by rememberSaveable { mutableStateOf<List<Boolean>>(emptyList()) }
    var playerRowKeys by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    // Post-save card state. Non-null while post-save card is visible.
    var postSaveInfo     by remember { mutableStateOf<PostSaveInfo?>(null) }
    // Snapshot kept alive so the exit fade animation has content to render.
    var lastPostSaveInfo by remember { mutableStateOf<PostSaveInfo?>(null) }
    if (postSaveInfo != null) lastPostSaveInfo = postSaveInfo
    var sessionHubInfo by remember { mutableStateOf<PostSaveInfo?>(null) }
    var sessionHubTitle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionHubInfo?.anchorPlay?.sessionId) {
        sessionHubTitle = viewModel.getSessionTitle(sessionHubInfo?.anchorPlay?.sessionId)
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                date.toFlexibleLocalDateOrNull()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val gameName = viewModel.selectedGame?.name ?: "Unknown game"
    val headerGameName = if (additionalGames.isEmpty()) gameName
        else (listOf(gameName) + additionalGames.map { it.name }).joinToString(" + ")

    LaunchedEffect(extractedPlay?.date) {
        extractedPlay?.date?.takeIf { it.isNotBlank() }?.let { date = it }
    }

    val initialDate = extractedPlay?.date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
    val hasUnsavedChanges by remember(
        date,
        duration,
        location,
        comments,
        quantity,
        incomplete,
        nowInStats,
        players,
        additionalGames,
        extractedPlay
    ) {
        derivedStateOf {
            date != initialDate ||
                duration.isNotBlank() ||
                location.isNotBlank() ||
                comments.isNotBlank() ||
                quantity != 1 ||
                incomplete ||
                !nowInStats ||
                players.isNotEmpty() ||
                additionalGames.isNotEmpty() ||
                extractedPlay != null
        }
    }

    LaunchedEffect(hasUnsavedChanges) {
        viewModel.setLogPlayHasUnsavedChanges(hasUnsavedChanges)
    }

    LaunchedEffect(players.size) {
        if (collapsedPlayers.size != players.size) {
            collapsedPlayers = List(players.size) { index -> collapsedPlayers.getOrElse(index) { false } }
        }
        if (playerRowKeys.size != players.size) {
            playerRowKeys = List(players.size) { index -> playerRowKeys.getOrElse(index) { java.util.UUID.randomUUID().toString() } }
        }
    }

    LaunchedEffect(extractedPlay) {
        if (extractedPlay != null && players.isNotEmpty()) {
            collapsedPlayers = players.map { it.isReadyToCollapse() }
        }
    }

    LaunchedEffect(postSaveInfo) { viewModel.setLogPlayPostSaveShowing(postSaveInfo != null) }

    BackHandler {
        if (postSaveInfo != null) {
            postSaveInfo = null
            onPosted()
        } else {
            onDiscard()
        }
    }

    val online = viewModel.isOnline()
    val totalGames = 1 + additionalGames.size
    var nameFieldFocusIndex by remember { mutableStateOf(-1) }

    // Collapse every card whose player data is complete. Called when the user moves away
    // from the current player by adding another one.
    val collapseCompletePlayers: () -> Unit = {
        collapsedPlayers = players.mapIndexed { i, p ->
            collapsedPlayers.getOrElse(i) { false } || p.isReadyToCollapse()
        }
    }

    val addEditablePlayer: () -> Unit = {
        val nextIndex = players.size
        collapseCompletePlayers()
        collapsedPlayers = collapsedPlayers + false
        playerRowKeys = playerRowKeys + java.util.UUID.randomUUID().toString()
        viewModel.addPlayer()
        nameFieldFocusIndex = nextIndex
    }

    // Compute frequent players in composable scope so remember is valid.
    val gameId = viewModel.selectedGame?.id ?: 0
    val excludedNames = remember(players) { players.map { it.name.trim() }.toSet() }
    val frequentPlayers = remember(gameId, excludedNames) {
        viewModel.getFrequentPlayers(gameId, excludedNames)
    }
    val recentPlayers = remember(excludedNames) {
        viewModel.getRecentPlayers(excludedNames)
    }
    val fabLabel = when {
        posting               -> "Posting..."
        !online && totalGames > 1 -> "Save $totalGames plays locally"
        !online               -> "Save Play Locally"
        totalGames > 1        -> "Log $totalGames plays to BGG"
        else                  -> "Log Play to BGG"
    }

    fun submitPlay() {
        if (!posting) {
            errorMsg = null
            val parsedDate = date.toFlexibleLocalDateOrNull() ?: LocalDate.now()
            val durationMin = duration.toIntOrNull() ?: 0
            val progressBefore = viewModel.getChallengeProgressList()
            viewModel.captureHistorySnapshot()
            viewModel.postPlay(
                date = parsedDate,
                durationMinutes = durationMin,
                location = location,
                comments = comments,
                quantity = quantity,
                incomplete = incomplete,
                nowInStats = nowInStats,
                onSuccess = { savedPlay, progressAfter ->
                    val game = viewModel.selectedGame
                    if (game != null) {
                        val ctx = SessionContext(
                            sessionId = savedPlay.sessionId ?: java.util.UUID.randomUUID().toString(),
                            gameId = game.id,
                            gameName = game.name,
                            players = players,
                            location = location,
                            startedAt = savedPlay.playedAt ?: System.currentTimeMillis(),
                            lastPlayTimestamp = savedPlay.playedAt ?: System.currentTimeMillis()
                        )
                        val record = viewModel.detectRecord(game.id, game.name, players)
                        val advances = progressAfter.mapNotNull { after ->
                            val before = progressBefore.firstOrNull { it.challenge.id == after.challenge.id }
                                ?: return@mapNotNull null
                            if (after.currentCount > before.currentCount)
                                ChallengeAdvance(after.challenge, before.currentCount, after.currentCount, after.goalCount)
                            else null
                        }
                        val advancedIds = advances.map { it.challenge.id }.toSet()
                        val stillActive = progressAfter.filter { it.isActive && it.challenge.id !in advancedIds }
                        postSaveInfo = PostSaveInfo(ctx, record, savedPlay, advances, stillActive)
                    } else {
                        onPosted()
                    }
                },
                onError = { errorMsg = it }
            )
        }
    }

    val contentBlur by animateDpAsState(
        targetValue    = if (postSaveInfo != null) 18.dp else 0.dp,
        animationSpec  = tween(200),
        label          = "contentBlur"
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).blur(contentBlur)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (errorMsg != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = errorMsg.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                        BoardFlowButton(
                            onClick = ::submitPlay,
                            enabled = !posting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (posting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(fabLabel)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp)
            ) {
                gameRelations?.let { relations ->
                    val relatedGames = if (relations.isExpansion) relations.baseGames else relations.expansions
                    if (relatedGames.isNotEmpty()) {
                        item {
                            RelatedGamesBanner(
                                relations = relations,
                                additionalGames = additionalGames,
                                onToggleGame = { viewModel.toggleAdditionalGame(it) }
                            )
                        }
                    }
                }

                item {
                    // Only show passive AI hint when no actionable suggestion banner is visible.
                    val detectedGameHint = extractedPlay?.detectedGameTitle
                        ?.takeIf { title ->
                            title.isNotBlank() &&
                            !title.equals(gameName, ignoreCase = true) &&
                            gameCandidates.isEmpty()
                        }
                    SessionDetailsCard(
                        title = "Log Play",
                        gameName = headerGameName,
                        detectedGameHint = detectedGameHint,
                        date = date,
                        duration = duration,
                        location = location,
                        notes = comments,
                        showAdvanced = showAdvanced,
                        quantity = quantity,
                        incomplete = incomplete,
                        nowInStats = nowInStats,
                        onDateClick = { showDatePicker = true },
                        onDateChange = { date = it },
                        onDurationChange = { duration = it },
                        onLocationChange = { location = it },
                        onNotesChange = { comments = it },
                        onAdvancedToggle = { showAdvanced = !showAdvanced },
                        onQuantityDecrease = { if (quantity > 1) quantity-- },
                        onQuantityIncrease = { quantity++ },
                        onIncompleteChange = { incomplete = it },
                        onNowInStatsChange = { nowInStats = it }
                    )
                }

                scanRecognitionResult?.let { result ->
                    item {
                        ScanResultBanner(
                            result = result,
                            hasPreselectedGame = scanStartedWithGame,
                            onDismiss = { viewModel.dismissScanRecognitionResult() },
                            onChooseGame = onChooseGame
                        )
                    }
                }

                if (gameCandidates.isNotEmpty()) {
                    item {
                        GameSuggestionBanner(
                            candidate = gameCandidates.first(),
                            geminiConfidence = extractedPlay?.detectedGameConfidence,
                            detectionEvidence = extractedPlay?.gameDetectionEvidence,
                            hasPreselectedGame = scanStartedWithGame,
                            onAccept = { viewModel.acceptGameSuggestion(gameCandidates.first().game) },
                            onDismiss = { viewModel.dismissGameSuggestion() },
                            onChooseGame = onChooseGame
                        )
                    }
                }

                if (scanRetryResult != null) {
                    item {
                        ScanRetryBanner(
                            onApply = { viewModel.acceptRetryResult() },
                            onDismiss = { viewModel.dismissRetryResult() }
                        )
                    }
                }

                item {
                    PlayersHeader(
                        playerCount = players.size,
                        hasAiOutput = extractedPlay != null,
                        onToggleAiOutput = { showAiOutput = !showAiOutput },
                        onAddPlayer = addEditablePlayer
                    )
                }

                if (frequentPlayers.isNotEmpty() || recentPlayers.isNotEmpty()) {
                    item {
                        FrequentPlayerChips(
                            gameName = gameName,
                            frequentPlayers = frequentPlayers,
                            recentPlayers = recentPlayers,
                            onAddPlayer = {
                                collapseCompletePlayers()
                                collapsedPlayers = collapsedPlayers + false
                                playerRowKeys = playerRowKeys + java.util.UUID.randomUUID().toString()
                                viewModel.addPlayerFromRoster(it)
                            }
                        )
                    }
                }

                val extracted = extractedPlay
                if (showAiOutput && extracted != null) {
                    item {
                        AiOutputCard(rawText = extracted.rawText, modelUsed = extracted.modelUsed)
                    }
                }

                itemsIndexed(
                    items = players,
                    key = { index, _ -> playerRowKeys.getOrElse(index) { "player-$index" } }
                ) { index, player ->
                    PlayerEditCard(
                        player = player,
                        rosterPlayers = rosterPlayers,
                        onUpdate = { viewModel.updatePlayer(index, it) },
                        onRemove = {
                            if (index < collapsedPlayers.size) {
                                collapsedPlayers = collapsedPlayers.toMutableList().also { it.removeAt(index) }
                            }
                            if (index < playerRowKeys.size) {
                                playerRowKeys = playerRowKeys.toMutableList().also { it.removeAt(index) }
                            }
                            viewModel.removePlayer(index)
                        },
                        collapsed = collapsedPlayers.getOrElse(index) { false },
                        onToggleCollapsed = {
                            collapsedPlayers = collapsedPlayers.toMutableList().also { it[index] = !it[index] }
                        },
                        requestScoreFocus = index == 0 && focusFirstScore,
                        onFocusDone = { focusFirstScore = false },
                        requestNameFocus = index == nameFieldFocusIndex,
                        onNameFocusDone = { nameFieldFocusIndex = -1 }
                    )
                }

                item {
                    AddPlayerRow(onClick = addEditablePlayer)
                }
            }
        }
    }

    // Post-save overlay — keeps form visible underneath, dims background
    AnimatedVisibility(
        visible = postSaveInfo != null,
        enter   = fadeIn(tween(180)),
        exit    = fadeOut(tween(160))
    ) {
        val info = lastPostSaveInfo ?: return@AnimatedVisibility
        val nextRecommendations = remember(info.anchorPlay, historyPlays) {
            if (recommendationsEnabled) viewModel.getPostSaveRecommendations(info.anchorPlay) else emptyList()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            FireworksLayer(
                primaryColor = MaterialTheme.colorScheme.primary,
                modifier     = Modifier.fillMaxSize()
            )
            PostSaveCard(
                info = info,
                nextRecommendations = nextRecommendations,
                challengeAdvances = info.challengeAdvances,
                stillActive = info.stillActive,
                onOpenSessionHub = { sessionHubInfo = info },
                onPlayAgain = {
                    viewModel.setupPlayAgain(info.sessionContext)
                    date = LocalDate.now().toString()
                    location = info.sessionContext.location
                    duration = ""
                    comments = ""
                    errorMsg = null
                    focusFirstScore = true
                    postSaveInfo = null
                },
                onChangeGame = {
                    viewModel.setupChangeGameSession(info.sessionContext)
                    postSaveInfo = null
                    onChangeGame()
                },
                onPickRecommendation = { game ->
                    viewModel.setupChangeGameSession(info.sessionContext)
                    viewModel.selectGame(game)
                    postSaveInfo = null
                    onPickRecommendation(game)
                },
                onDone = {
                    postSaveInfo = null
                    onPosted()
                }
            )
        }
    }

    sessionHubInfo?.let { info ->
        SessionHubDialog(
            session = historyPlays.deriveSessionHub(info.anchorPlay, sessionHubTitle),
            players = rosterPlayers,
            onDismiss = { sessionHubInfo = null; postSaveInfo = null },
            onRenameSession = { sessionId, title ->
                viewModel.renameSession(
                    sessionId = sessionId,
                    newTitle = title,
                    onSuccess = { savedTitle -> sessionHubTitle = savedTitle.ifBlank { null } }
                )
            },
            onPlayAgain = { session ->
                viewModel.setupPlayAgainFromSession(session.plays)
                sessionHubInfo = null
                postSaveInfo = null
                date = LocalDate.now().toString()
                location = session.plays.firstOrNull()?.location ?: ""
                duration = ""
                comments = ""
                errorMsg = null
                focusFirstScore = true
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Compact form composables
// ---------------------------------------------------------------------------

@Composable
private fun SessionDetailsCard(
    title: String,
    gameName: String,
    detectedGameHint: String? = null,
    date: String,
    duration: String,
    location: String,
    notes: String,
    showAdvanced: Boolean,
    quantity: Int,
    incomplete: Boolean,
    nowInStats: Boolean,
    onDateClick: () -> Unit,
    onDateChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onAdvancedToggle: () -> Unit,
    onQuantityDecrease: () -> Unit,
    onQuantityIncrease: () -> Unit,
    onIncompleteChange: (Boolean) -> Unit,
    onNowInStatsChange: (Boolean) -> Unit
) {
    Surface(
        shape = BoardFlowSurfaceTokens.ContentCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactGameHeader(title = title, gameName = gameName, detectedGameHint = detectedGameHint)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SessionFieldLabel("Date")
                    CompactTextField(
                        value = date,
                        onValueChange = onDateChange,
                        label = "Date",
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            BoardFlowIconButton(onClick = onDateClick, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date", modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
                Column(
                    modifier = Modifier.weight(0.7f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SessionFieldLabel("Duration (min)")
                    CompactTextField(
                        value = duration,
                        onValueChange = onDurationChange,
                        label = "Duration",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SessionFieldLabel("Location")
                CompactTextField(
                    value = location,
                    onValueChange = onLocationChange,
                    label = "Location",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TextButton(
                onClick = onAdvancedToggle,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(if (showAdvanced) "Hide options" else "More options")
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = showAdvanced,
                enter = expandVertically() + fadeIn(tween(150)),
                exit = shrinkVertically() + fadeOut(tween(150))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SessionFieldLabel("Notes")
                        CompactTextField(
                            value = notes,
                            onValueChange = onNotesChange,
                            label = "Notes",
                            singleLine = false,
                            minLines = 3,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CompactStepperRow(
                        label = "Quantity",
                        value = quantity.toString(),
                        subtitle = "Log multiple identical plays"
                    ) {
                        BoardFlowIconButton(onClick = onQuantityDecrease) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        Text(
                            quantity.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(22.dp),
                            textAlign = TextAlign.Center
                        )
                        BoardFlowIconButton(onClick = onQuantityIncrease) {
                            Icon(BoardFlowIcons.Add, contentDescription = "Increase")
                        }
                    }
                    CompactSwitchRow(
                        label = "Incomplete play",
                        subtitle = "Game was not finished",
                        checked = incomplete,
                        onCheckedChange = onIncompleteChange
                    )
                    CompactSwitchRow(
                        label = "Count in stats",
                        subtitle = "Include this play in BGG statistics",
                        checked = nowInStats,
                        onCheckedChange = onNowInStatsChange
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    )
}

@Composable
private fun CompactGameHeader(title: String, gameName: String, detectedGameHint: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                gameName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (!detectedGameHint.isNullOrBlank()) {
                Text(
                    "AI detected: $detectedGameHint",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
        placeholder = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        },
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier.height(if (singleLine) 52.dp else 92.dp)
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlayersHeader(
    playerCount: Int,
    hasAiOutput: Boolean,
    onToggleAiOutput: () -> Unit,
    onAddPlayer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.then(
                if (hasAiOutput) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onToggleAiOutput
                    )
                } else Modifier
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.People,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                if (playerCount > 0) "Players ($playerCount)" else "Players",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        BoardFlowIconButton(onClick = onAddPlayer) {
            Icon(Icons.Default.Add, contentDescription = "Add player", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PlayerEditCard(
    player: cz.nicolsburg.boardflow.model.PlayerResult,
    rosterPlayers: List<cz.nicolsburg.boardflow.model.Player>,
    onUpdate: (cz.nicolsburg.boardflow.model.PlayerResult) -> Unit,
    onRemove: () -> Unit,
    collapsed: Boolean = false,
    onToggleCollapsed: (() -> Unit)? = null,
    requestScoreFocus: Boolean = false,
    onFocusDone: () -> Unit = {},
    requestNameFocus: Boolean = false,
    onNameFocusDone: () -> Unit = {}
) {
    PlayerResultEditorCard(
        player = player,
        rosterPlayers = rosterPlayers,
        onUpdate = onUpdate,
        onRemove = onRemove,
        collapsed = collapsed,
        onToggleCollapsed = onToggleCollapsed,
        requestScoreFocus = requestScoreFocus,
        onFocusDone = onFocusDone,
        requestNameFocus = requestNameFocus,
        onNameFocusDone = onNameFocusDone
    )
}

private fun cz.nicolsburg.boardflow.model.PlayerResult.isReadyToCollapse(): Boolean =
    name.isNotBlank() && score.isNotBlank()

@Composable
private fun AddPlayerRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BoardFlowSurfaceTokens.ContentCardShape)
            .clickable(onClick = onClick),
        shape = BoardFlowSurfaceTokens.ContentCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Add player",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun AiOutputCard(rawText: String, modelUsed: String? = null) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Raw AI response",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (modelUsed != null) {
                        Text(
                            modelUsed,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                BoardFlowInlineAction(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(rawText))
                        copied = true
                    },
                    icon = Icons.Default.ContentCopy
                ) {
                    Text(if (copied) "Copied!" else "Copy")
                }
            }
            SelectionContainer {
                Text(
                    text = rawText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompactStepperRow(
    label: String,
    value: String,
    subtitle: String,
    controls: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = controls
            )
        }
    }
}

@Composable
private fun CompactSwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

// ---------------------------------------------------------------------------
// Post-save card (overlay)
// ---------------------------------------------------------------------------

@Composable
private fun PostSaveCard(
    info: PostSaveInfo,
    nextRecommendations: List<RecommendationPick>,
    onOpenSessionHub: () -> Unit,
    onPlayAgain: () -> Unit,
    onChangeGame: () -> Unit,
    onPickRecommendation: (BggGame) -> Unit,
    onDone: () -> Unit,
    challengeAdvances: List<ChallengeAdvance> = emptyList(),
    stillActive: List<ChallengeProgress> = emptyList()
) {
    var animIn by remember { mutableStateOf(false) }
    var recommendationsExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { animIn = true }

    val players = info.sessionContext.players
    val winners = players.filter { it.isWinner }
    val primaryMemory = info.record?.displayText ?: when {
        winners.size == 1 -> "${winners.first().name.trim()} takes the crown."
        winners.size > 1  -> "${winners.joinToString(" & ") { it.name.trim() }} share the victory."
        players.isNotEmpty() -> "Session recorded. The chronicle grows."
        else -> "This play is part of the record now."
    }
    val hasNumericScores = players.any { it.score.trim().toDoubleOrNull() != null }
    val sortedPlayers = if (hasNumericScores) {
        players.sortedByDescending { it.score.trim().toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
    } else {
        players
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = animIn,
            enter = slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec  = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) + scaleIn(
                initialScale   = 0.96f,
                animationSpec  = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium))
        ) {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                val heroColor = MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(heroColor.copy(alpha = 0.18f), Color.Transparent),
                                    endY   = size.height * 0.46f
                                )
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Game name — all-caps label at the very top
                    if (info.sessionContext.gameName.isNotBlank()) {
                        Text(
                            info.sessionContext.gameName.uppercase(),
                            style         = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.5.sp,
                            color         = MaterialTheme.colorScheme.primary,
                            textAlign     = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    // Hero badge — layered glow rings
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(heroColor.copy(alpha = 0.07f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .background(heroColor.copy(alpha = 0.13f), CircleShape)
                        )
                        Box(
                            modifier         = Modifier
                                .size(50.dp)
                                .background(heroColor.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint               = heroColor,
                                modifier           = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Primary message
                    Text(
                        primaryMemory,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        textAlign  = TextAlign.Center,
                        lineHeight = 30.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // Date — day name adds warmth over a bare date
                    val dateLabel = buildString {
                        append(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")))
                        val location = info.sessionContext.location
                        if (location.isNotBlank()) append(" · $location")
                    }
                    Text(
                        dateLabel,
                        style     = MaterialTheme.typography.labelSmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                        textAlign = TextAlign.Center
                    )

                    // Player results
                    if (sortedPlayers.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Spacer(Modifier.height(14.dp))
                        Column(
                            modifier            = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sortedPlayers.forEach { player ->
                                VictoryPlayerRow(player = player)
                            }
                        }
                    }

                    val hasAnyChallengeData = challengeAdvances.isNotEmpty() || stillActive.isNotEmpty()
                    if (hasAnyChallengeData) {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Challenges",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )

                                // Completed advances — prominent accent cards
                                val pc  = MaterialTheme.colorScheme.primaryContainer
                                val opc = MaterialTheme.colorScheme.onPrimaryContainer
                                val pri = MaterialTheme.colorScheme.primary
                                challengeAdvances.filter { it.isNewlyComplete }.forEach { advance ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = pc.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, pri.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(pri.copy(alpha = 0.22f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.EmojiEvents,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = pri
                                                )
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    advance.challenge.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = opc,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "Complete!",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = pri
                                                )
                                            }
                                        }
                                    }
                                }

                                // Progress advances — in-progress card, one step below completion
                                challengeAdvances.filter { !it.isNewlyComplete }.take(2).forEach { advance ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = pc.copy(alpha = 0.30f),
                                        border = BorderStroke(1.dp, pri.copy(alpha = 0.20f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(pri.copy(alpha = 0.16f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint = pri
                                                    )
                                                }
                                                Text(
                                                    advance.challenge.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = pc.copy(alpha = 0.80f)
                                                ) {
                                                    Text(
                                                        "${advance.to} / ${advance.goal}",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = opc
                                                    )
                                                }
                                            }
                                            LinearProgressIndicator(
                                                progress = { advance.to.toFloat() / advance.goal.coerceAtLeast(1) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(999.dp)),
                                                color = pri,
                                                trackColor = pc.copy(alpha = 0.50f)
                                            )
                                        }
                                    }
                                }

                                // Still-active — muted compact rows, lower visual tier
                                val activeSlots = (3 - challengeAdvances.size).coerceIn(0, 2)
                                if (activeSlots > 0 && stillActive.isNotEmpty()) {
                                    if (challengeAdvances.isNotEmpty()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                        )
                                        Text(
                                            "Still in progress",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                    stillActive.take(activeSlots).forEach { progress ->
                                        val fraction = progress.currentCount.toFloat() / progress.goalCount.coerceAtLeast(1)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    progress.challenge.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                LinearProgressIndicator(
                                                    progress = { fraction },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .clip(RoundedCornerShape(999.dp)),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f),
                                                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                                                )
                                            }
                                            Text(
                                                "${progress.currentCount} / ${progress.goalCount}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Primary CTA — most common next action
                    BoardFlowButton(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
                        Text("Play again")
                    }

                    Spacer(Modifier.height(8.dp))

                    // Secondary CTAs — side-by-side to save height and signal equal priority
                    Row(
                        modifier                = Modifier.fillMaxWidth(),
                        horizontalArrangement   = Arrangement.spacedBy(8.dp)
                    ) {
                        BoardFlowSecondaryButton(onClick = onOpenSessionHub, modifier = Modifier.weight(1f)) {
                            Text("View session")
                        }
                        BoardFlowSecondaryButton(onClick = onChangeGame, modifier = Modifier.weight(1f)) {
                            Text("Change game")
                        }
                    }

                    // Ghost dismiss — de-emphasised so the eye skips it unless intended
                    if (nextRecommendations.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { recommendationsExpanded = !recommendationsExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Try next",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    if (recommendationsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(visible = recommendationsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    nextRecommendations.take(2).forEach { pick ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
                                            modifier = Modifier.clickable { onPickRecommendation(pick.game) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        pick.game.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        pick.reason,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.ChevronRight,
                                                    contentDescription = "Log ${pick.game.name}",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VictoryPlayerRow(player: PlayerResult) {
    val isWinner = player.isWinner
    val primary  = MaterialTheme.colorScheme.primary
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = if (isWinner) primary.copy(alpha = 0.10f)
                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = if (isWinner) BorderStroke(1.dp, primary.copy(alpha = 0.30f)) else null
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent strip for winner
            if (isWinner) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(primary, primary.copy(alpha = 0.55f))
                            )
                        )
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start  = if (isWinner) 10.dp else 12.dp,
                        end    = 12.dp,
                        top    = 11.dp,
                        bottom = 11.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isWinner) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint               = primary,
                        modifier           = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    player.name.trim(),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isWinner) primary else MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.weight(1f)
                )
                val score = player.score.trim()
                Text(
                    score.ifBlank { "—" },
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isWinner) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fireworks
// ---------------------------------------------------------------------------

private data class FireParticle(
    val cx: Float, val cy: Float,
    val angle: Float, val speed: Float,
    val color: Color, val radius: Float, val delay: Float
)

private fun buildFireworks(primary: Color): List<FireParticle> {
    val rng = Random(42)
    val palette = listOf(primary, primary, Color.White, Color(0xFFFFF59D))
    // Five burst origins spread around the screen edges (fractions of canvas size).
    // Delays are staggered so they pop in sequence rather than all at once.
    val bursts = listOf(
        Triple(0.12f, 0.22f, 0.00f),
        Triple(0.88f, 0.20f, 0.12f),
        Triple(0.50f, 0.07f, 0.22f),
        Triple(0.20f, 0.70f, 0.32f),
        Triple(0.80f, 0.68f, 0.40f),
    )
    return buildList {
        for ((bx, by, bd) in bursts) {
            repeat(14) { i ->
                val base = i * (6.2832f / 14f)
                val jitter = (rng.nextFloat() - 0.5f) * 0.5f
                add(FireParticle(
                    cx     = bx,
                    cy     = by,
                    angle  = base + jitter,
                    speed  = 0.4f + rng.nextFloat() * 0.6f,
                    color  = palette[rng.nextInt(palette.size)],
                    radius = 3f + rng.nextFloat() * 3.5f,
                    delay  = bd + rng.nextFloat() * 0.04f
                ))
            }
        }
    }
}

@Composable
private fun FireworksLayer(primaryColor: Color, modifier: Modifier = Modifier) {
    val particles = remember(primaryColor) { buildFireworks(primaryColor) }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, animationSpec = tween(durationMillis = 1700, easing = LinearEasing))
    }
    val p = anim.value
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        for (particle in particles) {
            val t = ((p - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val eased = 1f - (1f - t) * (1f - t)           // ease-out: fast burst, slow settle
            val dist  = w * 0.22f * particle.speed
            val dx    = cos(particle.angle) * dist * eased
            val dy    = sin(particle.angle) * dist * eased + dist * 0.28f * t * t  // gravity
            val alpha = ((1f - t * t) * 1.5f).coerceIn(0f, 1f)
            val r     = particle.radius * (1f - t * 0.4f)
            drawCircle(
                color  = particle.color.copy(alpha = alpha),
                radius = r,
                center = Offset(w * particle.cx + dx, h * particle.cy + dy)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Frequent player chips
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrequentPlayerChips(
    gameName: String,
    frequentPlayers: List<BggPlayer>,
    recentPlayers: List<BggPlayer>,
    onAddPlayer: (BggPlayer) -> Unit
) {
    val recentOnly = recentPlayers.filter { r -> frequentPlayers.none { it.id == r.id } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (frequentPlayers.isNotEmpty()) {
            Text(
                if (gameName.isNotBlank()) "Frequent for $gameName" else "Frequent players",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp)
            ) {
                frequentPlayers.forEach { player ->
                    PlayerChip(player = player, onClick = { onAddPlayer(player) })
                }
            }
        }
        if (recentOnly.isNotEmpty()) {
            Text(
                "Recent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp)
            ) {
                recentOnly.forEach { player ->
                    PlayerChip(player = player, onClick = { onAddPlayer(player) })
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(player: BggPlayer, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label   = { Text(player.displayName, style = MaterialTheme.typography.labelMedium) },
        icon    = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(playerInitialColor(player.displayName), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = player.displayName.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                )
            }
        }
    )
}

private fun playerInitialColor(name: String): Color {
    val palette = listOf(
        Color(0xFF7C4DFF), Color(0xFF448AFF), Color(0xFF00ACC1),
        Color(0xFF43A047), Color(0xFFFF8F00), Color(0xFFE91E63),
        Color(0xFF795548), Color(0xFF546E7A)
    )
    return palette[(name.hashCode() and 0x7FFFFFFF) % palette.size]
}

// ---------------------------------------------------------------------------
// Retry result banner — shown when a background re-extraction succeeded
// ---------------------------------------------------------------------------

@Composable
private fun ScanRetryBanner(onApply: () -> Unit, onDismiss: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Scan retry got a cleaner result.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = primary
                )
            }
            Text(
                "Apply it to replace the current player data?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardFlowSecondaryButton(onClick = onDismiss) { Text("Dismiss") }
                BoardFlowButton(onClick = onApply) { Text("Apply update") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scan recognition result banner (non-blocking feedback after Quick Scan)
// ---------------------------------------------------------------------------

@Composable
private fun ScanResultBanner(
    result: ScanRecognitionResult,
    hasPreselectedGame: Boolean,
    onDismiss: () -> Unit,
    onChooseGame: () -> Unit
) {
    // Auto-dismiss the success state after 7 seconds to give time to read + act.
    if (result is ScanRecognitionResult.AutoSwitched) {
        LaunchedEffect(result) {
            kotlinx.coroutines.delay(7000)
            onDismiss()
        }
    }

    val isSuccess = result is ScanRecognitionResult.AutoSwitched
    val message = when (result) {
        is ScanRecognitionResult.AutoSwitched      -> "Detected and switched to ${result.gameName}"
        is ScanRecognitionResult.NoCollectionMatch -> "Detected ${result.detectedTitle}, but it is not in your collection"
        is ScanRecognitionResult.LowConfidence     -> "Could not confidently detect the game"
    }

    Surface(
        shape  = BoardFlowSurfaceTokens.ContentCardShape,
        color  = if (isSuccess)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSuccess)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (isSuccess) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                BoardFlowIconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    BoardFlowCloseGlyph(
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(14.dp),
                        iconSize = 14.dp
                    )
                }
            }
            when {
                isSuccess && hasPreselectedGame -> BoardFlowSecondaryButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Keep current") }

                isSuccess && !hasPreselectedGame -> BoardFlowSecondaryButton(
                    onClick  = onChooseGame,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose another game") }

                else -> BoardFlowSecondaryButton(
                    onClick  = onChooseGame,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose game") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Game suggestion banner (shown when detection confidence is below auto-switch
// threshold or when there are multiple plausible candidates)
// ---------------------------------------------------------------------------

@Composable
private fun GameSuggestionBanner(
    candidate: GameCandidate,
    geminiConfidence: Float? = null,
    detectionEvidence: String? = null,
    hasPreselectedGame: Boolean = false,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onChooseGame: () -> Unit = {}
) {
    val onSurfaceMuted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
        border   = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Header row: label + dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Detected game",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceMuted.copy(alpha = 0.60f)
                )
                BoardFlowIconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    BoardFlowCloseGlyph(
                        contentDescription = "Dismiss suggestion",
                        modifier = Modifier.size(14.dp),
                        iconSize = 14.dp
                    )
                }
            }

            // Game title
            Text(
                candidate.game.name,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary
            )

            // Confidence + evidence
            val confidenceLabel = when {
                geminiConfidence != null && geminiConfidence >= 0.90f -> "High confidence"
                geminiConfidence != null && geminiConfidence >= 0.70f -> "Good match"
                geminiConfidence != null                              -> "Possible match"
                else                                                  -> null
            }
            val confPct = geminiConfidence?.let { "${(it * 100).toInt()}%" }
            val confLine = listOfNotNull(confidenceLabel, confPct).joinToString(" · ")
            if (confLine.isNotBlank()) {
                Text(
                    confLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceMuted.copy(alpha = 0.60f)
                )
            }
            if (!detectionEvidence.isNullOrBlank()) {
                Text(
                    detectionEvidence,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceMuted.copy(alpha = 0.50f)
                )
            }

            // Actions — right-aligned, lightweight
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPreselectedGame) {
                    BoardFlowInlineAction(onClick = onDismiss) { Text("Keep current") }
                } else {
                    BoardFlowInlineAction(onClick = onChooseGame) { Text("Choose another") }
                }
                Spacer(Modifier.width(4.dp))
                BoardFlowSecondaryButton(onClick = onAccept) { Text("Use this game") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Related games banner (unchanged)
// ---------------------------------------------------------------------------

@Composable
private fun RelatedGamesBanner(
    relations: GameRelations,
    additionalGames: List<BggGame>,
    onToggleGame: (BggGame) -> Unit
) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    val relatedGames = if (relations.isExpansion) relations.baseGames else relations.expansions
    val label = if (relations.isExpansion) "Expansion - also post for base game?"
                else "Also post for an expansion?"
    var expanded by rememberSaveable { mutableStateOf(false) }
    val anySelected = relatedGames.any { game -> additionalGames.any { it.id == game.id } }

    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant,
        shape  = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { dismissed = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    BoardFlowCloseGlyph(
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                        iconSize = 16.dp
                    )
                }
            }

            if (expanded) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp)
                ) {
                    relatedGames.forEach { game ->
                        val selected = additionalGames.any { it.id == game.id }
                        FilterChip(
                            selected = selected,
                            onClick  = { onToggleGame(game) },
                            label    = { Text(game.name, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                TextButton(
                    onClick = { expanded = false },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Show less", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                TwoRowGameChips(
                    games = relatedGames,
                    additionalGames = additionalGames,
                    onToggleGame = onToggleGame,
                    onExpand = { expanded = true }
                )
            }

            if (anySelected) {
                Text(
                    "Ticked games will be posted automatically with the same players & scores.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TwoRowGameChips(
    games: List<BggGame>,
    additionalGames: List<BggGame>,
    onToggleGame: (BggGame) -> Unit,
    onExpand: () -> Unit,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 4.dp
) {
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val hSpacing = horizontalSpacing.roundToPx()
        val vSpacing = verticalSpacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Int.MAX_VALUE)

        // Measure all chips
        val allPlaceables = games.mapIndexed { i, game ->
            val selected = additionalGames.any { it.id == game.id }
            subcompose("chip_$i") {
                FilterChip(
                    selected = selected,
                    onClick  = { onToggleGame(game) },
                    label    = { Text(game.name, style = MaterialTheme.typography.labelMedium) }
                )
            }.first().measure(loose)
        }

        // Simulate flow layout and find the index where row 3 would begin
        var curX = 0; var curY = 0; var rowH = 0; var row = 1
        var cutIndex = allPlaceables.size
        for ((i, p) in allPlaceables.withIndex()) {
            if (curX > 0 && curX + hSpacing + p.width > constraints.maxWidth) {
                curY += rowH + vSpacing; curX = 0; rowH = 0; row++
            }
            if (row > 2) { cutIndex = i; break }
            curX += (if (curX == 0) 0 else hSpacing) + p.width
            rowH = maxOf(rowH, p.height)
        }

        val hasOverflow = cutIndex < allPlaceables.size

        // Measure "Show all" button only when there is overflow
        val showAllPlaceable = if (hasOverflow) {
            subcompose("showAll") {
                TextButton(
                    onClick = onExpand,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Show all (${games.size})", style = MaterialTheme.typography.labelSmall)
                }
            }.first().measure(loose)
        } else null

        // Layout rows 1–2 chips
        curX = 0; curY = 0; rowH = 0
        val positions = allPlaceables.take(cutIndex).map { p ->
            if (curX > 0 && curX + hSpacing + p.width > constraints.maxWidth) {
                curY += rowH + vSpacing; curX = 0; rowH = 0
            }
            val pos = curX to curY
            curX += (if (curX == 0) 0 else hSpacing) + p.width
            rowH = maxOf(rowH, p.height)
            pos
        }
        val flowHeight = if (allPlaceables.isEmpty()) 0 else curY + rowH
        val showAllY = flowHeight + (if (showAllPlaceable != null) vSpacing else 0)
        val totalHeight = (showAllY + (showAllPlaceable?.height ?: 0)).coerceAtLeast(0)

        layout(constraints.maxWidth, totalHeight) {
            allPlaceables.take(cutIndex).forEachIndexed { i, p ->
                p.place(positions[i].first, positions[i].second)
            }
            showAllPlaceable?.place(0, showAllY)
        }
    }
}
