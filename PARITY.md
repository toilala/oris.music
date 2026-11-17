# Oris rebuilt: feature-parity roadmap with oris-music-foundation

Target: feature parity with oris-music-foundation, written as genuine Oris code
(same libraries where sensible: Compose M3, Media3, Coil, Room, DataStore).
Borrowed internals stay credited in BORROWED.md until genuinely rewritten.

One verified slice at a time: build-gated commit, emulator-verified where
user-facing, status per slice.

## Stage 1 - playable core (in progress)
- [x] Real home feed from YouTube Music (loading/error/ready, artwork, tap-to-queue)
- [x] Stream resolution over the app's own InnerTubeXResolver (demo track deleted)
- [ ] Emulator-verified playback: tap a song, PLAYING state, advancing position

## Stage 2 - player experience
- [ ] Full now-playing screen: artwork, title/artist, seek bar with position/duration,
      play/pause/next/prev, shuffle/repeat, expand from a mini-player bar
- [ ] Queue screen: up-next list, tap to jump, remove/reorder
- [ ] Sleep timer basics (if cheap), playback speed/pitch later

## Stage 3 - search
- [ ] Online search screen: query field, live suggestions, results grouped
      (songs/albums/artists/playlists), tap song to play, tap others to detail pages
- [ ] Recent searches (local persistence)

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
