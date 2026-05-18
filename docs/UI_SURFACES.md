# BoardFlow UI Surface Inventory

This inventory documents the app's user-facing Compose surfaces: popup windows, dialogs, modal sheets, cards, menus/dropdowns, banners, list surfaces, and other contained UI elements. It is based on the source under `app/src/main/kotlin/cz/nicolsburg/boardflow/ui`.

Use this as a map when changing layout, motion, state handling, or visual language. Keep new surfaces in the same family unless a feature explicitly needs a new pattern.

## Shared Surface Primitives

| Surface | File | Shape / behavior | Used for |
| --- | --- | --- | --- |
| `SectionCard` | `ui/common/BoardFlowUi.kt` | Standard rounded content card with optional accent and click handling. | Settings cards, player rows, sleeve groups, filter sections, reusable grouped content. |
| `AnimatedDialog` | `ui/common/BoardFlowUi.kt` | Custom dialog wrapper with animated entry, drag handle, and bounded height. | Most large app dialogs and modal-style forms. |
| `BoardFlowConfirmationDialog` | `ui/common/BoardFlowUi.kt` | Compact confirm/cancel dialog (max 360 dp); neutral, positive, or destructive styling. Actions are trailing-aligned `TextButton`s — dismiss in muted `onSurfaceVariant`, confirm in `primary` or `error` with `SemiBold` weight. | Delete, discard, clear, sign-out, sync-again confirmations. |
| `BoardFlowModalBottomSheet` | `ui/common/BoardFlowUi.kt` | Bottom sheet with shared drag handle and rounded top corners. | Collection and History filter sheets. |
| `BoardFlowPickerField` | `ui/common/BoardFlowUi.kt` | Tappable rounded card showing a label + current value with an animated chevron. Amber border and label when the associated sheet is open. | Settings pickers (theme, sleeve manufacturer, Gemini model). |
| `BoardFlowPickerSheet<T>` | `ui/common/BoardFlowUi.kt` | `BoardFlowModalBottomSheet` listing generic options as rounded rows; selected row has an amber border and checkmark. Scrollable `LazyColumn` capped at 360 dp for long lists. Dismissed by drag, outside tap, or Cancel button. | Settings pickers (theme, sleeve manufacturer, Gemini model). |
| `Popover` | `ui/common/BoardFlowUi.kt` | Anchored floating popover with outside-tap dismissal. | Shared primitive; currently no broad feature owner. |
| `ScreenTabRow` | `ui/common/ScreenTabRow.kt` | Screen-level tab row. | History, Collection, Settings. |
| `GameSearchField` / `SearchFieldActionButton` | `ui/common/GameSearchField.kt` | Reusable search input with trailing icon actions. | New Play, Collection, History. |
| `PlayerResultEditorCard` | `ui/common/PlayerResultEditorCard.kt` | Collapsible player edit card with name, score, team, rating, winner, first-play, exact match, and suggestions. | Log Play, Edit Play, QR import review. |
| `BoardFlowTonalButton` | `ui/common/BoardFlowUi.kt` | `FilledTonalButton` wrapper, 42 dp height, grey `surfaceVariant` fill, press-scale animation. | Compact paired actions inside cards and dialogs (e.g. Edit + Play again in play details). |
| `BoardFlowCameraScene` | `ui/common/BoardFlowCameraUi.kt` | Full-screen camera scene with title/subtitle overlays. | Score scan and QR import scan. |
| `BoardFlowCameraActionPanel` | `ui/common/BoardFlowCameraUi.kt` | Bottom camera action panel. | Score scan capture/gallery/manual actions and QR import image/cancel actions. |
| `BoardFlowCameraPermissionPrompt` | `ui/common/BoardFlowCameraUi.kt` | Full-screen camera permission state. | Score scan and QR import scan. |

## App Shell

Source: `ui/app/AppShell.kt`

- `AppHeader`: persistent app chrome with app title, contextual subtitle, optional back/close action, and optional collection filter action.
- `NavigationBar`: bottom navigation for Log Play, History, Collection, Sync, and Settings. Hidden during Scan and Log Play review routes.
- `BoardFlowConfirmationDialog` titled "Discard log play?": shown when leaving Log Play with unsaved play data, editable players, or extracted scan data.
- Header collection filter action: compact icon action shown when Collection controls have scrolled away and filters are available.

## New Play / Search

Source: `ui/search/NewPlayScreen.kt`

