# BoardFlow

BoardFlow is an Android app for tracking board game plays with BoardGameGeek, Google Sheets / Drive, local offline history, roster-aware player stats, sleeve data, and AI-assisted score extraction.

It is built with Jetpack Compose and uses Room as the live local source of truth for collection data, local play history, and cached BGG play history.

## What The App Does

BoardFlow combines several related workflows in one app:

- search your collection or BGG for a game to log
- continue a recent session or use "play again" flows
- log a play directly to BGG when online
- save a play locally when offline
- post saved local plays later from History via an outbox-style "Unposted plays" section
- scan a score sheet photo and extract players / scores with Gemini, with a local preflight warning for obviously dark, blurry, low-resolution, or too-far images
- AI game recognition from scan: auto-identify the game from score sheet layout using saved recognition templates; auto-switch when confidence is high enough, or present a ranked suggestion banner for the user to confirm
- AI player recognition from scan: scanned player names are auto-resolved to roster players using saved hints, then aliases, then fuzzy matching; ambiguous hints are not auto-applied; confirmed mappings are saved as hints after each successful play log
- launch Quick Scan directly from the home-screen widget
- manage a saved player roster with aliases and optional BGG usernames
- review play history, aggregate stats, and saved-player activity
- edit or delete logged plays
- browse your collection, wishlist, and sleeve coverage
- exclude specific games from sleeve sync and display
- share a logged play as a QR code; import a play from a QR code
- sync your collection to a Google Sheet
- create or connect a Google spreadsheet
- import a CSV file into a sheet
- create Drive folders and QR-related assets through the Google sync pipeline
- export and import app data backups
- detect record moments after a play (first win, new high score, win streak)
- gamification layer: rarity-tiered insights, game mastery levels, period reviews, and animated observation cards

## Main User Flows

### 1. Log Play

Screens involved:

- `ui/search/NewPlayScreen.kt`
- `ui/scan/ScanScreen.kt`
- `ui/review/LogPlayScreen.kt`

Flow:

1. Pick a game from recent games, local collection search, or BGG search.
2. Optionally continue the last session for the same game or use a "play again" flow from History / Collection.
3. Optionally scan a scoresheet image and let Gemini prefill players and scores.
4. Review and edit the play in the compact card-based Log Play sheet.
5. Post to BGG if online and authenticated, or save locally for later if offline / posting fails.

Notable behavior:

- game search prefers the local loaded collection, then falls back to BGG XML search
- BGG search covers both base games and expansions (`type=boardgame,boardgameexpansion`)
- BGG search results are sorted alphabetically; lists over 20 items show a draggable fast-scroll bar with a floating letter bubble and haptic feedback per section
- search is debounced (800ms); local collection is checked first; if no match, BGG is queried with `exact=1` then `exact=0` as a fallback
- background collection loads never overwrite an active BGG search result set; the guard is cleared when the user selects a game or clears the query
- session context (game, players, location) is persisted across app restarts for up to 4 hours
- log form tracks unsaved changes; tapping X or back when any data is present (unsaved changes, editable players, or extracted play) triggers a discard confirmation dialog
- score-sheet images are checked locally before Gemini extraction; poor scans show a non-blocking warning with a reason and let the user continue anyway or retake
- if AI recognition identifies the wrong game, the user can tap "Choose another game" from the scan result banner; the app enters quick scan correction mode (`_quickScanCorrectionMode`), preserves the extracted players/scores, navigates to NewPlayScreen for re-selection, then returns to LogPlay with all data intact
- player rows are keyed UI items with shared editing UI between log and edit flows
- matched roster players are explicit; non-exact fuzzy matches require user confirmation
- scanned player names are resolved at `initEditablePlayers` time using: (1) saved `PlayerRecognitionHint` entries with confidence ≥ 0.70, (2) exact alias match — fuzzy matches are not auto-applied; hints are saved on successful play log when the original scanned name differs from the final resolved display name
- plays support quantity (multi-game sessions), incomplete flag, and nowInStats toggle
- expansions / sibling titles are detected from name patterns and shown alongside the base game

### 2. Edit Existing Play

Screen involved:

- `ui/history/HistoryScreen.kt` (`EditPlayDialog`)

Behavior:

