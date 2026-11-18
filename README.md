<p align="center"><img src="docs/player.webp" alt="Oris player" width="280"></p>

# Oris


- Search and play music, with a queue, liked songs, playlists, and album and artist pages
- Synced lyrics from several sources you can switch on, off and reorder
- Save songs for offline, sleep timer, playback speed, resume on Bluetooth
- Dark, warm look with colours from the artwork, optional wallpaper colours, pure black, and a choice of app icons
- No ads, no tracking, everything you like or play stays on your phone

The screenshot is from a phone running a test build.

## Build

Use JDK 17 and the Android SDK, then run:

```
./gradlew :app:assembleFossDebug
```

The APK ends up in `app/build/outputs/apk/foss/debug/`.