- `SessionContinueBanner`: top banner asking "Continue last session?", with Use and dismiss actions.
- `GameSearchField`: main game search box with camera action for quick scan.
- Loading list: `LazyColumn` of `ShimmerGameRow` placeholders.
- Error card: Material `Card` in error colors for collection/search errors, with "Use recent games instead".
- Empty states: centered "Log a Play" and "No games found" states.
- Game result list: `LazyColumn` of `GameRow` list items — populated from owned games only (wishlist excluded); falls back to BGG API search when no local owned match is found.
- Fast scroll bar and floating letter bubble: shown for result lists over 20 items.
- Change-game notice: small inline text when a previous session is being retargeted.
- **Active Challenges strip**: shown above the game list when there are incomplete challenges. Displays challenge titles and live progress to give the user context while picking a game.

## Scan

Source: `ui/scan/ScanScreen.kt`

- `BoardFlowCameraScene`: full-screen score-sheet camera preview.
- `BoardFlowCameraActionPanel`: bottom capture panel with Capture, Gallery, and Manual actions.
- `BoardFlowCameraPermissionPrompt`: camera permission state with grant, gallery, and manual actions.
- Pending photo preview overlay: full-screen scrim containing a preview `Card` titled "Use this photo?".
- Scan quality checking row: "Checking scan quality..." shown while local analysis runs.
- Scan quality warning: "This scan may be hard to read." with local reason rows, plus "Retake" and "Use anyway".
- Extraction loading state: centered progress indicator and "Extracting scores...".
- Extraction error state: centered error panel with selectable error text, "Try again from gallery", and "Enter Manually".

## Log Play Review

Source: `ui/review/LogPlayScreen.kt`

- `DatePickerDialog`: date picker for the play date.
- `SessionDetailsCard`: main play metadata card for game, AI detected hint, date, duration, location, notes, quantity, incomplete, and now-in-stats controls.
- `RelatedGamesBanner`: expansion/base-game follow-up posting banner. Chips are capped to 2 rows via `SubcomposeLayout`; overflow shown as "Show all (N)" / "Show less" `TextButton`.
- `ScanResultBanner`: post-scan game recognition feedback for auto-switch, no collection match, or low confidence.
- `GameSuggestionBanner`: candidate game suggestion banner with confidence/evidence and actions.
- `ScanRetryBanner`: non-blocking banner when a background AI retry produced cleaner data.
- `PlayersHeader`: players section header with add-player and AI output toggle.
- `FrequentPlayerChips`: frequent/recent player suggestion chips.
- `PlayerEditCard`: wrapper around `PlayerResultEditorCard`. Cards that have a filled name and score auto-collapse when scan results first arrive.
- `AiOutputCard`: collapsible raw AI output card with model name and copy action.
- `PostSaveCard`: post-log success card with session summary, record moment, and Play again / View session / Change game actions. Includes a collapsible "Try next" **Good Picks** section (up to 2 recommendations) when owned games fit the logged player count. Each pick shows game name and a reason string ("Best at N players", "Recommended for N players", "Fits M–N players", "Quick to table at ~N min", etc.). Tapping a pick navigates directly to Log Play for that game.
- Bottom post bar: persistent bottom action area with error surface and Log/Save button.

## History

Source: `ui/history/HistoryScreen.kt`

- `ScreenTabRow`: Plays, Stats, Players, Challenges tabs.
- `BoardFlowConfirmationDialog` titled "Refresh again?": confirms play-history refresh when cache is recent.
- `BoardFlowConfirmationDialog` titled "Delete play?": deletes local or BGG-backed plays.
- Error strips: inline error containers for delete/edit failures.
- History search field: `GameSearchField` with QR import and filter actions.
- `BoardFlowModalBottomSheet` with `HistoryFilterSheetContent`: sort, date range, player filters, and reset action.
- Filter status strip: "Filtered by..." surface with Clear action.
- `PendingPlaysCard`: local unposted plays outbox with Post / Post all controls.
- Plays list: `LazyColumn` of `PlayHistoryCard`.
- Loading list: `ShimmerPlayCard` placeholders.
- Empty state: centered "No play history".
- `PlayDetailsDialog`: animated play details dialog with thumbnail/backdrop, stats, insights, player rows, share, edit, play again, delete, and game/player deep links. Edit and Play again actions use paired `BoardFlowTonalButton`s on one row.
  - `ChronicleInsightCard`: golden-bordered card (`Color(0xFFF0A500)` accent, `AutoStories` icon) shown above insight strips. Displays the generated chronicle line, or a `...` placeholder when generation is pending. Hidden entirely when Chronicles are disabled in Settings.
  - `PlayMemorySection`: session memory area below the Chronicle card. Switches between `MemoryEmptyState` ("Capture this session" prompt), `MemoryDisplay` (amber mood chips in a `FlowRow` + attributed quote), and `MemoryEditor` (see below).
  - `MemoryEditor`: inline mood editor with a `FlowRow` of toggleable `MemorySelectableChip`s (preset + custom), an additive "Add another mood…" `OutlinedTextField`, a quote `OutlinedTextField`, and a "Save memory" `BoardFlowTonalButton` with a `Check` icon.
