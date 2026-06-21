# AGENTS.md

## Purpose

This repository contains the BoardFlow Android app.

Agents working here should preserve the current user-facing design language while improving correctness, maintainability, and architectural clarity. The app has already accumulated several interconnected flows, so the main job is usually to make the existing product work more coherently rather than to reinvent it.

## Product Snapshot

BoardFlow currently supports all of the following:

- local and online BGG play logging
- offline-first local play saving
- manual reposting of unposted local plays from History
- edit and delete play flows
- play quantity, incomplete flag, and nowInStats toggle
- AI score extraction from images with Gemini (with model fallback/cycling), preceded by a local non-blocking scan quality warning for obviously dark, blurry, low-resolution, or too-far images; malformed responses automatically trigger a silent background retry — if it succeeds while the user is still on `LogPlayScreen`, a non-blocking banner offers to apply the cleaner result
- the model name used for a scan is stored on `ExtractedPlay.modelUsed` and shown below the "Raw AI response" label in the AI output card
- AI game recognition from scan: auto-identify the game using saved recognition templates (title similarity + category fingerprint matching, two-gate autoswitch: TITLE_GATE >= 0.90 or TEMPLATE_CATEGORY_GATE >= 0.75 with >= 3 category matches)
- home-screen widgets: `SessionWidget` (last played session), `DailyInsightWidget` (rotating stat insights), and `StatsWidget` (plays this month + active challenge progress); all three include a camera button that cold-starts the app into Quick Scan via `ACTION_QUICK_SCAN`
- saved player roster with aliases, optional BGG usernames, and Levenshtein fuzzy matching
- collection browsing on a single My Shelf tab with owned / wishlist / played filter dimensions, plus sleeves
- per-game sleeve exclusion (toggle individual games out of sleeve display)
- configurable sleeve manufacturer priority (Appearance settings)
- game detail drill-ins with history and player links
- expansion / sibling title detection and display in log flow; `RelatedGamesBanner` shows the first 6 related games by default with a "Show all (N)" / "Show less" toggle and `animateContentSize` smooth expand; 2–5 related titles are shown without a toggle
- record moment detection after logging (first win, new high score, win streak)
- session memory: per-play mood chips (multi-select, preset + custom) and quote capture from `PlayDetailsDialog`
- chronicle generation: AI-generated single atmospheric sentence per session using Gemini, with deterministic offline fallback; stored in `play_memories` Room table; persists independently of BGG sync; togglable via Settings > AI
- history tabs for plays, stats, and players
- signature-based deduplication of local and BGG plays
- QR code play sharing and import
- Google Sheets / Drive sync
- CSV import/merge into a sheet
- spreadsheet creation / connection
- Drive folder and QR code creation
- backup export / import
- session continuation / play-again flows
- challenge notifications via `ChallengeNotificationWorker` (WorkManager): deadline warning (3 days out), completion, and streak-broken alerts; gated on `POST_NOTIFICATIONS` permission

When changing the app, keep those flows in mind. A fix in one area often has consequences for history, roster matching, sync, or backup behavior.

## Fast Start

If you are new to the repo, do not start with a whole-codebase read. Start here:

- `README.md`
  - current product behavior, runtime storage model, major screen flows
- `app/src/main/kotlin/cz/nicolsburg/boardflow/ui/app/AppShell.kt`
  - top-level navigation, scaffold, cross-screen routing
- `app/src/main/kotlin/cz/nicolsburg/boardflow/AppViewModel.kt`
  - search, log play, history, roster, local play persistence, outbox posting, import/export, record moments
- `app/src/main/kotlin/cz/nicolsburg/boardflow/SyncViewModel.kt`
  - Google account/sheet state, collection refresh, sleeve refresh, sync log, canonical collection loading
- `app/src/main/kotlin/cz/nicolsburg/boardflow/data/CanonicalCollectionStore.kt`
  - live Room-backed store
- `app/src/main/kotlin/cz/nicolsburg/boardflow/data/BggRepository.kt`
  - BGG search, collection, play history, log/edit/delete flows
- `app/src/main/kotlin/cz/nicolsburg/boardflow/data/GoogleApiClient.kt`
  - Sheets / Drive sync

Prefer targeted inspection of those files over broad exploration unless the issue clearly spans multiple layers.

## Current Architecture

- `MainActivity.kt`
  - thin Android entry point
  - lifecycle hooks, activity-result launchers, auth wiring
  - handles `ACTION_QUICK_SCAN` from home-screen widget in both `onCreate` (cold start) and `onNewIntent` (resumed); calls `appViewModel.requestWidgetQuickScan()`
  - `launchMode="singleTop"` so widget taps call `onNewIntent` when the app is already running
