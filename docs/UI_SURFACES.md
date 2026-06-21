# BoardFlow UI Surface Inventory

This document maps the main user-facing Compose surfaces in BoardFlow. Use it when adjusting layout, interaction patterns, or feature-state handling.

## Shared Surface Primitives

| Surface | File | Purpose |
| --- | --- | --- |
| `SectionCard` | `ui/common/BoardFlowUi.kt` | reusable rounded grouped-content card |
| `AnimatedDialog` | `ui/common/BoardFlowUi.kt` | shared large-dialog shell with motion and bounded height |
| `BoardFlowConfirmationDialog` | `ui/common/BoardFlowUi.kt` | compact confirm/cancel dialog |
| `BoardFlowModalBottomSheet` | `ui/common/BoardFlowUi.kt` | shared filter and picker sheet wrapper |
| `BoardFlowPickerField` | `ui/common/BoardFlowUi.kt` | settings-style single-value picker trigger |
| `BoardFlowPickerSheet<T>` | `ui/common/BoardFlowUi.kt` | generic option sheet for settings pickers |
| `GameSearchField` | `ui/common/GameSearchField.kt` | shared search input with trailing actions |
| `PlayerResultEditorCard` | `ui/common/PlayerResultEditorCard.kt` | shared player editing card for log/edit/import flows |
| `BoardFlowCameraScene` | `ui/common/BoardFlowCameraUi.kt` | full-screen camera preview shell |
| `BoardFlowCameraActionPanel` | `ui/common/BoardFlowCameraUi.kt` | camera action controls |
| `BoardFlowCameraPermissionPrompt` | `ui/common/BoardFlowCameraUi.kt` | camera permission state |

## App Shell

Source: `ui/app/AppShell.kt`

Main surfaces:

- persistent app header with subtitle and contextual actions
- bottom navigation for `Log Play`, `Journal`, `Collection`, `Sync`, and `Settings`
- discard confirmation when leaving `LogPlayScreen` with unsaved state

The bottom navigation is hidden on scan and review routes.

## New Play

Source: `ui/search/NewPlayScreen.kt`

Main surfaces:

- `SessionContinueBanner`
- `GameSearchField` with quick-scan action
- loading shimmer list
- collection/search error card
- empty states
- owned-game result list
- draggable fast-scroll bar for long lists
- pre-log recommendation lanes when enabled and the query is blank
- active challenges strip

Important behavior:

- results for this screen are owned-only
- wishlist games are excluded from the main log-play result flow

## Scan

Source: `ui/scan/ScanScreen.kt`

Main surfaces:

- `BoardFlowCameraScene`
- `BoardFlowCameraActionPanel`
- `BoardFlowCameraPermissionPrompt`
- pending photo preview overlay
- local quality-check progress row
- scan quality warning with `Retake` and `Use anyway`
- extraction loading state
- extraction error state

## Log Play Review

Source: `ui/review/LogPlayScreen.kt`

Main surfaces:

- `SessionDetailsCard`
- `RelatedGamesBanner`
- `ScanResultBanner`
- `GameSuggestionBanner`
- `ScanRetryBanner`
- `PlayersHeader`
- frequent-player chips
- `PlayerEditCard` wrapping `PlayerResultEditorCard`
- `AiOutputCard`
- `PostSaveCard`
- persistent bottom action bar
- `DatePickerDialog`

`PostSaveCard` may include:

- record moment
- challenge progress deltas
- collapsible "Try next" Good Picks section

## Journal

Source: `ui/history/HistoryScreen.kt`

Main surfaces:

- `ScreenTabRow` with `Plays`, `Challenges`, `Stats` (per-player profiles + head-to-head), and `Players`
- `GameSearchField` with QR import and filter actions
- plays refresh confirmation
- delete confirmation
- filter sheet
- filter status strip
- pending plays outbox card
- play list
- loading shimmer list
- plays empty state
- `ChallengesEntry` shortcut strip from Plays into Challenges
- `PlayDetailsDialog`
- `EditPlayDialog`
- `SharePlayQrDialog`
- `SessionHubDialog`
- `ShareSessionQrDialog`
- nested player-detail dialogs

### Play Details Dialog

Main sections:

- hero image/backdrop header
- stats and metadata
- chronicle card
- session memory display or editor
- insight strips
- player rows
- share, edit, play-again, and delete actions

### Challenges Tab

Current surfaces:

- `ChallengesTabContent`
- challenge list cards
- empty state
- floating `New challenge` action when the tab is active
- `CreateChallengeDialog`
- challenge card long-press action sheet via `BoardFlowModalBottomSheet`

Current challenge creation flow supports all seven shipped challenge types.

## Journal Stats

Primary files:

- `ui/history/PlayStatsTab.kt`
- `ui/history/InsightStripCard.kt`
- `ui/history/PlayStatsHelpers.kt`
- `ui/history/PlayStatsComputer.kt`

Main surfaces:

- stats source label
- `Table Brief`
- `PeriodReviewCard`
- `ContextualInsightStrip`
- `HeroObservationCard`
- summary section
- heatmap section
- activity section
- top games
- top players
- rivalry pairs
- day-of-week distribution
- on-this-day section
- more-numbers section
- `HeadToHeadSection` -- two `BoardFlowPickerField` player selectors with head-to-head play and win stats between the selected pair

### Session Hub Dialog

Source: `ui/history/SessionHubDialog.kt`

Opened from `PlayDetailsDialog` when a play belongs to a multi-play session.

Main sections:

