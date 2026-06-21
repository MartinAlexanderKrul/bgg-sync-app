# BoardFlow

BoardFlow is an Android app for logging board game plays with a mix of local-first persistence, BoardGameGeek integration, Google Sheets and Drive sync, sleeve tracking, widgets, and AI-assisted score extraction.

It is built with Jetpack Compose and uses Room as the live runtime source of truth for collection data, local logged plays, cached BGG plays, session memories, and thumbnail cache data.

## What The App Does

BoardFlow combines several connected workflows in one app:

- search your owned collection first, then fall back to BGG search
- log plays online to BGG or save them locally when offline
- keep an outbox of unposted local plays and post them later from History
- scan a scoresheet image with Gemini to prefill players and scores
- warn locally when a scan is too dark, blurry, low-resolution, or framed too far away
- recognize the game from scan evidence using saved recognition hints and collection matching
- recognize roster players from scanned names using hints, aliases, and fuzzy matching
- review play history, stats, players, and challenge progress
- capture session memories with moods, quote, and an AI chronicle line
- browse owned games, wishlist entries, sleeves, and detailed game metadata
- sync collection data with Google Sheets and Drive
- import CSV rows into a connected sheet
- create and share QR exports for individual plays or full sessions, then import them back into the app
- show home-screen widgets for the last session, rotating daily insights, and monthly play stats with challenge progress
- suggest "Good Picks" before and after logging based on player count fit and history

## Main Product Areas

### Log Play

Primary screens:

- `ui/search/NewPlayScreen.kt`
- `ui/scan/ScanScreen.kt`
- `ui/review/LogPlayScreen.kt`

Key behavior:

1. Choose a game from recent titles, owned collection search, recommendations, or BGG search.
2. Optionally continue the current session, play again from history, or jump in from a widget quick scan.
3. Optionally scan a scoresheet image and let Gemini prefill players, scores, and detected game evidence.
4. Review the extracted result, correct the game if needed, edit players and metadata, then log or save locally.
5. After save, show record moments, challenge progress, and optional Good Picks recommendations.

Notable details:

- search is debounced and prefers owned collection results before BGG XML search
- BGG search includes both base games and expansions
- result lists longer than 20 items show a draggable fast-scroll bar
- scan extraction supports malformed-response background retry with a non-blocking apply banner
- correction mode preserves extracted players and scores while the user re-selects the game
- editable player rows are shared between log, edit, and QR import review flows
- plays support quantity, incomplete, and "now in stats" toggles

### History

Primary screen:

- `ui/history/HistoryScreen.kt`

Tabs:

- `Plays`
- `Challenges`
- `Stats` (includes per-player profiles and head-to-head picker)
- `Players`

Key behavior:

- merges local logged plays with cached BGG plays
- deduplicates with signature matching and lighter history correlation matching
- shows a manual outbox for unposted local plays
- supports edit, delete, repost, play again, and QR share actions for individual plays and full sessions
- drives roster-based player views and richer stats surfaces
- overlays session memories and chronicle lines onto both local and cached BGG plays

### Collection

Primary screens:

- `ui/collection/CollectionScreen.kt`
- `ui/collection/GameDetailDialog.kt`

Tabs:

- `My Shelf`
- `Sleeves`
- `Stats`

Key behavior:

- collection data comes from the canonical Room snapshot
- `My Shelf` lists owned games by default; the filter sheet exposes membership as filter
  dimensions instead of separate tabs:
  - **Show**: `Owned` (default) / `Wishlist` / `Played, not owned` / `Any`
  - **Play status**: `Any` (default) / `Played` / `Unplayed`
  - **Players** / **Best for** / **Recommended for** player-count filters
- played-but-not-owned games are cached as `GameItem`s during sync
  (`SyncViewModel.enrichPlayedGames`) so they appear under `Show -> Played, not owned`, are
  searchable in Log Play, and open as game info from a play; sleeves ignore them
- game detail acts as a cross-link hub into history and players
- sleeve data respects per-game exclusions stored in preferences
- player-count recommendation data includes Best, Recommended, and Not Recommended values

### Sync

Primary screens:

- `ui/sync/SyncScreen.kt`
- `ui/sync/SpreadsheetModal.kt`

Key behavior:

- manages BGG readiness, Google readiness, and spreadsheet connection state
- refreshes BGG collection and sleeve data
- syncs collection data into Google Sheets
- creates or connects a spreadsheet
- imports CSV data into a sheet
- creates Drive folders and QR assets
- keeps a user-visible sync log
- performs a silent startup refresh only when the last successful sync is older than 4 hours

### Settings

Primary screen:

- `ui/settings/SettingsScreen.kt`

Tabs:

- `Accounts`
- `Preferences`
- `Scan`
- `Data`

Key behavior:

- BGG credentials and Google account management
- Gemini API keys, model endpoint, and model discovery
- theme, stats source, recommendations toggle, chronicle toggle, sleeve brand priority
- recognition template management
- player recognition hint clearing
- backup export and import
- collection cache clearing
- custom mood template management