- `ui/app/AppShell.kt`
  - app scaffold
  - header
  - bottom nav (5 tabs: NewPlay, History, Collection, Sync, Settings)
  - screen routing
  - cross-screen deep-link style callbacks between Collection, History, Players, and Log Play
  - consumes `pendingHistoryNavigation` requests from `AppViewModel`
  - consumes `pendingWidgetQuickScan` to navigate to the scan flow when the widget is tapped
  - header play-timer indicator (`AppHeader`, shown while `activeTimer != null`): tapping it
    opens Log Play for the timed game (clock keeps running, duration prefilled via
    `takePrefill()`); long-press shows a "Stop timer?" confirmation that calls `stopPlayTimer()`
- `auth/GoogleAuthManager.kt`
  - Google account selection / sign-in orchestration
- `core/di/AppContainer.kt`
  - lightweight manual DI container
- `core/navigation/AppRoutes.kt`
  - central route definitions
- `AppViewModel.kt`
  - game search and recent games
  - session continuation / play-again setup
  - AI extraction handoff into review; Gemini model cycling; `scanRetryResult: StateFlow<ExtractedPlay?>` — set when a background retry resolves a previously malformed scan; `acceptRetryResult()` replaces the current extracted play and re-initialises editable players; `dismissRetryResult()` discards it; `cancelBackgroundRetry()` called automatically on save or discard
  - game recognition: `GameRecognitionEngine` ranks candidates from the local collection; confirmed scans saved as `GameRecognitionHint`; two autoswitch gates (TITLE_GATE, TEMPLATE_CATEGORY_GATE)
  - recognition hint management: `saveGameRecognitionHint`, `deleteGameRecognitionHint`, `replaceGameRecognitionHint`, `clearGameRecognitionHints`
  - player recognition: `PlayerRecognitionEngine` resolves scanned names to roster players; `initEditablePlayers` applies hints (confidence ≥ 0.70) and alias matches; `savePlayerHintsFromCurrentPlay` persists mappings on successful log; `clearPlayerRecognitionHints` exposed for Settings
  - `pendingWidgetQuickScan` StateFlow consumed by AppShell to navigate to scan on widget tap
  - editable log state integration (shared between log and edit flows)
  - roster and player alias management; fuzzy matching with Levenshtein distance
  - local play history and cached BGG history merge and deduplication (signature-based); sorted by date DESC, id DESC
  - play post/edit/delete flows (local and BGG)
  - local outbox posting for unposted plays (per-play and bulk)
  - record moment detection (first win, new high score, win streak)
  - session memory and chronicle: `savePlayMemory()` persists moods/quote to `play_memories` and triggers chronicle generation; `ensureChronicleForPlay()` auto-generates when opening a play with memory but no chronicle; concurrency managed via `chronicleJobs`, `chronicleInFlightSourceKeys`, `chronicleGenerationLock`; `chroniclePendingPlayIds: StateFlow<Set<String>>` drives the `...` placeholder; `chronicleEnabled: StateFlow<Boolean>` gates all generation and display; custom mood management: `addCustomMoodIfNew`, `deleteCustomMood`, `renameCustomMood`
  - expansion / sibling title detection (`GameRelations`); `findRelatedGames` uses `isExpansionOf()` helper supporting both separator-based (`"Root: Sub"`) and space-prefix-based (`"Root Sub"`) expansion names; detects when the selected game is itself a space-separated expansion and treats its prefix as the base
  - cross-tab navigation requests (`pendingHistoryNavigation`)
  - import/export and backup restore
  - app theme and sleeve manufacturer preference state (`appTheme`, `sleevePreferredManufacturer`)
- `SyncViewModel.kt`
  - Google auth state
  - spreadsheet connection state
  - collection refresh (BGG + Sheets + sleeves, with play count backfill from history)
  - sleeve refresh and per-game exclusion state
  - CSV import/merge
  - Drive folder and QR code creation
  - full Sheets sync
  - sync log / progress state
  - silent startup collection load (gated: only runs if last sync was more than 4 hours ago via `securePrefs.lastSyncedAt`; writes `lastSyncedAt` on successful completion so the gate is honoured on subsequent startups)
