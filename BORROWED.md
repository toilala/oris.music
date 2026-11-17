# Borrowed vs original code tracker

This repo rebuilds the original `toilala/oris-music` app code toward a fully
working player. Some parts of the original repository were themselves copied
from other GPL-3.0 projects. This file tracks exactly what is borrowed, from
where, and the plan to replace each piece with genuinely rewritten Oris code.

License: GPL-3.0 (see LICENSE). Credits for borrowed code stay in place while
the code stays close to its source. Replacements must be genuine rewrites
(own structure and logic), not renames.

## Currently borrowed (from Mochy Music, GPL-3.0, https://github.com/realidkroo/Mochy-Music)

| Area | Files | Status |
|---|---|---|
| YouTube API client (`innertube/` module) | 106 files, byte-identical to Mochy `providers/innertube` | BORROWED - rewrite planned |
| Proof-of-origin tokens (`app/.../utils/potoken/`) | PoTokenGenerator, PoTokenWebView, JavaScriptUtil, po_token.html (identical after package rename), PoTokenResult, PoTokenException | BORROWED - rewrite planned |
| Stream cipher (`app/.../utils/cipher/`) | CipherWebView, FunctionNameExtractor, PlayerJsFetcher (~100% Mochy), CipherDeobfuscator (gutted copy, currently broken) | BORROWED - rewrite planned |
| Queue helpers | EmptyQueue, ListQueue, YouTubeAlbumRadio (~85% Mochy) | BORROWED - rewrite planned |
| Theme base | ui/theme/Type.kt (95%), ui/theme/Theme.kt (70%) | BORROWED - rewrite planned |
| Small utilities | utils/DataStore.kt, models/MediaMetadata.kt (partial overlap) | BORROWED - rewrite planned |
| Gradle wrapper | gradlew, gradlew.bat, gradle-wrapper.jar (stock Gradle wrapper, Apache-2.0, via oris-music-foundation) | TOOLING - no rewrite needed |

## Original Oris code (no significant match in Mochy, Metrolist or oris-music-foundation)

- MainActivity, OrisApp, OrisMediaService (854 lines), MediaLibrarySessionCallback, PlayerConnection (own simplified implementation)
- ui/screens/HomeScreen.kt, ui/player/PlayerControls.kt
- playback/queues: YouTubeQueue, YouTubePlaylistQueue, LocalMixQueue, RadioQueuePageFilter, Queue
- constants/PreferenceKeys.kt, constants/MediaSessionConstants.kt, di/AppModule.kt, di/PlaceholderModules.kt
- utils/InnerTubeXResolver.kt, utils/TimeUtils.kt, utils/ReportException.kt, extensions/MediaItemExt.kt
- AndroidManifest.xml, build configuration, resources, app icons

## Replacement log

(Each genuine rewrite lands here with its commit and verification evidence.)

- YTPlayerUtils.kt: REWRITTEN as genuine Oris code. The Metrolist-lineage stub (which returned a hardcoded demo MP3) was deleted outright and replaced with our own stream resolution over the app's InnerTubeXResolver; the Metrolist header went with the deleted code. Playback wiring (ResolvingDataSource in OrisMediaService) is new Oris code too. Engine internals (InnerTubeX library, innertube module, PoToken) remain borrowed as listed above.
