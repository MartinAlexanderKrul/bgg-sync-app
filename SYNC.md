# SYNC.md

## BoardFlow Sync and Caching: Technical Overview

This document explains how BoardFlow handles Google Sheets read/write, syncing, and caching, including the behavior of all sync-related buttons and how data is modified. It is based on the current codebase and architecture as of April 2026.

---

## 1. Google Sheets Read/Write Granularity

- **Row-Oriented Updates:**
  - The app reads and writes Google Sheets data by row, mapping each row to a domain model (e.g., a game or play log).
  - Updates are not performed by overwriting the entire sheet, but by targeting specific rows and columns.

- **Batch Updates:**
  - Uses Google Sheets API's `BatchUpdateValuesRequest` to send many cell updates (across multiple rows/games) in a single API call.
  - Example (from `GoogleApiClient.kt`):
    ```kotlin
    fun batchWrite(updates: List<ValueRange>) {
        if (updates.isEmpty()) return
        val req = BatchUpdateValuesRequest().setValueInputOption("RAW").setData(updates)
        retryWrite { sheets.spreadsheets().values().batchUpdate(spreadsheetId, req).execute() }
    }
    ```
  - This minimizes API request volume and latency.

- **Whole Collection Updates:**
  - When syncing the whole collection, all necessary cell changes for all games are prepared and sent in a single batch request.
  - The batch covers multiple games and their cells in one request, not one request per game.

---

## 2. Sync and Caching Workflows

### Key Components
- **`GoogleApiClient.kt`:** Implements all Google Sheets read/write logic, including batch updates.
- **`SyncViewModel.kt`:** Orchestrates sync logic, manages logs, busy state, and cancellation.
- **`SyncScreen.kt`:** UI for sync controls; defines sync buttons and their actions.

### State Management
- Sync operations are managed via `SyncViewModel`, with logs, busy state, and cancellation support.
- Uses `StateFlow` for screen state and logs.

### Caching
- Local cache is used to store fetched BGG and Google Sheets data for offline access and performance.
- Sync operations update both the local cache and the remote sheet as needed.
- Caching logic is handled in the data layer (see `data/` folder).

---

## 3. Sync Buttons and Their Behaviors

### Typical Sync Buttons (from `SyncScreen.kt`)

#### 1. **Sync BGG to Google Sheet**
- **Action:**
  - Fetches the latest collection from BGG (BoardGameGeek) for the current user.
  - Updates the Google Sheet with the fetched data using a batch update.
  - Updates the local cache with the new data.
- **Trigger:**
  - Calls `syncViewModel.syncBgg(account, forceRefresh = true)`.
- **Data Modified:**
  - Google Sheet (remote)
  - Local cache

#### 2. **Sync Google Sheet to Local**
- **Action:**
  - Reads the current state of the Google Sheet.
  - Updates the local cache with the sheet data.
- **Trigger:**
  - Calls `syncViewModel.syncSheet(account)`.
- **Data Modified:**
  - Local cache only

#### 3. **Manual Refresh / Force Sync**
- **Action:**
  - Forces a refresh from BGG and/or Google Sheet, bypassing cache.
  - Ensures the latest data is fetched and written.
- **Trigger:**
  - Calls sync methods with `forceRefresh = true`.
- **Data Modified:**
  - Google Sheet (if writing)
  - Local cache

#### 4. **Cancel Sync**
- **Action:**
  - Cancels any ongoing sync operation.
- **Trigger:**
  - Calls `syncViewModel.cancelSync()`.
- **Data Modified:**
  - None (operation is aborted)

#### 5. **Sync Logs and Progress**
- **Action:**
  - Sync operations log progress and errors to a log view (managed by `SyncViewModel`).
  - Busy state is tracked to disable buttons and show progress indicators.

---

## 4. Sync Operation Flow (Example)

- **User presses "Sync BGG to Google Sheet":**
  1. UI disables sync buttons and shows progress.
  2. `SyncViewModel` launches a coroutine for the sync job.
  3. BGG data is fetched (with retry and error handling).
  4. Data is mapped to sheet rows and prepared as a batch update.
  5. Batch update is sent to Google Sheets API.
  6. Local cache is updated.
  7. Logs are updated throughout the process.
  8. On completion or error, busy state is cleared and UI is updated.

---

## 5. Error Handling and Retry

- Sync operations use retry logic for network and API errors.
- Errors are logged and surfaced to the user in the sync log view.
- Cancellation is supported via coroutine job cancellation.

---

## 6. Caching Details

- **Local cache** is used for both BGG and Google Sheets data.
- Cache is updated on successful syncs and used for offline access.
- Cache invalidation occurs on force refresh or when remote data changes.

---

## 7. Additional Notes

- **Batching** is critical for minimizing Google API quota usage and latency.
- **StateFlow** and unidirectional data flow are used for robust UI state management.
- **Logs** and busy state are surfaced in the UI for transparency.
- **SyncViewModel** is the main orchestrator for all sync and caching operations.

---

## 8. References

- `app/src/main/kotlin/cz/nicolsburg/boardflow/data/GoogleApiClient.kt`
- `app/src/main/kotlin/cz/nicolsburg/boardflow/SyncViewModel.kt`
- `app/src/main/kotlin/cz/nicolsburg/boardflow/ui/sync/SyncScreen.kt`

---

## 9. Summary Table: Sync Buttons

| Button Label                | Action                                      | Data Modified         |
|----------------------------|----------------------------------------------|----------------------|
| Sync BGG to Google Sheet   | Fetch BGG, update sheet & cache              | Sheet, Local cache   |
| Sync Google Sheet to Local | Fetch sheet, update local cache              | Local cache          |
| Manual Refresh / Force Sync| Force fetch & update                         | Sheet, Local cache   |
| Cancel Sync                | Abort ongoing sync                           | None                 |

---

## 10. Build/Verification

- Run `./gradlew.bat :app:compileDebugKotlin` to verify changes.
- For full build: `./gradlew.bat :app:assembleDebug`

---

## 11. Runtime Note

- Google sign-in and Sheets/Drive access depend on external Firebase/Google Cloud OAuth configuration. Successful compile does not guarantee runtime sign-in success if SHA fingerprints or OAuth client setup are wrong.

---

## 12. Architecture Reference

See `AGENTS.md` for a full breakdown of the app's architecture, conventions, and refactor guidance.