- `data/`
  - `BggApiClient.kt` -- low-level BGG HTTP/XML client and sleeve scraping
  - `BggRepository.kt` -- BGG feature layer (collection, search, play CRUD, history)
  - `GoogleApiClient.kt` -- Sheets / Drive API
  - `GeminiRepository.kt` -- AI extraction (scores + game detection), model discovery, fallback cycling; debug-logged under TAG "Gemini"; sets `ExtractedPlay.modelUsed` to the winning model name and `ExtractedPlay.isMalformed = true` when JSON parsing fails
  - `ScanImageQualityAnalyzer.kt` -- local pre-Gemini image readability checks; does not persist image, player, or score data
  - `CanonicalCollectionStore.kt` -- Room-backed live source of truth (DB v10); stores canonical games, logged plays, BGG play cache, play sessions, play memories, thumbnail cache, players, challenges, game recognition hints, player recognition hints, and sleeve tracking; `getBggPlaysCache()` and `getLoggedPlays()` apply the `play_memories` overlay on read, with `parseMemoryFromNotes()` as fallback for plays with `$$mood:`/`$$quote:` lines in comments
  - `SessionMemoryJson.kt` -- `toSessionMemoryOrNull()`, `toJsonString()`, `parseMemoryFromNotes()` extension functions
  - `chronicle/` -- chronicle service pipeline: `SessionChronicleService` (plan + compose), `GeminiChronicleLineGenerator` (Gemini API, 4 retries, model fallback, 2.5 s timeout), `FallbackChronicleComposer` (deterministic offline fallback), `ChronicleLineGenerator` interface, `ChronicleRequest` and `ChronicleAiConfig` data classes
  - `BackupSerializer.kt` -- backup JSON import/export (format version 7; includes `memory` JSON per play; exports players and challenges from Room)
  - `SecurePreferences.kt` -- encrypted preferences (credentials, settings, roster, session, recognition hints, `chronicle_enabled`, `custom_moods`)
  - `QrGenerator.kt` -- QR code PNG generation and gallery save
  - `PlayShareSerializer.kt` -- play encode/decode for QR sharing
  - `BggImageCache.kt` -- thumbnail preload after sync
  - `BggCache.kt` -- file-based BGG collection cache
  - `BggPlaySync.kt` -- top-level BGG play cache refresh function
  - `CsvParser.kt` -- CSV row parsing for sync
  - `ChallengeNotificationWorker.kt` -- WorkManager worker; fires deadline (3 days out), completion, and streak-broken notifications per active challenge; gated on `POST_NOTIFICATIONS` permission; uses `challenge_notif` SharedPreferences to suppress re-fires
  - `BggSyncWorker.kt` -- WorkManager background sync worker
- `model/`
  - `Models.kt` -- all core data classes (`GameItem`, `LoggedPlay`, `Player`, `SessionContext`, `RecordMoment`, `GameRecognitionHint`, `PlayerRecognitionHint`, `GameCandidate`, ...); `ExtractedPlay` carries `isMalformed: Boolean` (parse failed, partial data) and `modelUsed: String?` (Gemini model that produced the response); `SessionMemory` carries moods, quote, `chronicleLine`, `chronicleSourceKey`, `chronicleCreatedAt`; `trimMemorySuffix()` strips `$$mood:`/`$$quote:` lines from BGG comments for display
  - `SleeveDatabase.kt` -- `SleeveManufacturer` enum, `SleeveEntry`, `SleeveDatabase` object
- `ui/`
  - screens and shared Compose UI helpers
  - `ui/widget/SessionsWidget.kt` -- `SessionGlanceWidget` (open base, all layouts) + `SessionWidget` receiver; shows last session
  - `ui/widget/DailyInsightWidget.kt` -- `DailyInsightGlanceWidget` (overrides `computeSnapshot`) + `DailyInsightWidget` receiver; shows rotating stat insights
  - `ui/widget/StatsWidget.kt` -- `StatsGlanceWidget` (overrides `computeSnapshot`) + `StatsWidget` receiver; shows plays this month, top game, and most urgent active challenge with deadline/progress; accent driven by deadline urgency

## Source Of Truth

### Live Runtime Data

The live runtime source of truth is Room via `CanonicalCollectionStore`.

It stores:

- canonical merged collection snapshot (`GameItem` records)
- local logged plays
- cached BGG play history
- session memories (`play_memories` table, keyed by play ID; applied as an overlay on read; never cleared by BGG sync; fallback parses `$$mood:`/`$$quote:` from BGG notes when no entry exists)
- players (`players` table; migrated from `SecurePreferences` on first load if Room is empty)
- challenges (`challenges` table; migrated from `SecurePreferences` on first load if Room is empty)
- game recognition hints (`game_recognition_hints` table)
- player recognition hints (`player_recognition_hints` table)
- sleeve tracking overrides (`game_sleeve_tracking` table)

### Preferences / Settings

`SecurePreferences` stores:

