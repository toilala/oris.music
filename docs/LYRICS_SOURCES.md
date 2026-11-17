# Lyrics sources

Checked 17 November 2026. Oris implements its own adapters and parsers. The source list is scoped to Echo's nine built-in providers, not Mochy's additional providers.

| Source | Oris route and status | Default |
| --- | --- | --- |
| LRCLIB | Public search API. Real synced/plain responses verified. Artist, title/version, album and duration matching; synced candidates preferred. | On, first |
| Unison | Public, unsigned community API. Independent JSON/LRC/TTML adapter. Provider and submitter links are both required and displayed. Example lookups returned 404 during validation, not a successful coverage claim. Missing attribution fails closed. Unison permits people, non-profits and open-source projects, not companies. | On |
| Better Lyrics | Public keyless cache access through the existing boidu endpoint. Real TTML response verified. Uncached requests may require an extension-exclusive key; Oris does not supply or copy one. The newer api.betterlyrics.org address returned 403 in the check environment and is not used as a bypass. | On |
| KuGou | Uses Better Lyrics' documented keyless KuGou endpoint. Real line-synced LRC response verified. Labelled "KuGou via Better Lyrics" to avoid claiming a direct KuGou integration. No PC-client impersonation or internal KuGou access key logic. | On |
| YouLyPlus | Independent canonical V2 JSON reader. Primary endpoint returned 502; the mirror listed in the reference app returned 404. Adapter remains available for an explicit user retry, but no successful live coverage is claimed. No private key used. | Off until enabled |
| PaxSenix | Independent search, recording match, and TTML/enhanced-LRC/line-array reader. Honest Oris request returned 403. No retry using another app's identity. Adapter is optional for the user's own network; availability is unverified. | Off until enabled |
| YouTube Subtitle | Existing InnerTube transcript transport, adapted to the lyrics view. No audio stream is prepared for a lyrics request. Transcripts can include non-lyric speech and may be unavailable for a video. | On, after YouTube Music |
| YouTube Music | Existing lyrics-tab transport, adapted to the lyrics view. Plain-text fallback. Coverage depends on the track. | On, before Subtitle |
| SimpMusic | Not requested by Oris. Service explicitly returned a client restriction when tested with the honest Oris identity. The settings row explains why it is unavailable; it is not a fake enabled adapter. | Unavailable |

Automatic follows the user's saved order among enabled, usable sources. It prefers the first synced result and retains the first plain result if no timed result is found. Individual source selection respects disabled sources. A failed/blocked provider gets a short cooldown rather than repeated requests on every track. A normal 404 does not disable other songs from that provider.

Reorder arrows and enable switches are in Settings > Lyrics providers, also reachable from the Lyrics source menu. Preferences stay on the device. Successful lyrics are held only in a small memory cache. Requests have timeouts, response-size limits and cancellation. Plain lyrics are labelled as plain. TTML currently supplies line timing, not word-by-word highlighting.

No lrc.red integration or contact-email field is included. No personal contact email is included in the source or API requests. NetEase, QQ Music, Deezer, automatic-caption and module providers are outside the final Echo-only scope.

## Rights and access caveats

A source's open-source client/server license is not a license to the songwriters' lyrics. Availability of an API response is not proof of public redistribution or commercial display rights. Check those rights before a public/commercial release. Oris does not scrape around blocks, copy extension-only credentials, or impersonate SimpMusic, Mochy or Echo.

## Sources inspected

- Echo registry at commit cb398f13d8aa0fbaf7ea709221c0bccbcba612f0: https://github.com/EchoMusicApp/Echo-Music/blob/cb398f13d8aa0fbaf7ea709221c0bccbcba612f0/lyrics/src/main/kotlin/echo/music/iad1tya/lyrics/LyricsProviderRegistry.kt
- Echo adapters and provider modules in the same revision: https://github.com/EchoMusicApp/Echo-Music
- Mochy registry/adapters at commit 7e590802345329bb108d74f337af1ea9fe407d2a were reviewed for the initial audit only: https://github.com/realidkroo/Mochy-Music
- LRCLIB: https://lrclib.net/docs
- Better Lyrics API and provider-specific endpoint rules: https://docs.betterlyrics.org/reference/get-lyrics
- Better Lyrics authentication: https://lyrics-api-docs.boidu.dev/docs/authentication
- Unison API, usage scope and credit requirements: https://github.com/better-lyrics/unison and https://unison.boidu.dev/docs
- YouLyPlus endpoint contract and rights notice: https://github.com/ibratabian17/LyricsPlus/blob/gelato/docs/endpoints.md
- PaxSenix API: https://lyrics.paxsenix.org/
