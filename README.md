# BGG Combined

Android app for BoardGameGeek play logging, collection sync, Google Sheets / Drive sync, and AI-assisted score sheet extraction.

## Overview

The app combines a few related workflows into one Compose-based Android client:

- Search and load board games from BoardGameGeek
- Log plays online or save them locally when offline
- Scan score sheets and extract structured results with Gemini
- Sync BGG collection data into Google Sheets
- Create Google Drive folders and QR codes for games
- Manage players, aliases, and BGG usernames
- Export and import local app data

The current UI design and screen layout are intentionally preserved, but the app structure has been refactored for clearer ownership and maintainability.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Navigation Compose
- Android ViewModel + StateFlow
- OkHttp
- Google Identity / Credential Manager
- Google Drive + Sheets SDKs
- CameraX
- Coil

Build targets:

- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk = 26`
- Java 17 / Kotlin JVM target 17

## Project Structure

Main source root:

```text
app/src/main/kotlin/com/bgg/combined/
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
    BggApiClient.kt
    BggCache.kt
    BggImageCache.kt
    BggRepository.kt
    CsvParser.kt
    GeminiRepository.kt
    GoogleApiClient.kt
    QrGenerator.kt
    SecurePreferences.kt
  model/
    Models.kt
  ui/
    app/
      AppShell.kt
    collection/
      CollectionScreen.kt
    common/
      ModifierExtensions.kt
    history/
      HistoryScreen.kt
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
      SyncScreen.kt
    theme/
      Theme.kt
```

### Architecture Notes

- `MainActivity` is intentionally thin.
  It owns Android lifecycle integration, activity result launchers, and wires the app shell.
- `ui/app/AppShell.kt` owns top-level navigation, scaffold, header, and tab layout.
- `auth/GoogleAuthManager.kt` owns Google account selection and authorization flow.
- `core/di/AppContainer.kt` provides lightweight manual dependency wiring.
- `AppViewModel` owns gameplay, history, settings, and local collection state.
- `SyncViewModel` owns Google sync, sheet state, sync logs, and remote collection loading.
- `data/` contains remote/local service code and persistence helpers.

## Build

From the repository root:

```sh
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected emulator/device:

```sh
./gradlew.bat :app:installDebug
```

## Configuration

### BoardGameGeek

Set in Settings:

- BGG username
- BGG password

Notes:

- Collection loading uses BGG XML endpoints
- Play posting uses the unofficial BGG play logging endpoint

### Gemini

Set in Settings:

- Gemini API key
- Gemini model endpoint

Get an API key from [Google AI Studio](https://aistudio.google.com).

### Google Sign-In / Sheets / Drive

The app uses:

- Credential Manager for Google account selection
- Google Identity authorization for Drive / Sheets scopes

Requirements:

- valid `google-services.json`
- working Firebase / Google Auth configuration
- Android and Web OAuth clients configured correctly
- correct debug/release SHA fingerprints registered in Firebase / Google Cloud

If Google sign-in fails at runtime, check the Google Auth / Firebase configuration first.

## What Changed In The Recent Refactor

- Extracted app shell and navigation from `MainActivity`
- Added dedicated Google auth manager
- Moved DI and routes into `core/`
- Removed unused Retrofit / Moshi dependencies
- Updated build target to Java 17
- Simplified several screen-level APIs
- Removed obsolete UI helpers and unsafe state access patterns

## Verification

Latest verified local command:

```sh
./gradlew.bat :app:compileDebugKotlin
```

Status:

- passes successfully after the current refactor

## Known Runtime Caveat

Compile-time verification is passing, but Google sign-in still depends on correct Firebase / Google Cloud project setup outside the repo. If sign-in closes immediately or Credential Manager reports provider/framework errors, verify the OAuth client configuration and registered SHA fingerprints.