- opens from History play details
- uses the same compact player editor visual language as Log Play
- preserves fields on configuration changes with saveable state
- stays open when save fails so the user can correct and retry
- updates the local Room record, and updates BGG too when the play is already posted there

### 3. History

Screen involved:

- `ui/history/HistoryScreen.kt`

Tabs:

- `Plays`
- `Stats`
- `Players`

Current behavior:

- local plays and cached BGG plays are merged for display; deduplication uses a signature-based matching strategy to avoid showing the same play twice
- local-only plays can be deleted locally
- BGG-backed plays can be deleted from BGG and pruned from cache
- an `Unposted plays` card in Plays acts as the manual outbox for local plays not yet posted to BGG; supports per-play posting and bulk sync
- stats support date filters, a selectable source scope (all logged plays or only plays marked Count in stats), and a narrative Table Brief before deeper metrics
- players tab is roster-only and sorts by most recent saved-player activity
- history rows always show all logged players and do not collapse same/similar names into one row
- unsaved logged names can still be grouped as `Unknown` in general stats where appropriate
- plays can be shared as QR codes and imported from QR

### 4. Collection

Screen involved:

- `ui/collection/CollectionScreen.kt`
- `ui/collection/GameDetailDialog.kt`

Tabs / areas:

- `Owned`
- `Wishlist`
- `Sleeves`

Behavior:

- collection data comes from the canonical Room snapshot managed by sync
- game detail dialog links collection data with history and player insights
- collection-to-history and collection-to-player deep links are supported
- sleeve data is merged from BGG, scraping, and local exclusions; individual games can be excluded from sleeve display
- the `Owned` tab strictly means owned games

### 5. Sync

Screen involved:

- `ui/sync/SyncScreen.kt`
- `ui/sync/SpreadsheetModal.kt`

Behavior:

- manages readiness for BGG, Google account, and spreadsheet connection
- supports refreshing BGG collection
- supports refreshing sleeve sizes
- supports full Google Sheet sync
- supports creating a spreadsheet from BGG
- supports connecting an existing spreadsheet
- supports CSV import/merge into a sheet
- supports creating Drive folders and uploading / downloading QR codes
- shows a sync log with summary + detailed dialog
- can clear local sync log state
- collection refresh automatically backfills play counts from cached BGG play history when BGG play count is missing

### 6. Settings

Screen involved:

- `ui/settings/SettingsScreen.kt`

Sections:

- `Accounts`
- `Appearance`
- `AI`
- `Data`

Behavior:

- manage BGG username/password
- manage Google account and spreadsheet connection
- set Gemini API key and configurable model endpoint
- discover and select from available Gemini models
- switch app theme (Light, Dark)
- choose whether History stats use all logged plays or only plays marked Count in stats
- choose priority sleeve manufacturer shown first in sleeve recommendations
- export backup JSON, optionally including sensitive data
- import backup JSON
- clear cached collection data
- AI section shows saved recognition template count; templates can be viewed (tap), edited or deleted (long press), and bulk-cleared with confirmation
- AI section shows saved player recognition hint count; hints can be bulk-cleared with confirmation

## Data Model And Storage

### Live Local Source Of Truth

The live runtime store is Room via:

- `data/CanonicalCollectionStore.kt`

It stores:

- canonical merged collection snapshot (`GameItem` records)
- local logged plays
- cached BGG play history

### Preferences / Secrets

`data/SecurePreferences.kt` stores:

- BGG credentials (username, password)
- Gemini configuration (API key, model endpoint, available models cache)
- app theme and sleeve manufacturer priority
- recent games (last 50)
- player roster (display names, aliases, BGG usernames)
- sync-related preferences (spreadsheet ID, sheet tab name, Google email)
- session context (active game, players, location, timestamp)
- sleeve exclusion list (game IDs excluded from sleeve display)
- per-game insight key cache
- AI game recognition templates (`GameRecognitionHint` list: normalized titles, category fingerprints, confirmation count)
- AI player recognition hints (`PlayerRecognitionHint` list: normalized scanned name, confirmed roster player ID, display name, timesConfirmed, lastConfirmedAt)

### Import / Export

`data/BackupSerializer.kt` handles backup JSON import/export (current format version 3).

Backups can include:

- collection snapshot (full `GameItem` array)
- local logged plays
- cached BGG plays
- player roster
- recent games
- sleeve exclusions
- AI game recognition templates (`recognitionHints` array)
- settings (theme, spreadsheet config, sleeve preference)
- optionally sensitive data (BGG password, Gemini API key)

Import is selective: only fields present in the backup JSON are applied; missing fields do not overwrite existing values. Recognition templates from a backup fully replace the existing templates on device (bulk replace, not merge).

## Architecture

Primary entry points:

- `MainActivity.kt`
- `ui/app/AppShell.kt`

Core modules:

- `auth/GoogleAuthManager.kt`
- `core/di/AppContainer.kt`
- `core/navigation/AppRoutes.kt`
- `AppViewModel.kt`
- `SyncViewModel.kt`

High-level ownership:

- `MainActivity`
  - Android lifecycle and activity-result plumbing
  - handles `ACTION_QUICK_SCAN` intent from home-screen widget (cold start and `onNewIntent`)
- `AppShell`
  - navigation graph, scaffold, header, and bottom tabs
  - cross-screen deep-link routing (Collection -> History filtered by game, Collection -> Players filtered by player, etc.)
  - consumes `pendingWidgetQuickScan` to navigate directly to the scan flow when the widget is tapped
- `AppViewModel`
  - game search and recent games
  - log play state, session continuation, play-again flows
  - editable player state (shared between log and edit)
  - player roster management (aliases, BGG usernames, fuzzy matching with Levenshtein distance)
  - local play history and cached BGG play history merge and deduplication
  - play posting, editing, and deletion (local and BGG)
  - manual unposted play outbox (per-play and bulk)
  - AI extraction (Gemini) handoff and result handling
  - game recognition engine (`GameRecognitionEngine`) and hint management (save, delete, replace, clear)
  - player recognition engine (`PlayerRecognitionEngine`) and hint management (save on successful log, clear); resolves scanned names to roster players at `initEditablePlayers` time
  - session context persistence (active game / players / location)
  - record moment detection (first win, new high score, win streak)
  - expansion / game-relation detection
  - import/export
  - app theme and sleeve manufacturer preference
- `SyncViewModel`
  - Google account state
  - spreadsheet connection
  - collection refresh (BGG + Sheets + sleeves merge)
  - sleeve refresh and per-game exclusion management
  - Google Sheet sync
  - CSV import
  - Drive folder and QR code creation
  - sync log and progress state
  - silent startup collection load (skipped if last sync was within 4 hours; writes `lastSyncedAt` on completion so the gate is respected on subsequent startups)
- `data/`
  - BGG API and scraping (`BggApiClient`, `BggRepository`)
  - Google APIs (`GoogleApiClient`)
  - AI extraction (`GeminiRepository`)
  - local scan image quality preflight (`ScanImageQualityAnalyzer`)
  - Room persistence (`CanonicalCollectionStore`)
  - backup serialization (`BackupSerializer`)
  - QR code generation and play sharing (`QrGenerator`, `PlayShareSerializer`)
  - image preloading (`BggImageCache`)

## Package Layout

Main source root:

```text
app/src/main/kotlin/cz/nicolsburg/boardflow/
  AppViewModel.kt
  MainActivity.kt
  SyncConfig.kt
  SyncViewModel.kt
  auth/
    GoogleAuthManager.kt
  core/
    di/
      AppContainer.kt
    navigation/
      AppRoutes.kt
  data/
    BackupSerializer.kt
    BggApiClient.kt
    BggCache.kt
    BggImageCache.kt
    BggPlaySync.kt
    BggRepository.kt
    CanonicalCollectionStore.kt
    CsvParser.kt
    GeminiRepository.kt
    GoogleApiClient.kt
    PlayShareSerializer.kt
    QrGenerator.kt
    ScanImageQualityAnalyzer.kt
    SecurePreferences.kt
  model/
    Models.kt          (BggGame, PlayerResult, LoggedPlay, GameItem, Player, ...)
    SleeveDatabase.kt  (SleeveManufacturer enum, SleeveEntry, SleeveDatabase object)
  ui/
    app/
      AppShell.kt
    collection/
      CollectionScreen.kt
      GameDetailDialog.kt
      GameUiCommon.kt
      SleevesScreen.kt
    common/
      BoardFlowCameraUi.kt
      BoardFlowIcons.kt
      BoardFlowMotion.kt
      BoardFlowUi.kt
      CornerCloseStrip.kt
      GameBackdrop.kt
      GameSearchField.kt
      ModifierExtensions.kt
      PlayerResultEditorCard.kt
      ScreenTabRow.kt
    history/
      HistoryScreen.kt
      InsightStripCard.kt
      PlayStatsHelpers.kt
      PlayStatsTab.kt
      QrPlayImportScreen.kt
    players/
      PlayersScreen.kt
    review/
      LogPlayScreen.kt
    scan/
      ScanScreen.kt
    search/
      NewPlayScreen.kt
    settings/
      SettingsScreen.kt
    sync/
      SpreadsheetModal.kt
      SyncScreen.kt
    theme/
      Theme.kt
      Spacing.kt
    widget/
      QuickScanWidget.kt
```