- BGG credentials (username, password)
- Gemini key, model endpoint, available models cache
- app theme (`app_theme`, enum name string)
- sleeve priority manufacturer (`sleeve_preferred_manufacturer`, `SleeveManufacturer` enum name)
- player roster (legacy; still written for backup compatibility; Room is authoritative at runtime)
- recent games (last 50)
- sync preferences (spreadsheet ID, sheet tab name, Google email)
- session context (active game, players, location, timestamp)
- sleeve exclusion list (game IDs)
- per-game insight key cache
- chronicle enabled flag (`chronicle_enabled`; boolean; default true)
- custom mood templates (`custom_moods`; JSON array of user-defined mood label strings)
- challenges (legacy; still written for backup compatibility; Room is authoritative at runtime)

Do not move live collection/history state back into large JSON blobs in preferences.

### Backup Format

`BackupSerializer` owns import/export JSON (format version 7).

Backups can contain:

- collection snapshot (full `GameItem` array under `collectionSnapshots.__canonical_collection__`)
- local logged plays (including embedded `memory` JSON object per play)
- cached BGG plays (including embedded `memory` JSON object per play)
- player roster (read from Room via `CanonicalCollectionStore.getPlayers()`)
- challenges (read from Room via `CanonicalCollectionStore.getChallenges()`)
- recent games
- sleeve exclusions
- AI game recognition templates
- AI player recognition templates
- settings (theme, spreadsheet config, sleeve manufacturer preference)
- optionally sensitive data (BGG password, Gemini API key)

Import is selective: only keys present in the backup JSON are applied; missing keys do not overwrite existing values.

## Key Product Flows To Understand

### Search -> Log Play

- `NewPlayScreen` loads recent games and local collection-backed search first
- if no local collection result matches, search falls back to BGG XML search
- BGG search covers both base games and expansions (`type=boardgame,boardgameexpansion`)
- BGG search results are sorted alphabetically; the list has no hard result cap
- search is debounced (800ms after typing stops); local collection is checked first; if no local match, BGG is called with `exact=1` first, then `exact=0` as a fallback if exact returns nothing
- `isBggSearchActive` in `AppViewModel` prevents `loadCollection`, `updateFromCollection`, and `loadRecentGames` from overwriting `_searchResults` while a BGG search result set is displayed; the guard clears when the user selects a game or clears the query
- lists longer than 20 items show a draggable fast-scroll bar on the right edge (`NewPlayScreen.FastScrollBar`): amber pill thumb, animated opacity (idle 20% / scrolling 65% / dragging 80%), floating letter bubble that leads the thumb position, and haptic feedback (`HapticFeedbackType.TextHandleMove`) per letter section change
- selected games move into `LogPlayScreen`
- session context may prefill players/location
- AI extraction may prefill players/scores
- `ScanScreen` runs `ScanImageQualityAnalyzer` before sending an image to Gemini; poor scans show a non-blocking "This scan may be hard to read." warning with a reason and "Use anyway" / "Retake" actions
- expansion / sibling titles detected from name patterns and shown alongside base game
- the search field has a trailing camera (`CameraAlt`) icon button that triggers the quick scan flow via `onScanQuick`; tapping it exits any active correction mode and navigates directly to `ScanScreen`

### Quick Scan Correction Flow

When AI recognition picks the wrong game after a scan, the user can tap "Choose another game" from the banner in `LogPlayScreen`:

1. `AppViewModel.enterQuickScanCorrectionMode()` sets `_quickScanCorrectionMode = true`
2. `AppShell` navigates back to `NewPlayScreen` without clearing `_extractedPlay` or `_editablePlayers`
3. `AppViewModel.selectGame()` short-circuits its normal clear logic while correction mode is active
4. When the user picks a replacement game, `applyDetectedGameCorrection()` reads the preserved `_extractedPlay`, calls `initEditablePlayers()` with the original extracted players, applies the new game, and clears correction mode
5. `AppShell` then navigates directly to `LogPlayScreen` — no new scan

If the user presses back from `NewPlayScreen` while in correction mode, `exitQuickScanCorrectionMode()` is called and they remain on `NewPlayScreen` (back does not exit to the previous screen in this state).

### Log Play -> Local / BGG

- if online with credentials, the app can post to BGG
- if offline or posting is unavailable, the play can still be saved locally
- extra related games may post separately; failures there can leave local follow-up plays
- local unposted plays are intentionally user-controlled from History rather than silently auto-posted on startup
- tapping X or back when Log Play has any data (unsaved changes, editable players, or an extracted play) shows a discard confirmation dialog; the check reads `AppViewModel` StateFlow values directly to avoid a one-frame `LaunchedEffect` delay
- after posting, `AppViewModel` detects record moments (first win, new high score, win streak) by comparing against play history snapshot

### Play Deduplication (Merge Logic)