## AI Features

### Score Extraction

`GeminiRepository` extracts:

- players and scores
- detected game title
- detected game confidence
- detected scoring categories
- short evidence text

Requests use `streamGenerateContent?alt=sse` (SSE streaming). The UI transitions from **"Sending to AI…"** to **"Reading response…"** as soon as the first SSE chunk arrives, giving the user immediate feedback without waiting for the full response.

`responseMimeType: "application/json"` is set on every request so the model is structurally constrained to emit valid JSON. This eliminates malformed responses. The background-retry-with-banner path still exists as a safety net but should never trigger under normal conditions.

Image resolution is capped at **1200 px** on the longest dimension (previously 800 px), giving the model more detail for dense or handwritten score sheets. Output tokens are budgeted at **2048** with `temperature 0.15` and `topP 0.90`.

### Game Recognition

`GameRecognitionEngine` ranks collection candidates using:

- title similarity
- category text appearing in the game name
- saved category-template overlap
- saved title bonus

`AppViewModel` can auto-switch the selected game when confidence gates are met, or show a ranked suggestion banner when they are not.

### Player Recognition

`PlayerRecognitionEngine` resolves scanned names through:

1. saved scan hints
2. exact alias or display-name match
3. fuzzy Levenshtein match

Only high-confidence non-fuzzy results are auto-applied.

### Chronicles

Session memories can include:

- moods
- quote
- note
- chronicle line

Chronicles are generated by `SessionChronicleService`, which reuses existing results when the source key matches, calls Gemini when generation is needed, and falls back deterministically when Gemini is unavailable.

## Challenges And Recommendations

### Challenges

Current challenge types:

- `PLAY_N_TIMES`
- `PLAY_SPECIFIC_GAME`
- `PLAY_N_DISTINCT`
- `PLAYER_WIN_STREAK`
- `PLAY_WITH_GROUP_N_TIMES`
- `PLAY_STREAK`
- `PLAY_N_UNPLAYED`

Challenge progress is calculated live from history in `AppViewModel.getChallengeProgressList()`. The app also auto-creates a monthly challenge when challenge state is first loaded and the current period does not already have one.

### Recommendations

BoardFlow currently has two recommendation surfaces:

- pre-log recommendations on `NewPlayScreen`
- post-log Good Picks in `LogPlayScreen`

Both respect the user preference toggle `recommendationsEnabled`. Player-count fit prioritizes:

1. Not Recommended -> filtered out
2. Best -> top score
3. Recommended
4. official min/max range

## Storage Model

### Room

`data/CanonicalCollectionStore.kt` is the live runtime source of truth.

It stores:

- canonical merged collection snapshot
- local logged plays
- cached BGG play history
- play sessions
- session memories in `play_memories`
- history thumbnail cache in `game_thumbnail_cache`
- players in `players`
- challenges in `challenges`
- game recognition hints in `game_recognition_hints`
- player recognition hints in `player_recognition_hints`
- sleeve tracking in `game_sleeve_tracking`

Current DB version: `10`

Recent migrations of note:

- `6 -> 7`: adds `notRecommendedPlayers` to `canonical_games`
- `7 -> 8`: adds `game_thumbnail_cache`
- `8 -> 9`: adds `players`, `challenges`, `game_recognition_hints`, `player_recognition_hints`
- `9 -> 10`: adds `game_sleeve_tracking`

### SecurePreferences

`data/SecurePreferences.kt` stores:

- BGG credentials
- Gemini keys and selected model endpoint
- theme, stats scope, chronicle toggle, recommendations toggle
- roster players and aliases (legacy; now also in Room — migrated on first load)
- recent games
- Google sync preferences
- session context
- sleeve exclusions
- recognition hints
- player recognition hints
- custom moods and mood usage order
- challenges (legacy; now also in Room — migrated on first load)

### Backup Format

`data/BackupSerializer.kt` currently exports backup format version `7`.

Backups can include:

- collection snapshot
- local logged plays
- cached BGG plays
- players
- recent games
- model list cache
- game recognition hints
- player recognition hints
- custom moods and mood usage order
- challenges
- settings
- optionally sensitive data such as BGG password and Gemini keys

Import is selective: only keys present in the backup replace existing values.

## Architecture Map

Primary entry points:

- `MainActivity.kt`
- `ui/app/AppShell.kt`

Core view models:

- `AppViewModel.kt`
- `SyncViewModel.kt`

Important supporting modules:

- `auth/GoogleAuthManager.kt`
- `core/di/AppContainer.kt`
- `core/navigation/AppRoutes.kt`
- `data/BggRepository.kt`
- `data/BggApiClient.kt`
- `data/GoogleApiClient.kt`
- `data/GeminiRepository.kt`
- `data/CanonicalCollectionStore.kt`

