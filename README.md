# OrisMusic

A clean, modern Android music player built with Jetpack Compose, Media3/ExoPlayer, and Material 3.

## Features

- **Modern Architecture**: Built with Jetpack Compose, Hilt DI, and Media3/ExoPlayer
- **Material 3 Design**: Dark theme with custom color palette
- **Background Playback**: Media3 Session service for foreground playback
- **Clean UI**: Bottom sheet player, transport controls, progress slider
- **Extensible**: Modular structure ready for YouTube, local files, and streaming sources

## Tech Stack

- **UI**: Jetpack Compose with Material 3
- **Media**: Media3 ExoPlayer (media3-session, media3-exoplayer, media3-ui)
- **DI**: Hilt (Dagger)
- **Networking**: Ktor + OkHttp
- **Images**: Coil 3
- **Database**: Room
- **Async**: Kotlin Coroutines + Flow
- **Serialization**: Kotlinx Serialization
- **Build**: Gradle Kotlin DSL with version catalog

## Project Structure

```
app/
├── src/main/
│   ├── kotlin/com/orismusic/client/
│   │   ├── di/              # Hilt modules
│   │   ├── ui/
│   │   │   ├── theme/       # Material 3 theme, typography
│   │   │   ├── player/      # PlayerControls, BottomSheetPlayer
│   │   │   └── screens/     # Home, Search, Library, Queue, Settings
│   │   ├── OrisApp.kt       # Application class
│   │   └── MainActivity.kt  # Main activity with navigation
│   └── res/
│       ├── values/          # Strings, themes
│       └── xml/             # Backup rules
```

## Building

1. Copy `local.properties.template` to `local.properties` and set your Android SDK path
2. Run `./gradlew assembleDebug` (Linux/Mac) or `gradlew.bat assembleDebug` (Windows)

## Requirements

- Android Studio Koala or later
- JDK 21
- Android SDK 36
- Min SDK 26 (Android 8.0)

## Architecture Notes

- **Dark-only theme** (like Mochy) - avoids light mode issues
- **Media3 Session** for system integration (notifications, lock screen, Android Auto)
- **Hilt** for dependency injection
- **Compose Navigation** for screen navigation
- **Bottom sheet player** that expands/collapses

## Next Steps

- [ ] Add YouTube Music source (using InnerTube/NewPipeExtractor)
- [ ] Add local media scanner
- [ ] Implement search functionality
- [ ] Add playlist management
- [ ] Add equalizer/DSP
- [ ] Add lyrics support
- [ ] Add Cast support (GMS flavor)
- [ ] Add offline downloads