- `AppViewModel.historyPlays` is the merged, deduplicated derived flow
- two dedup strategies run in sequence:
  - **signature key**: full play hash (game, date, players with scores/colors, location, comments)
  - **history correlation key**: lighter hash (game, date, players, colors) used when score info may differ
- local plays that match a fetched BGG play are marked as posted; truly orphaned local plays that were deleted on BGG are pruned

### History

- History merges local logged plays with cached BGG plays
- local-only plays can be deleted locally
- BGG plays can be deleted remotely
- edit flow updates local state and remote BGG when needed
- History includes:
  - `Plays`
  - `Challenges`
  - `Stats` -- includes per-player stat profiles and a `HeadToHeadSection` with `BoardFlowPickerField` player selectors
  - `Players`
- the Plays tab also acts as the outbox surface for unposted local plays

### Collection

- collection data is loaded through sync and propagated from the canonical Room snapshot
- tabs are `My Shelf`, `Sleeves`, and `Stats`. Membership (owned / wishlist / played) is no
  longer separate tabs — it lives in the `My Shelf` filter sheet as two single-select
  dimensions:
  - **Show** (`OwnershipFilter`): `Owned` (default) / `Wishlist` / `Played, not owned` / `Any`
  - **Play status** (`PlayStatusFilter`): `Any` (default) / `Played` / `Unplayed`
  plus the `Players` / `Best for` / `Recommended for` player-count filters
  (`bestForMatches` / `recommendedForMatches` delegate to `playerCountMatches`;
  `isPlayedGame` keys off play history `gameId` plus `numPlays > 0`)
- played-but-not-owned games are cached as `GameItem`s during sync via
  `SyncViewModel.enrichPlayedGames` (using `BggApiClient.fetchThingDetails`), so they survive
  sync (`mergeGameItems` is additive), surface under `Show -> Played, not owned`, are
  searchable in Log Play (`AppViewModel.logPlayPool`), and resolve as game info from a play.
  Sleeve surfaces filter on `isOwned`, so they ignore played-only games.
- game detail dialog is a major cross-link hub into History and Players
- sleeve display respects per-game exclusion toggles

### Sync

- Sync screen is the user-facing operational hub for:
  - BGG readiness
  - Google readiness
  - sheet connection
  - refresh collection (BGG + Sheets + sleeves; backfills play counts from cached history)
  - refresh sleeve sizes
  - sync to Sheets
  - CSV import/merge
  - create Drive folders / QR codes (with optional gallery save)
  - create/connect spreadsheet
  - review sync log

### Settings

- manages BGG credentials
- manages Google sheet connection access points
- manages Gemini configuration (key, model endpoint, model discovery)
- manages theme (Light, Dark)
- manages sleeve manufacturer priority (`SleeveManufacturer`; persisted in `SecurePreferences`, exposed via `AppViewModel.sleevePreferredManufacturer`; used in `GameDetailDialog` via `SleeveEntry.preferredFor()`)
- manages import/export (backup includes recognition templates since format v3)
- can clear cached collection
- AI section: view / edit / delete individual recognition templates; bulk-clear all templates with confirmation
- AI section: Chronicles toggle (on by default); turning it off cancels all in-flight generation jobs and hides chronicle cards throughout the app
- AI section: Mood Templates manager (`CustomMoodsDialog`) — view, rename (`EditMoodDialog`), and delete custom moods saved during session memory entry

### AI Game Recognition

#### Engine (`data/GameRecognitionEngine.kt`)

- stateless; `rankCandidates(extractedPlay, collection, hints)` scores every entry and returns up to 5 ranked `GameCandidate` objects
- four scoring signals:
  1. **Title similarity** — Levenshtein distance + containment, up to 1.0; primary signal when a title is detected
  2. **Category-name-in-game-name** — legacy low-weight boost (up to 0.2) when detected category strings appear in the game name
  3. **Template category overlap** — compares detected scoring categories against `hint.normalizedCategories`; requires `>= MIN_TEMPLATE_CATEGORY_OVERLAP` (3) for a strong signal; weight is 0.75 / 0.50 / 0.30 depending on title strength so category-only sheets can still produce a high-confidence match
  4. **Saved title bonus** — +0.15 if the detected title matches a title stored in the hint from a prior confirmation
- `GameCandidate` carries `score`, `matchReason`, `primarySignal` (`"title"` or `"category-template"`), and `templateOverlap`

#### Autoswitch Gates (`AppViewModel.extractScores`)

Two gates decide whether to auto-switch the active game without user input:

- **TITLE_GATE**: `score >= 0.90`, `primarySignal != "category-template"`, Gemini confidence >= 0.95, score margin over 2nd candidate >= 0.15
- **TEMPLATE_CATEGORY_GATE**: `score >= 0.75`, `primarySignal == "category-template"`, `templateOverlap >= 3`, Gemini confidence >= 0.95, margin >= 0.15

