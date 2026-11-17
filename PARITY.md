# Oris rebuilt: feature-parity roadmap with oris-music-foundation

Target: feature parity with oris-music-foundation, written as genuine Oris code
(same libraries where sensible: Compose M3, Media3, Coil, Room, DataStore).
Borrowed internals stay credited in BORROWED.md until genuinely rewritten.

One verified slice at a time: build-gated commit, emulator-verified where
user-facing, status per slice.

## Stage 1 - playable core (phone verified)
- [x] Real home feed from YouTube Music (loading/error/ready, artwork, tap-to-queue)
- [x] Stream resolution over the app's own InnerTubeXResolver (demo track deleted)
- [x] Owner verified anonymous home-song playback and play/pause on phone (Nov 17).
      CI is refused by YouTube's bot check, so UI/unit checks run without live playback.
- [x] On-device refusal diagnostic with explicit bot-check retry stop.

## Stage 2 - player experience
- [x] System Back dismisses player, then unwinds tab history to Home; state survives recreation.
- [x] Play icon state follows Media3 isPlaying, not requested play intent.
- [ ] Full now-playing screen: artwork, title/artist, seek bar with position/duration,
      play/pause/next/prev, shuffle/repeat, expand from a mini-player bar
- [ ] Queue screen: up-next list, tap to jump, remove/reorder
- [ ] Sleep timer basics (if cheap), playback speed/pitch later

## Stage 3 - search
- [ ] Online search screen: query field, live suggestions, results grouped
      (songs/albums/artists/playlists), tap song to play, tap others to detail pages
- [x] Online grouped search results; song taps queue the song group.
- [x] Debounced query suggestions, explicit Search button, clear/retry states.
- [x] Recent searches (local persistence, clear history).
- [ ] Album/artist/playlist rows open detail pages (stage 4).

## Stage 4 - detail pages
- [ ] Album page (track list, play all)
- [ ] Artist page (top songs, albums)
- [ ] Online playlist page (tracks, play/shuffle)

## Stage 5 - library (needs local db: Room)
- [ ] Liked songs (heart in player/list, library songs tab)
- [ ] Local playlists: create, add songs, play
- [ ] History / recently played
- [ ] Library tabs: songs/albums/artists/playlists

## Stage 6 - lyrics
- [ ] Lyrics pane in player (YouTube transcript/lyrics providers as foundation does)

## Stage 7 - offline and caching
- [ ] Stream caching for replay without re-download
- [ ] Downloads: save songs for offline, downloads tab, offline playback

## Stage 8 - settings
- [ ] Playback settings (audio quality, data saver)
- [ ] Appearance (theme), content (explicit filter), about/credits

## Stage 9 - discovery
- [ ] Explore/moods and genres, charts
- [ ] Song radio / related, quick-pick continuations on home

## Stage 10+ - advanced parity (later, individually scoped)
- Crossmix/transitions, equalizer, Android widget, Android Auto, Discord RPC,
  scrobbling, import from other apps, canvas, recap, voice control

## Music-only search and radio follow-up
- [x] Search combines Music's Songs endpoint with safe catalog groups.
- [x] Prefer audio for duplicate track/artist recordings, preserve service relevance,
      reject podcast/UGC types, tolerate missing type with supporting album/artist metadata.
- [x] Home/search queues can continue from YouTube Music radio pages; candidates share
      the same policy and are de-duplicated across pages.
- [ ] Listening-history/genre personalization (requires library history first).
- Artist/channel ownership is not supplied as a verified fact by the current model.

## Task removal and playback settings

- Own-written Settings screen reached from the Home settings icon.
- Keep playing after removal from recents defaults OFF. Home/app switching is unchanged.
- OFF cancels service coroutines and resolver warm-up/requests, closes the token WebView, releases all players and session, abandons focus, removes the notification and stops the non-sticky service.
- ON explicitly keeps playback on task removal. No scheduled playback jobs are created by this implementation.
- Debug-only local-tone activity and OS task-removal checks test actual playback without depending on streamed audio. Not included in release builds.

## Multi-source lyrics

- Player opens Lyrics with Automatic or explicit source selection.
- Automatic follows saved user order and enabled sources, preferring a synced result while retaining a plain fallback. The registry contains Echo's nine sources; availability caveats are explicit rather than claiming all nine work.
- Settings and the Lyrics dropdown expose provider order and enable/disable choices, persisted locally. Restore defaults is available.
- Artist/title/version/duration checks reduce wrong-recording LRCLIB matches; remaster labels can differ but live/remix labels are not erased.
- Synced lines highlight and scroll with playback; timed lines seek on tap. Plain lyrics remain readable and labelled. TTML is line-synced here, not word-highlighted.
- Requests have cancellation, limits/timeouts and a small successful-result memory cache. No disk lyric cache.
- SimpMusic is unavailable under its client restriction. YouLyPlus/PaxSenix are optional, off by default after 502/403 validation. Unison reads require linked provider/submitter credit. KuGou uses Better Lyrics' keyless route. Full source audit: [docs/LYRICS_SOURCES.md](docs/LYRICS_SOURCES.md).
- A debug-only metadata screen exercises real lyric retrieval with no audio resolution. Release APKs do not contain the debug harness.