High-level responsibilities:

- `MainActivity`: Android lifecycle, auth launchers, widget intent entry points
- `AppShell`: scaffold, routing, header state, bottom navigation, cross-screen hops
- `AppViewModel`: search, log flow, history, roster, recognition, challenges, chronicles, recommendations, import/export
- `SyncViewModel`: account state, sheet state, collection refresh, sleeves, CSV import, Drive and QR sync surfaces

## Package Layout

```text
app/src/main/kotlin/cz/nicolsburg/boardflow/
  AppViewModel.kt
  MainActivity.kt
  SyncConfig.kt
  SyncViewModel.kt
  auth/
  core/
  data/
  model/
  ui/
```

Key UI areas:

- `ui/app`
- `ui/search`
- `ui/scan`
- `ui/review`
- `ui/history`
- `ui/challenges`
- `ui/collection`
- `ui/settings`
- `ui/sync`
- `ui/widget`

## Documentation Index

- UI surface inventory: [`docs/UI_SURFACES.md`](docs/UI_SURFACES.md)
- gamification, chronicles, challenges, and recommendations: [`docs/GAMIFICATION.md`](docs/GAMIFICATION.md)
- logging tags and Logcat usage: [`docs/LOGGING.md`](docs/LOGGING.md)
- widget architecture and behavior: [`docs/WIDGETS.md`](docs/WIDGETS.md)

## External Integrations

### BoardGameGeek

Used for:

- collection refresh
- BGG XML search
- play history fetch
- play post/edit/delete
- sleeve-related metadata

Notes:

- authenticated play actions use cookie-backed session persistence
- XML search outside local collection requires `BGG_XML_API_TOKEN`
- failed token-backed search degrades quietly to empty results

### Google Identity, Sheets, and Drive

Used for:

- account selection and authorization
- spreadsheet creation and connection
- collection sync into sheets
- CSV import
- per-game Drive folder and QR asset flows

### Gemini

Used for:

- score extraction from images
- chronicle generation

#### API key and model configuration

- user-provided primary API key
- extra API keys for rotation (`SecurePreferences.getGeminiExtraApiKeys`)
- model discovery via `listAvailableModels` (v1beta → v1 fallback)
- default model: `gemini-2.0-flash-lite`

#### Rotation strategy (score extraction and chronicle)

When a request returns HTTP 429 or 503:

1. **Zero-quota check** — if the response body contains `"limit: 0"`, the model is immediately marked exhausted and key rotation is skipped. The model is added to a session-scoped `zeroQuotaModels` set and `onModelExhausted` is called so the ViewModel can persist a 24-hour TTL via `SecurePreferences.markModelExhausted`.
2. **Key rotation** — if the failure is a normal rate limit and more keys are available, the next key is tried. Score extraction uses exponential backoff (2 s, 4 s, 8 s… capped at 16 s) for 429s and a flat 2 s for 503s.
3. **Model rotation** — once all keys for a model are exhausted, the next model from the priority list is tried and the key index resets to 0.

Up to 10 attempts total across all key+model combinations.

#### Model priority order (score extraction)

```
gemini-2.0-flash → gemini-2.0-flash-lite → gemini-1.5-flash-latest →
gemini-1.5-flash → gemini-2.5-flash-preview-05-20 → gemini-2.5-flash →
gemini-1.5-pro-latest → gemini-1.5-pro → (remaining available models, alphabetical)
```

Models outside the priority list sort after all prioritised entries, gemini-prefixed first.

#### Model exhaustion persistence

`SecurePreferences.markModelExhausted(model)` stores a per-model expiry timestamp (default 24 h TTL). `getEffectiveModels()` filters out models whose TTL has not yet expired, so quota recovers automatically after a reset without user action. The older `removeAvailableModel` (permanent removal) is retained for backup/restore compatibility only.

#### Session-scoped model stickiness

When the repository rotates to a fallback model during a scan, `AppViewModel` caches the new model for 5 minutes (`sessionModel` / `sessionModelExpiry`). Subsequent scans within that window reuse the working model rather than retrying the exhausted one.

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

Install on a connected device or emulator:

```sh
./gradlew.bat :app:installDebug
```

## Configuration

Common runtime settings:

- BGG username
- BGG password
- Gemini API key
- Gemini model endpoint

Google auth and API flows also require valid external configuration:

- `google-services.json`
- Android and Web OAuth clients
- correct debug and release SHA fingerprints

## Verification

Recommended after meaningful changes:

```sh
./gradlew.bat :app:compileDebugKotlin
```

Use this when resources, packaging, or signing-related behavior changed:

```sh
./gradlew.bat :app:assembleDebug
```

## Known Documentation Notes

- This repo currently has no `app/src/test` or `app/src/androidTest` source set.
- The docs in `docs/` are intended to describe shipped behavior, not aspirational features.
- Keep docs in UTF-8 and prefer plain ASCII punctuation to avoid mojibake.