If a gate fires, `AppViewModel` calls `applyDetectedGame()` silently and emits `ScanRecognitionResult.AutoSwitched`. If neither gate fires but candidates exist, they are exposed via `_gameCandidates` for the user to pick from.

#### Recognition Result State (`ScanRecognitionResult`)

Sealed class emitted to `_scanRecognitionResult` after each scan:

- `AutoSwitched(gameName)` — a gate fired; game was silently switched; `ScanResultBanner` in `LogPlayScreen` shows a success message
- `NoCollectionMatch(detectedTitle)` — Gemini detected a title but nothing in the collection matched; `ScanResultBanner` shows an info message with a "Choose another game" action
- `LowConfidence` — Gemini could not detect the game or confidence was below threshold; no banner shown

#### Candidate Suggestion Flow

When neither gate fires but `_gameCandidates` is non-empty, `LogPlayScreen` renders `GameSuggestionBanner` showing the top candidate with confidence and evidence text. From there:

- `AppViewModel.acceptGameSuggestion(game)` — saves a `GameRecognitionHint` for the confirmed game, applies it via `applyDetectedGame()`, and clears `_gameCandidates`
- `AppViewModel.dismissGameSuggestion()` — clears `_gameCandidates` without changing the active game
- `AppViewModel.dismissScanRecognitionResult()` — clears `_scanRecognitionResult` and exits correction mode if active
- `scanStartedWithGame: StateFlow<Boolean>` — true when the scan was started with a game already selected (id != 0); passed to the banner composables as `hasPreselectedGame` to control whether the "Choose another game" action is offered
- `clearExtractedPlay()` — public helper that nulls `_extractedPlay`; called by screens that need to discard scan data without clearing the full log play flow

`LogPlayScreen` also shows a passive `detectedGameHint` label ("AI detected: X") inside `CompactGameHeader` when Gemini identified a title different from the current game and no actionable banner is already showing.

#### Gemini Prompt and Response (`data/GeminiRepository.kt`)

The extraction prompt explicitly requests game identification fields alongside player scores:

- `detectedGameTitle` — best-guess game name from visible text, logos, or scoring structure; `null` if indeterminate
- `detectedGameConfidence` — float 0.0–1.0 reflecting certainty about the title
- `detectedScoringCategories` — list of scoring column/row header labels visible on the sheet
- `gameDetectionEvidence` — one sentence describing the visual cue used for identification

