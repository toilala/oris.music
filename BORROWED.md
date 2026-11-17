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
- HomeScreen.kt: REWRITTEN as genuine Oris code. The old static placeholder home screen (with demo autoplay in MainActivity) was replaced with a real YouTube Music home feed (loading/error/ready states, Coil artwork, tap-to-queue) written fresh for Oris. The innertube module it talks to remains borrowed as listed above.
- innertube HomePage.kt: ORIS EXTENSION to borrowed code. YouTube now serves home shelf songs as musicResponsiveListItemRenderer items, which the borrowed parser dropped, leaving the feed empty. Added fromResponsiveSongItem so home shelves parse as songs again. The module remains borrowed/credited; this fix is ours.

- PoTokenGenerator.kt: small Oris integration fix to borrowed code, not a rewrite. Application context is supplied through its constructor instead of an uninitialized CipherDeobfuscator global. InnerTubeXResolver uses its suspend token entry point. Mochy provenance and GPL credit remain.

- YTPlayerUtils.kt guest path: fresh Oris implementation informed by reading the visitor-first / VISIONOS-first approach in Mochy Music and oris-music-foundation YTPlayerUtils.kt. No source blocks copied. Accepts only direct HTTPS googlevideo audio without cipher or n-transform; unsupported formats remain with the credited InnerTubeX library. Existing innertube API module stays borrowed/GPL-3.0. Reference: https://github.com/toilala/oris-music-foundation

- PlaybackDiagnostic and refusal handling: fresh Oris code. Shows provider status/reason on device without cookies/tokens/stream URLs; explicit bot-check refusal stops automatic load retries. This is a diagnostic build, not proof of working anonymous playback.

## Music candidate policy and radio (Oris rewrite)

Read Echo Music's OnlineSearchViewModel, RadioQueuePageFilter and
RecommendationEngine at cb398f13 for ideas: Music search endpoints, preserving
service relevance, track+artist de-duplication, and next/related radio candidates.
Source: https://github.com/EchoMusicApp/Echo-Music
No Echo blocks were pasted into MusicCandidates or MusicRadioQueue. These are
new Oris implementations. Existing inherited YouTube/InnerTube parsers remain
covered by the credits above. No claim is made that metadata proves original
artist/channel ownership. SimpMusic lyrics is not integrated: its API explicitly
rejects non-SimpMusic clients.

### Playback settings and task-removal shutdown

The playback preference, Settings UI, shutdown coordination, cancellation additions and debug local-tone checks are independently written for Oris. Existing Android/Media3 lifecycle APIs and the inherited resolver/token components remain under their original credits. No Echo or Mochy implementation was pasted for this slice.

### Multi-source lyrics

Lyrics fetching, recording matching, LRC/TTML parsing and Compose UI are independently written for Oris after studying Echo/Mochy provider approaches. No provider implementation was copied. Source text is supplied by LRCLIB (https://lrclib.net), Better Lyrics (https://github.com/better-lyrics/api, GPL-3.0 service; including its Kugou adapter) and the existing YouTube Music client. Provider credit is visible in the lyrics view. Better Lyrics is called with an honest Oris User-Agent and no private key; public cache access can fail or be incomplete. No extension-only or SimpMusic credentials/client identity are used. Lyrics remain their authors' copyrighted works; availability through an API does not establish redistribution/display rights for a public release.