- **Challenges tab** (`ChallengesTabContent` in `ui/challenges/ChallengesScreen.kt`): `LazyColumn` of `ChallengeCard` rows — each shows title, description, a linear progress bar, current/target count, completion state, and a delete icon. Empty state when no challenges exist. When the Challenges tab is active, a floating `EmojiEvents` FAB labeled "New challenge" appears.
- `CreateChallengeDialog`: `AnimatedDialog` for creating a challenge. Fields: optional title (auto-derived from goal if blank); goal type dropdown (Play N times / Play a specific game N times / Play N distinct games); game search autocomplete (appears for PLAY_SPECIFIC_GAME); numeric target count; optional start and end date pickers. Cancel / Create actions.
- Nested `PlayerDetailDialog`: opened from player rows inside play details.
- `SharePlayQrDialog`: animated dialog showing a generated QR code and share image / done actions.
- `EditPlayDialog`: animated play edit dialog with metadata fields, player editor cards, date picker, notes, and save.
- `DatePickerDialog`: used inside Edit Play.
- Players tab surfaces: reuses `PlayersTabContent`, `PlayerDetailDialog`, `EditPlayerDialog`, and `AddPlayerDialog`.

## History Stats

Sources: `ui/history/PlayStatsTab.kt`, `ui/history/InsightStripCard.kt`

- Stats tab list: `LazyColumn` of analytics sections.
- Stats source label: shows whether the tab is using all logged plays or only plays marked Count in stats. The source is chosen in Settings > Preferences.
- `Table Brief`: compact narrative section with up to three ranked observations for the selected period, such as pace changes, table favorite, dormant favorite, rivalry watch, or most-present player.
- `PeriodReviewCard`: auto-generated narrative card surfaced at the top of Stats during the first 5 days of a new month (or first 7 days of a new year). Driven by `buildPeriodReview()` in `PlayStatsHelpers.kt`. Shows play count, unique games, new players, and a narrative highlight ("Martin finally won Brass."). Only shown in the All-time range.
- `ContextualInsightStrip` / `InsightStripCard`: compact rarity-aware insight strips. Background alpha, border colour, and icon tint scale across the five rarity tiers (Common → Legendary). Used for game/player/stat context.
- `HeroObservationCard`: prominent rotating observation card driven by `buildSmartObservations()`. Features a spring-physics scale entrance (0.95 → 1.0, ~350 ms), a single shimmer sweep for Epic/Legendary cards (fires 1.5 s after settle), and haptic feedback (`LongPress`) on Epic/Legendary reveal. Tappable to cycle through observations.
- `SummarySection`: overview metrics with inline `GamerArchetype` display (archetype title and tagline shown to the right of the play count). `ArchetypeCard` was removed; archetype is now embedded in this section.
- `HeatmapSection`: play activity heatmap card.
- `ActivitySection`: recent/all-time activity chart section.
- `TopGamesSection` and `TopGameRow`: top game rankings; rows can deep-link into game-filtered history.
- `TopPlayersSection` and `TopPlayerRow`: top player rankings; rows can deep-link into player-filtered history.
- `RivalryPairsSection` and `RivalryPairRow`: rivalry pair stats. Graph-row player labels use `shortName()` to abbreviate long names (e.g. "Martin Alexander Krul" → "Martin K.") only in the narrow bar-flanking slots; the narrative headline keeps the full name.
- `DayOfWeekSection`: play distribution section.
- `OnThisDaySection`: historical anniversary/memory section.
- `MoreNumbersSection`: collapsed-by-default metric grid for secondary numbers such as H-index, depth, active days, complete rate, and longest session.

## QR Play Import

Source: `ui/history/QrPlayImportScreen.kt`

