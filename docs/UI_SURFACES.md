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

- `ScreenTabRow` with `Plays`, `Challenges`, `Stats`, and `Players`
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

Current challenge creation flow supports all seven shipped challenge types.

## Journal Stats

Primary files:

- `ui/history/PlayStatsTab.kt`
- `ui/history/InsightStripCard.kt`
- `ui/history/PlayStatsHelpers.kt`

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

## QR Play Import

Source: `ui/history/QrPlayImportScreen.kt`

Main surfaces:

- `BoardFlowCameraScene`
- `BoardFlowCameraActionPanel`
- `BoardFlowCameraPermissionPrompt`
- parsing overlay
- import review screen
- import header card
- small toggle cards
- player editor list
- error surface
- `DatePickerDialog`

## Collection

Source: `ui/collection/CollectionScreen.kt`

Main surfaces:

- tab row for `Owned`, `Wishlist`, and `Sleeves`
- collection refresh confirmation
- filter sheet
- collection search field
- loading shimmer cards
- error and empty states
- `GameCard` rows
- `GameDetailsDialog`

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
- prefer BoardFlow button wrappers over raw Material buttons
- keep business logic out of composables when a surface starts growing
- update this file whenever a new durable surface or modal flow is introduced
