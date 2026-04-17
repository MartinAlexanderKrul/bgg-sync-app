# BGG Combined

This project is an Android app for logging board game plays to BoardGameGeek (BGG) using AI-powered scoresheet scanning. It is the main app module for the BGG Sync workspace.

## Features

- **Search** for board games using the BGG XML API
- **Scan** handwritten scoresheets with your device camera or gallery
- **AI Vision**: Extracts player names, scores, and winners using Google Gemini Vision API
- **Review & Edit**: Edit extracted results before posting
- **Post** plays to BGG using the unofficial BGG play logging endpoint
- **History**: View plays logged to BGG and those saved locally (offline mode)
- **Games**: Browse your cached BGG game collection
- **Players**: Manage your player roster and aliases
- **Settings**: Configure Gemini API key, BGG credentials, theme, and import/export local data
- **Offline Mode**: Enter scores manually and sync when online

## Project Structure

```
bgg-combined/
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   └── ...
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── ...
```

- The main Android app is in the `app` module.
- All source code is under `app/src/main/kotlin/com/bgg/scorelogger/`.

## Build & Install

### Prerequisites
- Android Studio (Giraffe or newer recommended)
- Android SDK (API 26+)
- Java 17+

### Build APK

From the `bgg-combined` directory:

```sh
./gradlew :app:assembleDebug
```

The APK will be at:
```
bgg-combined/app/build/outputs/apk/debug/app-debug.apk
```

### Install on Emulator/Device

Ensure an emulator or device is running, then:

```sh
./gradlew :app:installDebug
```

or manually with adb:

```sh
adb install "bgg-combined/app/build/outputs/apk/debug/app-debug.apk"
```

## Configuration

- **Gemini API Key**: Obtain from [aistudio.google.com](https://aistudio.google.com) and enter in Settings.
- **BGG Credentials**: Enter your BoardGameGeek username and password in Settings. Credentials are stored encrypted on-device.

## Notes
- The app uses the unofficial BGG play logging endpoint (`geekplay.php`). If logging stops working, check for changes in the BGG website API.
- All AI vision is performed via Google Gemini (free tier available).

## License
This project is for personal use. See LICENSE file if present.