## UI Surface Inventory

For a screen-by-screen map of dialogs, popups, bottom sheets, cards, dropdowns, banners, lists, and related UI surfaces, see [`docs/UI_SURFACES.md`](docs/UI_SURFACES.md).

## Gamification System

For the full reference on rarity tiers, insight categories, mastery levels, animations, and period reviews, see [`docs/GAMIFICATION.md`](docs/GAMIFICATION.md).

## External Integrations

### BoardGameGeek

Used for:

- collection refresh (authenticated and unauthenticated flows)
- BGG XML game search outside the loaded collection
- play history fetch / refresh
- play logging, editing, and deletion
- sleeve-related metadata inputs

Notes:

- play posting/editing/deletion uses authenticated flows with cookie-based session persistence
- BGG XML search outside the local collection requires `BGG_XML_API_TOKEN`
- when token-backed search is unavailable, the app fails quietly to an empty result state

### Google Identity / Sheets / Drive

Used for:

- account selection
- Drive / Sheets authorization
- spreadsheet creation / connection
- pushing collection data to Sheets (create/update by objectId)
- CSV import/merge
- Drive folder creation per game
- QR code upload/download

### Gemini

Used for:

- AI-assisted score extraction from images

The app supports:

- user-provided Gemini API key
- configurable model endpoint
- model discovery and automatic fallback/cycling when a model is unavailable (503/429)
- session-scoped fallback model: when the repository switches to a fallback model mid-scan, that model is reused for subsequent scans for up to 5 minutes; after that (or on next app launch) the user's configured model is used again

Before an image is sent to Gemini, `ScanImageQualityAnalyzer` runs local-only checks for obvious readability problems: dark images, blur, low resolution, and likely too much empty border / too-far framing. Poor scans show "This scan may be hard to read." with a reason, plus "Use anyway" and "Retake"; good scans continue directly to extraction.

## UX / Product Notes

Current app behavior intentionally includes:

- manual user-controlled posting of saved local plays from History instead of silent auto-post on startup
- roster-aware but explicit player matching; fuzzy matches (Levenshtein distance) shown as suggestions, not auto-applied
- preserved local history even when offline
- shared compact player editing UI between log and edit flows
- history navigation from Collection and player-centric screens
- stats and players views that are based on saved roster entities where appropriate
- record moment detection (first win, new high score, win streak) surfaced immediately after logging
- expansion/sibling title grouping driven by name pattern heuristics

## Build

From repo root:

```sh
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/board-flow-debug.apk
```

Install on a connected device/emulator:

```sh
./gradlew.bat :app:installDebug
```

## Configuration

### Required / common app settings

Set in Settings as needed:

- BGG username
- BGG password
- Gemini API key
- Gemini model endpoint

Google sign-in / Sheets / Drive also require valid Firebase / Google Cloud OAuth setup:

- `google-services.json`
- Android + Web OAuth clients
- correct SHA fingerprints for debug/release

Compile success does not guarantee runtime Google auth success if that external setup is wrong.

## Verification

Recommended local verification after meaningful changes:

```sh
./gradlew.bat :app:compileDebugKotlin
```

Use this too when startup/resources/packaging changed:

```sh
./gradlew.bat :app:assembleDebug
```

## Text Encoding

- keep source and docs in UTF-8
- be careful with PowerShell bulk rewrites
- prefer plain ASCII punctuation where practical
- if you see mojibake like `Ã‚Â·` or `Ã¢â‚¬Â¦`, fix it before committing