- `BoardFlowCameraScene`: full-screen QR scanning camera.
- `BoardFlowCameraActionPanel`: bottom panel with From image and Cancel actions.
- `BoardFlowCameraPermissionPrompt`: camera permission state with Allow camera and Use image.
- Parsing overlay: centered progress indicator while decoding.
- `QrPlayImportReview`: full-screen review surface in a `LazyColumn`.
- Import header card: game/date/duration/location/quantity/notes and collection match status.
- `SmallToggleCard`: Incomplete and Count in stats toggles.
- Player editor list: `PlayerResultEditorCard` per imported player.
- Error surface: import/save error container.
- `DatePickerDialog`: used for imported play date.

## Collection

Source: `ui/collection/CollectionScreen.kt`

- `ScreenTabRow`: Owned, Wishlist, Sleeves tabs.
- `BoardFlowConfirmationDialog` titled "Sync again?": confirms collection refresh when cache is recent.
- `GameDetailsDialog`: animated game detail dialog opened from collection cards.
- `BoardFlowModalBottomSheet` with `FilterSheetContent`: sort, player count, best-for filters, and reset action.
- Collection search field: `GameSearchField` with filter action and active-filter indicator dot.
- Game list: `LazyColumn` of `GameCard`.
- `GameCard`: collection item card with thumbnail, BGG/Drive mini link buttons, rating, year, weight, time, player count, wishlist, and recommendation text.
- Loading list: `ShimmerGameCard` placeholders.
- Empty and error states: centered states with optional Load/Retry action.
- Header filter action: coordinated with `AppShell` when on-screen filters scroll away.

## Game Details Dialog

Source: `ui/collection/GameDetailDialog.kt`

- `GameDetailsDialog`: primary animated dialog with image backdrop, scrollable content, sticky compact header, and game actions.
- Header section: cover image, status chips, Log Play, and History actions.
- `YourStatsCard`: personal game stats with player links. Includes a mastery-level pill chip below the play count (Learning / Familiar / Comfortable / Practiced / Deep / Mastered based on total plays). Driven by `masteryLabel()`.
- `ContextualInsightStrip`: contextual game insight when available. Insight is rarity-aware (colour, border, icon opacity scale with tier).
- `PlayerPreferenceBlock`: player-count information. Header row shows "Players" label with the official min–max range right-aligned. Below, up to three community-poll columns appear when data is present: **Best for** (primary color bubbles), **Great with** (muted bubbles), **Avoid** (error-color bubbles for "Not Recommended" counts). Columns are conditionally rendered — absent when data is missing.
- `InfoGroupBlock`: overview, ratings, and custom metadata groups.
- `SleevesBlock` / `SleevesSection`: sleeve status, counts, manufacturer recommendation, and navigation to sleeve group.
- External action row: Open BGG and Drive buttons.
- `CompactStickyHeader`: small header overlay shown while the dialog content scrolls.

## Sleeves

Source: `ui/collection/SleevesScreen.kt`

- `SleeveSummaryHeader`: accented `SectionCard` summarizing included games and sizes; click expands game selector, long press toggles all owned mode, swipe excludes/includes all, share exports CSV.
- Included game selector: expandable `SectionCard` listing games with compact checkmarks for inclusion/exclusion.
- `SleeveSizeGroupCard`: expandable card per sleeve size group, with total count and game breakdown.
- Empty state: centered "All games are sleeved".

## Players

Source: `ui/players/PlayersScreen.kt`

This screen currently exists in source but is not wired into `AppShell`; player-oriented navigation currently lands in `History` -> `Players`.

- Players list: `LazyColumn` of `PlayerListItem`.
- `PlayerListItem`: `SectionCard` with identity, aliases, play summary, favorite game, and edit icon.
- Floating add button: opens `AddPlayerDialog`.
- `PlayerDialog`: shared animated dialog shell for player add/edit/detail.
- `AddPlayerDialog`: new player form.
- `EditPlayerDialog`: display name, BGG username, aliases, add alias (inline `OutlinedTextField` with trailing `+` icon; lit when non-blank; IME Done adds alias), remove alias, delete player. Delete and Save Changes actions use a paired `BoardFlowDestructiveButton` + `BoardFlowButton` on one row with trash / pen icons.
- `BoardFlowConfirmationDialog` titled "Delete player?": destructive confirmation.
- `BoardFlowConfirmationDialog` titled "Remove alias?": destructive confirmation.
- `PlayerDetailDialog`: player identity, stats, favorite/most-played links, rivalries, All plays and Edit actions.
- Nested rival player detail: `PlayerDetailDialog` can open another player dialog from a rivalry row.

