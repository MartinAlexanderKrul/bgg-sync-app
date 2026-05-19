# BoardFlow Logging Reference

BoardFlow uses Android's native `android.util.Log`. Most feature areas define a dedicated `TAG` so Logcat filtering stays practical during debugging.

## Levels

| Level | Use |
| --- | --- |
| `Log.d` | detailed trace and normal-path diagnostics |
| `Log.i` | important successful operations |
| `Log.w` | recoverable problems and degraded paths |
| `Log.e` | hard failures or exhausted retries |

## Main Tags

### `QuickScan`

File: `AppViewModel.kt`

Used for:

- entering correction mode
- selecting a replacement game during correction
- re-initializing extracted players after correction
- clearing correction mode

Typical messages:

- `Entering correction mode`
- `Correction game selected`
- `Re-initialized N player(s)`
- `Correction mode cleared`

### `AutoSwitch`

File: `AppViewModel.kt`

Used for:

- scan start
- game-recognition gate decisions
- unposted-play sync progress
- scan failure reporting

Typical messages:

- `Scan started`
- `gate=TITLE_GATE`
- `gate=TEMPLATE_CATEGORY_GATE`
- `gate=BLOCKED`
- `Syncing N unposted play(s)`
- `Sync complete`
- `Scan failed`

### `PlayerRecognition`

Files:

- `AppViewModel.kt`
- `data/PlayerRecognitionEngine.kt`

Used for:

- hint resolution
- alias resolution
- fuzzy resolution
- no-match trace
- hint save events
- clearing all saved hints

Typical messages:

- `hint 'X' -> 'Y'`
- `alias 'X' -> 'Y'`
- `fuzzy 'X' -> 'Y'`
- `no match 'X'`
- `saved hint 'X' -> 'Y'`
- `all player recognition hints cleared`

### `GameRecognition`

File: `data/GameRecognitionEngine.kt`

Used for:

- candidate-ranking start
- title-only and category-template ranking
- per-game score breakdown

Typical messages:

- `rankCandidates: title='X'`
- `rankCandidates: no title`
- score breakdown lines per candidate

### `Gemini`

File: `data/GeminiRepository.kt`

Used for:

- extraction start
- per-attempt trace
- model fallback
- model listing
- parse success
- parse degradation

Typical messages:

- `Starting extraction`
- `Attempt N/M`
- `Success`
- `HTTP 503/429 ... switching model`
- `Failed after N attempts`
- `Found N model(s)`
- `Parsed: ...`
- `Parse error`

### `ScanQuality`

File: `data/ScanImageQualityAnalyzer.kt`

Used for:

- local image readability checks before Gemini

Typical messages:

- `resolution: WxH < MIN`
- `Could not decode image`
- `avg luma=...`
- `laplacian variance=...`
- `content area ratio=...`
- `result: OK`
- `result: POOR [...]`

### `BggApiClient`

File: `data/BggApiClient.kt`

Used for:

- BGG XML calls
- sleeve fetch and parse paths
- low-level API diagnostics

Typical messages:

- `ThingDetail id=...`
- `Fetching BGG sleeves for gameId=...`
- `BGG sleeves HTTP ...`
- `Fetching BGG sleeve API for gameId=...`
- `Parsed BGG sleeve API for gameId=...`
- `Relevant sleeve lines ...`

### `BggRepository`

File: `data/BggRepository.kt`

Used for:

- login
- play post
- play delete

Typical messages:

- `Login success for ...`
- `Play logged: gameId=...`
- delete confirmation-step traces

## HTTP Logging

`BggApiClient` and `BggRepository` use `HttpLoggingInterceptor` in debug-style traces. Request and response lines are logged through their module tag, and sensitive headers are redacted.

## Logcat Filter Examples

```text
tag:ScanQuality | tag:Gemini | tag:GameRecognition | tag:AutoSwitch
```

Full scan path.

```text
tag:QuickScan | tag:PlayerRecognition
```

Quick scan correction and scanned-player resolution.

```text
tag:BggApiClient | tag:BggRepository
```

BGG network activity.

```text
level:warn
```

Warnings and errors only.

## Maintenance Notes

- add new tags only when a feature area has enough complexity to justify filtering independently
- prefer stable message prefixes so developers can search exact substrings over time
- keep logs descriptive but avoid dumping secrets, raw auth values, or entire sensitive payloads