- session title with inline rename (edit icon) and QR share (share icon) actions
- `SessionHubSummaryCard` — date, player count, play count, duration, total points, location, and top winners
- `SessionHubMemoryCard` — mood chips and quotes (shown when present)
- per-play `SessionHubPlayCard` list — tappable cards that open the individual `PlayDetailsDialog`
- "Play this session again" action

`ShareSessionQrDialog` is shown in-place (same as `SharePlayQrDialog` for single plays) with a "Share image" action and a display of the session label and play count.

## QR Play Import

Source: `ui/history/QrPlayImportScreen.kt`

Handles both single-play (`BFPLAY1:`) and session (`BFSESS1:`) QR codes. The scanner detects which format was scanned and routes to the appropriate review screen.

Main surfaces:

- `BoardFlowCameraScene`
- `BoardFlowCameraActionPanel`
- `BoardFlowCameraPermissionPrompt`
- parsing overlay
- `QrPlayImportReview` — single-play review with editable fields, player list, and date picker
- `QrSessionImportReview` — session review showing a summary card and per-play cards; imports all plays in one action
- error surface

## Collection

Source: `ui/collection/CollectionScreen.kt`

Main surfaces:

- tab row for `My Shelf`, `Sleeves`, and `Stats`
- collection refresh confirmation
- filter sheet
- collection search field
- loading shimmer cards
- error and empty states
- `GameCard` rows
- `GameDetailsDialog`
- `CollectionStatsTab` — stats-only tab with no filter or search; reads `allGames` directly

`My Shelf` lists owned games by default. Membership and play state are filter dimensions in the
filter sheet rather than separate tabs:

- **Show**: `Owned` (default) / `Wishlist` / `Played, not owned` / `Any`
- **Play status**: `Any` (default) / `Played` / `Unplayed`
- **Players** / **Best for** / **Recommended for** player-count filters

Played-but-not-owned games are cached as `GameItem`s during sync
(`SyncViewModel.enrichPlayedGames`) so they appear under `Show -> Played, not owned`, are
searchable in Log Play, and open as game info from a play. Sleeves ignore them (sleeve surfaces
filter on `isOwned`).

## Collection Stats Tab

Source: `ui/collection/CollectionStatsTab.kt`

Cards shown (each conditional on having data):

- `OverviewCard` — Owned / Wishlist / Unplayed big-stat tiles; avg rating and total BGG plays row
- `PlayDepthCard` — proportional bar chart: Unplayed / Tried (1-4) / Familiar (5-14) / Deep (15+)
- `ComplexityCard` — proportional bar chart of weight tiers (Light → Expert)
- `SleeveCard` — Sleeved / To sleeve / Not tracking counts; percentage sleeved line
- `TopPlayedCard` — ranked list of top 5 most-played owned games
- `UnplayedShelfCard` — collapsible alphabetical list of owned games never played

Stats are computed once per `games` list via the pure `computeStats()` function and held in `remember`.

## Game Details Dialog

Source: `ui/collection/GameDetailDialog.kt`

Main surfaces:

- hero dialog shell with sticky compact header
- status chips and top actions
- `YourStatsCard`
- mastery pill
- contextual insight strip
- player-count preference block
- info groups
- sleeves section
- external BGG and Drive actions

The player-count block can show:

- Best for
- Great with
- Avoid

depending on what recommendation data exists for the game.

## Sleeves

Source: `ui/collection/SleevesScreen.kt`

Main surfaces:

- sleeve summary header
- expandable included-game selector
- sleeve-size group cards
- empty state

## Players

Source: `ui/players/PlayersScreen.kt`

This source set still exists, but app navigation now centers player-facing UX inside Journal -> Players.

Main surfaces:

- players list
- add player dialog
- edit player dialog
- delete-player confirmation
- remove-alias confirmation
- player detail dialog

## Settings

Source: `ui/settings/SettingsScreen.kt`

Main surfaces:

- tab row for `Accounts`, `Preferences`, `Scan`, and `Data`
- spreadsheet connect dialog
- sign-out confirmation
- collection cache clear confirmation
- recognition template clear confirmation
- player hint clear confirmation
- backup import confirmation

### Accounts Tab

- Google account card
- spreadsheet connection card
- BGG credentials card

### Preferences Tab

- theme picker
- history stats source picker
- recommendations toggle
- chronicles toggle
- sleeve manufacturer picker
- mood templates card and dialogs

### Scan Tab

- Gemini key management card
- model picker and refresh flow
- recognition templates card and dialogs
- player recognition hints card

### Data Tab

- collection cache management
- backup export/import
- inline success and error status

## Sync

Sources:

- `ui/sync/SyncScreen.kt`
- `ui/sync/SpreadsheetModal.kt`

Main surfaces:

- readiness hub
- step cards for BGG and Sheets actions
- advanced section
- spreadsheet connect dialog
- Google manage dialog
- BGG edit dialog
- sync-again confirmation
- clear-sync-log confirmation
- bottom log bar
- sync log dialog
- busy progress surfaces

## System-Owned Surfaces

These are launched from the app but owned by Android:

- backup export document picker
- backup import document picker
- score scan gallery picker
- QR import image picker
- CSV picker
- external URL intents
- Android share sheets

## Maintenance Notes

- prefer `AnimatedDialog` for custom app dialogs
- prefer `BoardFlowConfirmationDialog` for simple confirm/cancel flows
- prefer `BoardFlowModalBottomSheet` for temporary filter and picker content
- prefer `BoardFlowModalBottomSheet` for long-press action menus instead of `DropdownMenu`
- prefer BoardFlow button wrappers over raw Material buttons
- keep business logic out of composables when a surface starts growing
- update this file whenever a new durable surface or modal flow is introduced