## Settings

Source: `ui/settings/SettingsScreen.kt`

- `ScreenTabRow`: four tabs — **Accounts**, **Preferences**, **Scan**, **Data**.
- `SettingsCard`: `SectionCard` wrapper for settings groups.
- `SpreadsheetConnectDialog`: `AnimatedDialog` used from Settings for Google Sheet connect/change/create.
- `BoardFlowConfirmationDialog` titled "Import backup and replace current data?": destructive backup import confirmation.
- `BoardFlowConfirmationDialog` titled "Sign out of Google?": Google sign-out confirmation.
- `BoardFlowConfirmationDialog` titled "Clear collection cache?": destructive collection cache clear.
- `BoardFlowConfirmationDialog` titled "Clear recognition templates?": destructive game recognition template clear.
- `BoardFlowConfirmationDialog` titled "Clear player recognition hints?": destructive player hint clear.

**Accounts tab** — all external service credentials:
- Google `SettingsCard`: signed-in email + sign-out, or Sign in button; Google Sheets connection (connect/change).
- BoardGameGeek `SettingsCard`: username + password fields with visibility toggle.
- Google AI Studio `SettingsCard`: primary Gemini API key field with info dialog, visibility toggle, and backup key list (add/delete/toggle visibility per key).

**Preferences tab** — appearance and behaviour:
- Theme picker: `BoardFlowPickerField` + `BoardFlowPickerSheet` over `AppTheme` entries (Light/Dark).
- History stats source picker: `BoardFlowPickerField` + `BoardFlowPickerSheet` over `StatsPlayScope` entries.
- Chronicles toggle: `SettingsCard` with `AutoStories` icon and a `Switch`. When off, all in-flight chronicle generation is cancelled and chronicle cards are hidden app-wide.
- Sleeve manufacturer picker: `BoardFlowPickerField` + `BoardFlowPickerSheet` over `SleeveManufacturer` entries.
- Mood Templates `SettingsCard`: count + "Manage moods" → `CustomMoodsDialog`.

**Scan tab** — scoresheet scanning configuration and learned data:
- Gemini Model `SettingsCard`: `BoardFlowPickerField` + `BoardFlowPickerSheet` when models have been discovered; plain `OutlinedTextField` before discovery; "Refresh available models" button.
- Recognition Templates `SettingsCard`: count, "View templates" → `RecognitionTemplatesDialog`, "Clear recognition templates" with confirmation.
- Player Recognition Hints `SettingsCard`: count, "Clear player recognition hints" with confirmation.

**Data tab** — data management:
- Collection Cache `SettingsCard`: "Clear Collection Cache" with confirmation.
- Backup & Restore `SettingsCard`: include-sensitive-data checkbox, Export Data, Import Data buttons; inline success/error status.

**Shared dialogs:**
- API key help dialog: `AnimatedDialog` explaining how to get a Gemini API key.
- `RecognitionTemplatesDialog`: `AnimatedDialog` listing saved game recognition templates; title only, no close button (drag-handle dismisses).
- Recognition template item `DropdownMenu`: long-press menu with Edit and Delete.
- `EditTemplateDialog`: `AnimatedDialog` for editing template scoring categories; title only, no close button.
- Category chips: removable `SuggestionChip` entries in template editing.
- `CustomMoodsDialog`: `AnimatedDialog` listing user-saved mood labels as amber `CircleShape` chips. Each chip has Edit and Delete `TextButton`s; live-updates via `customMoods` StateFlow.
- `EditMoodDialog`: `AnimatedDialog` with a pre-filled `OutlinedTextField` (40 char max) and a `BoardFlowButton` with `Check` icon to save.

## Sync

Sources: `ui/sync/SyncScreen.kt`, `ui/sync/SpreadsheetModal.kt`