`parseGeminiResponse()` maps these to `ExtractedPlay` fields. Date handling: `null` or the literal string `"null"` from the model are both treated as absent (the app uses today's date); the model is instructed not to invent a date. On success the returned `ExtractedPlay` also carries `modelUsed` (the model that returned HTTP 200) and `isMalformed = false`. If JSON parsing throws, partial player extraction is attempted via regex, `isMalformed` is set to `true`, and the raw text is prefixed with a warning emoji.

Debug logging (`TAG = "Gemini"`) covers: start time, per-attempt model and HTTP status, total elapsed time, model fallback switches, and parse results.

#### Malformed Response Retry

When `extractScores` receives an `ExtractedPlay` with `isMalformed = true`, `AppViewModel` immediately launches a background coroutine (stored as `_retryJob`) that silently re-calls Gemini with the same image file and model parameters. The loading indicator is **not** shown during the retry — the user lands on `LogPlayScreen` with the partial data and can start editing. If the retry returns `isMalformed = false`, `_scanRetryResult` is set and `LogPlayScreen` shows a `ScanRetryBanner` ("Scan retry got a cleaner result. Apply it?") with "Apply update" / "Dismiss" buttons. Accepting calls `acceptRetryResult()` which replaces `_extractedPlay` and re-initialises editable players. The retry job is cancelled (and `_scanRetryResult` cleared) by `cancelBackgroundRetry()`, which is invoked from `clearExtractedPlay()` and both success paths in `postPlay()`, ensuring the background work stops when the user saves or discards the play.

#### Scan Image Quality (`data/ScanImageQualityAnalyzer.kt`)

Before Gemini extraction, captured or gallery-picked score-sheet images are checked locally for obvious readability issues:

- too dark
- too blurry
- too low resolution
- likely too far away / too much empty border

Good images continue to Gemini immediately. Poor images show a warning in `ScanScreen` with the first available human-readable reason, but the user can still choose "Use anyway" and continue the existing extraction flow. "Retake" returns to the camera/gallery choice. Keep this preflight local-only and separate from game recognition, player recognition, and Gemini prompt/response logic.

#### Hint Lifecycle (Game)

- A `GameRecognitionHint` stores the game's `objectId`, normalized title strings, normalized scoring category strings, `confirmedAt` timestamp, and `timesConfirmed` count
- `SecurePreferences.saveGameRecognitionHint()` merges with any existing hint for the same game (accumulates titles and categories, increments counter)
- `replaceGameRecognitionHint()` does a full replace without merging (used by the Settings edit dialog)
- `deleteGameRecognitionHint(gameObjectId)` removes one entry; `clearGameRecognitionHints()` removes all
- Hints are included in backup export/import (format v3 `recognitionHints` array; import bulk-replaces)
- Settings > AI shows the hint count, allows viewing (tap), editing categories or deleting (long press), and bulk clearing with confirmation

### AI Player Recognition (`data/PlayerRecognitionEngine.kt`)

`PlayerRecognitionEngine` (stateless object) resolves a raw scanned player name to a roster `Player` using a three-tier lookup:

1. **Saved hints** (`PlayerRecognitionHint`) — keyed by `scannedNameNormalized`; confidence = `0.70 + timesConfirmed * 0.05` capped at `0.95`; if two hints for the same scanned name point to different players with close `timesConfirmed` (second ≥ half of first), the result is flagged ambiguous (confidence 0.55)
2. **Exact alias / display name** — case-insensitive; confidence 1.0
3. **Fuzzy (Levenshtein)** — threshold = `max(2, name.length / 3)`; confidence = `1 - dist / (name.length + 1)`

`AppViewModel.initEditablePlayers()` applies resolution for each scan-extracted player: if match confidence ≥ 0.70 and source ≠ "fuzzy", the scanned name is replaced with `player.displayName`. Fuzzy matches are not auto-applied. Raw scanned names are stored in `_originalScannedNames`.

`AppViewModel.savePlayerHintsFromCurrentPlay()` is called on both success paths in `postPlay`. It compares each final editable player name against its original scanned name; for any pair where a roster player is found for the final name and `normalizeForRecognition(originalScanned) ≠ normalizeForRecognition(rosterDisplayName)`, a `PlayerRecognitionHint` is saved (or incremented via `savePlayerRecognitionHint`).

`SecurePreferences.savePlayerRecognitionHint()` upserts by `(scannedNameNormalized, confirmedRosterPlayerId)` pair, incrementing `timesConfirmed` on collision.

Settings > AI section shows the count of saved player hints and a "Clear player recognition hints" action.

### QR Play Sharing

- `PlayShareSerializer` encodes a `LoggedPlay` for QR
- `QrGenerator` produces a QR PNG and can save to the device gallery
- `QrPlayImportScreen` lets users scan/paste a QR play and confirm import
- import lands in `AppViewModel.pendingImportedPlay`; user confirms before saving locally

### Record Moments

- `AppViewModel.captureHistorySnapshot()` snapshots play history before posting
- `AppViewModel.detectRecord()` compares the new play against the snapshot:
  - **FirstWin**: first-ever win for this game by this player
  - **NewHighScore**: score exceeds previous best for this player in this game
  - **WinStreak**: player has won 2 or more consecutive plays of this game
- result surfaced in `LogPlayScreen` after a successful post

## What Usually Matters

- preserve the current visual hierarchy unless the user explicitly asks for a redesign
- keep merge logic source-aware:
  - BGG owns identity, stats, BGG links, ownership flags, BGG play count, and remote history
  - Google Sheets owns spreadsheet-specific/manual sheet values and links
  - sleeve refresh owns sleeves only
- full sync should update the canonical merged snapshot once at the end
- local/offline history should not mutate canonical collection state
- local unposted plays should remain visible and user-controlled
- BGG XML search outside the loaded collection requires the XML API token and should fail quietly to an empty result state if missing/rejected
- player matching should stay explicit unless a match is truly exact
- sleeve exclusions are per-game and stored in `SecurePreferences`; respect them in both display and export

## Expectations For Changes

- keep the existing visual layout and design language unless explicitly asked to redesign
- prefer small structural fixes over broad rewrites
- keep `MainActivity` thin
- keep navigation concerns in `ui/app` or `core/navigation`
- keep business logic out of composables
- prefer `StateFlow` and unidirectional data flow
- avoid introducing new overlapping sources of truth
- do not reintroduce deprecated Google sign-in APIs
- if a task is localized, stay within that feature area unless the bug clearly crosses layers

## BGG Flow Notes

- both authenticated and unauthenticated collection flows exist
- play posting/edit/delete is authenticated; uses cookie-based session persistence in `BggApiClient`
- retry and error handling exist in the repository layer
- BGG XML search outside the local collection requires `BGG_XML_API_TOKEN`
- if BGG XML token-based search is unavailable, fail quietly to empty results instead of noisy user-facing errors where possible
- `BggRepository.searchGames` accepts an `exact: Boolean` parameter that maps to `exact=1` / `exact=0` in the BGG XML API URL; `AppViewModel.searchGames` cascades exact=1 then exact=0 automatically
- search covers `type=boardgame,boardgameexpansion` so expansions appear alongside base games in external search results

## History / Roster Notes

- saved roster players are distinct from arbitrary logged names
- history rows should show all logged players, even if names are similar
- general stats may treat unsaved names differently from roster views
- roster-oriented views should stay roster-based
- editable player rows should use stable UI identity and survive configuration changes where practical
- fuzzy player matching uses Levenshtein distance (threshold ~1/3 of input length); matches shown as suggestions, not auto-applied
- `PlayerResultEditorCard` shows a `MatchedPlayerChip` ("Matched [name]") when the typed name resolves to an exact roster player; the chip is styled as a `SuggestionChip` at the same size as fuzzy suggestions
- the name field is only auto-renamed to `player.displayName` when the field is **not** focused; if the user is actively typing, the rename is deferred until they leave the field (`onFocusChanged` guard on the `LaunchedEffect(exactMatch?.id, nameFocused)`)
- player cards collapse only when the user explicitly adds another player (`collapseCompletePlayers()` called from `addEditablePlayer` and `addPlayerFromRoster`); there is no reactive auto-collapse on field completion

## Sleeve Notes

- `SleeveManufacturer` enum lives in `SleeveDatabase.kt` (7 values: AUTO, TLAMA_DIAMOND, PALADIN, ULTRA_PRO, SAPPHIRE, SLEEVE_KINGS, ARCANE_TINMEN)
- `SleeveEntry.preferredFor(manufacturer)` returns the (brand, productName) pair for the chosen brand, falling back to the best available if that brand does not carry this size
- the user-selected manufacturer is read from `SecurePreferences.sleevePreferredManufacturer`, exposed as `AppViewModel.sleevePreferredManufacturer: StateFlow<SleeveManufacturer>`
- `GameDetailDialog` reads it at composition time and passes it through `SleevesBlock` -> `SleevesSection`
- per-game exclusions are stored in `SecurePreferences` as a `Set<String>` of game objectIds; managed via `SyncViewModel.toggleSleeveGameExclusion` / `excludeAllSleeveGames` / `includeAllSleeveGames`

## UI Conventions

- preserve the current screen hierarchy and tab layout
- prefer extracting small reusable helpers when a screen starts carrying duplicated framework glue
- avoid unsafe `!!` access in composables when nullable state can be handled cleanly
- keep screen parameters minimal and explicit
- use saveable state for meaningful in-progress screen state
- keep user-facing strings and docs in UTF-8, but prefer plain ASCII punctuation when practical
- be careful with PowerShell bulk replacements; they can introduce mojibake like `Ã‚Â·`, `Ã¢â‚¬Â¦`, or `ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦`
- if encoding corruption appears in working changes, fix it before continuing

### Modal Pattern Example

```kotlin
@Composable
fun SpreadsheetConnectModal(
    currentSheetName: String?,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
    onCreateNew: (() -> Unit)? = null
) { ... }
```

## Build / Verification

Before finishing substantial changes, run:

```sh
./gradlew.bat :app:compileDebugKotlin
```

Also use this when startup behavior, resources, or packaging may be affected:

```sh
./gradlew.bat :app:assembleDebug
```

If you only changed docs or a very small behavior fix, compile is usually enough.

## Important Runtime Note

Google sign-in and Google Sheets / Drive access depend on external Firebase / Google Cloud OAuth configuration. A successful compile does not guarantee runtime sign-in success if SHA fingerprints or OAuth client setup are wrong.

## Dependency Notes

Current notable choices:

- Java 17 / Kotlin JVM target 17
- Compose + Material 3
- Navigation Compose
- Credential Manager + Google Identity
- OkHttp
- CameraX
- Coil
- Room

Avoid adding Retrofit / Moshi back unless there is a clear need; they were removed as unused.

## Refactor Guidance

If continuing modernization, the most valuable current targets are:

- split `AppViewModel` and `SyncViewModel` into smaller feature-oriented state holders
- move models out of the catch-all `Models.kt` into more focused files
- replace heuristic history reconciliation with stronger explicit sync identity
- further reduce duplicated collection state / bridging between view models
- add clearer UI state models for mixed loading/data/error screens
- improve Google sign-in diagnostics with device-tested logging if runtime issues continue

When in doubt, inspect the targeted feature files first and avoid a whole-repo read unless the bug really spans sync, storage, and UI together.
