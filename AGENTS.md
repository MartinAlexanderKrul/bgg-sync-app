# AGENTS.md

## Purpose

This repository contains the BoardFlow Android app. Agents working here should preserve the current user-facing UI design while improving architecture, correctness, and maintainability.

## Fast Start

If you are a new agent in this repo, do not start by reading everything. Start with:

- `README.md` for the current product and storage model
- `app/src/main/kotlin/cz/nicolsburg/boardflow/ui/app/AppShell.kt` for navigation and top-level layout
- `app/src/main/kotlin/cz/nicolsburg/boardflow/AppViewModel.kt` for gameplay, search, history, and offline flows
- `app/src/main/kotlin/cz/nicolsburg/boardflow/SyncViewModel.kt` for sync, cache refresh, and sheet/collection loading
- `app/src/main/kotlin/cz/nicolsburg/boardflow/data/CanonicalCollectionStore.kt` for the live Room-backed source of truth
- `app/src/main/kotlin/cz/nicolsburg/boardflow/data/BggRepository.kt` for BGG search/log/play history endpoints
- `app/src/main/kotlin/cz/nicolsburg/boardflow/data/GoogleApiClient.kt` for Sheets/Drive sync

Prefer targeted inspection of those files over broad codebase exploration unless the issue clearly spans multiple layers.

## Current Architecture

- `MainActivity.kt`
  Thin Android entry point. Handles lifecycle, activity result launchers, and wires app-level services into Compose.
- `ui/app/AppShell.kt`
  Owns the top-level scaffold, navigation graph, header, and bottom navigation.
- `auth/GoogleAuthManager.kt`
  Owns Google account selection and authorization orchestration.
- `core/di/AppContainer.kt`
  Lightweight manual DI container.
- `core/navigation/AppRoutes.kt`
  Single source of truth for app routes.
- `AppViewModel.kt`
  Owns gameplay, search, player management, settings-adjacent app state, history, and local/offline flows.
- `SyncViewModel.kt`
  Owns Google sync flows, sync logs, spreadsheet state, and synced collection loading.
- `data/`
  API clients, caching, storage, parsing, and persistence helpers.
- `ui/`
  Screen composables and shared UI helpers.

## Source Of Truth

- Canonical collection data, local logged plays, and cached BGG play history live in Room via `CanonicalCollectionStore`.
- `SecurePreferences` now stores settings, credentials, recent games, and backup/compatibility helpers only.
- `BackupSerializer` owns import/export JSON format.
- Do not reintroduce JSON blobs as the live runtime cache unless explicitly asked.

**Notable integrations and patterns:**

- `data/GeminiRepository.kt`: Handles AI-assisted score extraction from images using Gemini API, with model fallback and error handling.
- `data/GoogleApiClient.kt`: Manages Google Sheets/Drive sync, spreadsheet tab/row/column logic, Drive folder and QR code creation, and applies sheet styles.
- `data/CanonicalCollectionStore.kt`: Room-backed live store for canonical collection data, local plays, and cached BGG play history.
- `data/BackupSerializer.kt`: JSON import/export for backups; `SecurePreferences` now stores settings and compatibility helpers rather than the live caches.
- `ui/sync/SpreadsheetModal.kt`: Implements the modal composable pattern for spreadsheet connection/creation (`SpreadsheetConnectModal`).
- Sleeve data is merged from BGG API, HTML scraping, and local cache (see `GameItem.Sleeves` in `Models.kt` and related logic in `GoogleApiClient.kt`).

## What Usually Matters

- Preserve the current visual hierarchy unless the user explicitly asks for a redesign.
- Keep merge logic source-aware:
  - BGG owns identity, stats, players, BGG links, ownership flags, and BGG play count
  - Google Sheets owns manual/spreadsheet values and sheet links
  - sleeve refresh owns sleeves only
- Full sync should update the canonical merged snapshot once at the end.
- Local/offline history should not mutate canonical collection state.
- BGG search outside the loaded collection requires the XML API token and should fail quietly to an empty result state if the token is missing or rejected.

## Expectations For Changes

- Keep the existing visual layout and design language unless explicitly asked to redesign.
- Prefer small, structural improvements over broad behavioral rewrites.
- Keep `MainActivity` thin.
- Put navigation concerns in `ui/app` or `core/navigation`, not in random screens.
- Put Android service / identity / auth orchestration into focused helpers or managers.
- Keep view models focused by domain responsibility.
- Avoid pushing business logic into composables.
- Prefer `StateFlow` and unidirectional data flow for screen state.
- Remove dead dependencies when they are clearly unused.
- Do not reintroduce deprecated Google sign-in APIs.
- If a change is only about one feature area, stay in that area first instead of refactoring unrelated modules.

**BGG API flows:**

- Both authenticated and unauthenticated collection/play logging flows are supported (see `BggRepository.kt`).
- Retry and error handling are implemented for BGG endpoints and login.
- BGG XML API search outside the loaded collection requires an application token stored in Settings and sent as `Authorization: Bearer ...`.
- When a search falls back from the local collection to BGG XML search, the app should fail gracefully to an empty result state if the token is missing or rejected.
- The live source of truth for collection and history data is Room, not the old JSON cache files.

## UI Conventions

- Preserve the current screen hierarchy and tab layout.
- Prefer extracting small reusable helpers when a screen starts carrying framework glue or duplicated UI logic.
- Avoid unsafe `!!` access in composables when a nullable state can be handled cleanly.
- Keep screen parameters minimal and explicit.
- Keep user-facing strings and source files in UTF-8, but prefer plain ASCII punctuation when practical.
- Be careful with PowerShell bulk text replacements or rewrite scripts; they can cause mojibake like `Â·`, `â€¦`, or `Ã¢â‚¬Â¦` if encoding is mishandled.
- If encoding corruption appears in uncommitted changes, fix it before doing any further refactors or commits.

**Modal pattern example:**
```
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

When relevant, also use:

```sh
./gradlew.bat :app:assembleDebug
```

If you only changed docs or small behavior, compile is usually enough. Reach for `assembleDebug` when you touched resources, packaging, or startup behavior.

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

Avoid adding Retrofit / Moshi back unless there is a clear need; they were removed as unused.

## Refactor Guidance

If continuing the modernization work:

- next best targets are splitting `AppViewModel` and `SyncViewModel` into smaller feature-oriented state holders
- move model classes out of the catch-all `Models.kt` file into more focused files
- add clearer UI state models for screens with mixed loading/data/error logic
- improve Google sign-in diagnostics with device-tested logging if runtime issues continue
- when in doubt, inspect the targeted feature files first and avoid a whole-repo read unless the bug spans sync, storage, and UI at the same time