- `ReadinessHub`: top readiness/status card for Google, BGG, and Sheet setup.
- Step `SectionCard`s: BGG refresh actions and Google Sheets sync actions.
- `AdvancedSection`: expandable advanced area with CSV import, Drive folder/QR creation, and save-QR checkbox.
- `SpreadsheetConnectDialog`: `AnimatedDialog` for connecting or creating a sheet.
- `GoogleManageDialog`: `AnimatedDialog` for Google sign-in/sign-out management.
- Nested `BoardFlowConfirmationDialog` titled "Sign out of Google?": shown from Google manage dialog.
- `BggEditDialog`: `AnimatedDialog` for BGG username/password edit.
- `LogBar`: bottom sync status bar, tappable to view details.
- `LogDialog`: animated dialog with sync summary and raw log entries.
- `BoardFlowConfirmationDialog` titled "Clear sync log?": destructive clear confirmation.
- `BoardFlowConfirmationDialog` titled "Sync again?": confirms refresh/sync if collection was recently synced.
- Busy progress: `LinearProgressIndicator` in main screen and spinner in log surfaces.

## Data / System Pickers

These are not custom Compose dialogs, but they open system-owned surfaces:

- Backup export: `ActivityResultContracts.CreateDocument("application/json")` in Settings.
- Backup import: `ActivityResultContracts.OpenDocument()` in Settings.
- Score scan gallery: `ActivityResultContracts.GetContent()` in Scan.
- QR import image picker: `ActivityResultContracts.GetContent()` in QR Play Import.
- CSV picker: launched by the host through Sync.
- External URL intents: BGG, Drive, sleeve search, Google AI Studio links.
- Android share sheets: sleeve CSV export and QR image sharing.

## Maintenance Notes

- Prefer `AnimatedDialog` for custom app dialogs and `BoardFlowConfirmationDialog` for simple confirm/cancel decisions.
- `AnimatedDialog` provides a drag-handle strip at the top; do not add a redundant X close `IconButton` inside the dialog content.
- Name `AnimatedDialog`-backed composables with a `Dialog` suffix, not `Modal` (the three Sync dialogs were renamed accordingly).
- Prefer `BoardFlowModalBottomSheet` for temporary filter panels.
- Prefer `BoardFlowPickerField` + `BoardFlowPickerSheet` for settings-style single-value pickers; do not add new `ExposedDropdownMenuBox` pickers.
- Prefer `SectionCard` for repeated list/group cards.
- Corner-radius families: use `BoardFlowSurfaceTokens.Shape` (12 dp) for standard cards, `BoardFlowSurfaceTokens.ContentCardShape` (16 dp) for prominent feature content surfaces, `BoardFlowActionTokens.ButtonShape` (16 dp) for buttons, and `BoardFlowConfirmationTokens.Shape` (20 dp) for confirmation dialogs. Do not introduce new hardcoded radius values in the 14–18 dp range.
- Button hierarchy: use `BoardFlowButton` / `BoardFlowPrimaryButton` for primary actions, `BoardFlowSecondaryButton` / `BoardFlowOutlinedButton` for secondary actions, `BoardFlowTonalButton` for compact secondary actions inside cards and dialogs (grey surfaceVariant fill, 42 dp height), `BoardFlowDestructiveButton` for destructive actions. Do not add raw `FilledTonalButton` or `Button` calls without a BoardFlow wrapper.
- Confirmation dialog actions: use trailing-aligned `TextButton` pairs — dismiss labeled in `onSurfaceVariant`, confirm labeled in `primary` (or `error` for destructive) with `SemiBold` weight. Do not use full-width `weight(1f)` outlined/filled buttons inside `BoardFlowConfirmationDialog`.
- Paired action rows (non-confirmation dialogs): when a full-dialog form needs two sibling actions (e.g. destructive + confirm, or delete + save), place them in a `Row` with `weight(1f)` on each button, icons leading. Use `BoardFlowDestructiveButton` + `BoardFlowButton` for destructive/confirm pairs, `BoardFlowTonalButton` + `BoardFlowTonalButton` for neutral pairs.
- Chip overflow: use `SubcomposeLayout` for row-capped chip lists (e.g. 2-row limit with "Show all" overflow). `FlowRowOverflow.expandOrClip` is not available in Compose BOM 2024.08.00.
- Log Play game search shows owned games only (no wishlist). Use `AppViewModel.logPlaySearchResults` / `loadLogPlayGames()` / `filterLogPlayGames()` — do not switch this screen to the shared `searchResults` flow.
- Text separators: use `" · "` as the standard inline-text separator between metadata items; avoid mixing with `" - "` or em-dashes in the same visual context.
- Avoid adding business logic directly to composables when a surface grows; push state decisions into view models or small UI state models.
- When adding a new surface, update this file with the trigger, file, and dismissal/confirmation behavior.
