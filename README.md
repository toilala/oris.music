<p align="center"><img src="docs/player.webp" alt="Oris player" width="280"></p>

# Oris

A free, no-account music player for Android, made by [Nischal](https://github.com/toilala).

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

## Credits and license

GPL-3.0. See [LICENSE](LICENSE). Existing copyright and license notices in the source still apply.

- [Mochy Music](https://github.com/realidkroo/Mochy-Music): the inherited `innertube` client, token/cipher code, some queue helpers, theme and small utilities.
- [Metrolist / InnerTubeX](https://github.com/MetrolistGroup/innertubex): stream extraction library and inherited playback lineage. The old YTPlayerUtils stub was replaced; InnerTubeX and other credited engine parts remain.
- [Echo Music](https://github.com/EchoMusicApp/Echo-Music) and Mochy: references for the new search/radio and lyrics work. Those Oris implementations were written separately, not pasted.
- Lyrics come from [LRCLIB](https://lrclib.net), [Better Lyrics](https://github.com/better-lyrics/api) (also the KuGou route), [Unison](https://github.com/better-lyrics/unison), [YouLyPlus](https://github.com/ibratabian17/LyricsPlus), [PaxSenix](https://lyrics.paxsenix.org/) and YouTube, where available. Provider and required submitter credits are shown in the app. Some sources are optional or unavailable; SimpMusic is disabled because its service restricts other clients. Lyrics still belong to their owners. API access is not a grant of redistribution rights.
