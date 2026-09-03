# Imlog

A modern Android instant messaging and logging application.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-30%20(Android%2011)-brightgreen)](build.gradle.kts)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-orange)](build.gradle.kts)

## Philosophy

Imlog is a personal-first messaging app designed for private logging and direct communication. It prioritizes local data ownership and offline-first functionality.

## Features

- **Local-First Architecture**: All messages and data are stored locally on your device
- **Multi-Format Support**: Send and receive text, images, audio recordings, videos, and files
- **Topic-Based Organization**: Organize conversations into topics for better management
- **Rich Media Playback**: Built-in audio and video playback using Media3
- **Privacy Focused**: No cloud sync, no account required - your data stays on your device

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Database | SQLDelight |
| Dependency Injection | Hilt |
| Media Playback | Media3 (ExoPlayer) |
| Image Loading | Coil |
| Serialization | Kotlinx Serialization |
| Background Tasks | WorkManager |
| Architecture | Clean Architecture + MVVM |

## Requirements

- Android 11 (API 30) or higher
- Android Studio Hedgehog or later

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/fseasy/imlog.git
cd imlog
```

### Build

```bash
./gradlew assembleDebug
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/kotlin/top/fseasy/imlog/
├── data/                  # Data layer - repositories, database, datastore
├── di/                    # Dependency injection modules
├── domain/                # Domain layer - models, use cases, repository interfaces
├── features/              # Feature modules
│   ├── auth/             # Authentication (user creation & selection)
│   ├── home/              # Main home screen with topics
│   ├── settings/         # Settings and preferences
│   └── view/             # Media viewing
├── ui/                    # Shared UI components and theme
└── worker/               # Background workers
```

## Architecture Overview

Imlog follows **Clean Architecture** with three distinct layers:

1. **Domain Layer**: Contains business logic, use cases, and domain models
2. **Data Layer**: Implements repositories, manages database operations via SQLDelight
3. **UI Layer**: Jetpack Compose screens and ViewModels

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome. Please feel free to submit issues or pull requests.
