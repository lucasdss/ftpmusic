# AUTO_CENTER_NOW_PLAYING — Behavior Report (caveman)

**ADR:** 0126  
**Pref:** `list_auto_center_now_playing` · default **ON**  
**SoT:** `ListChromePrefs.autoCenterNowPlaying` → `LocalListChromePrefs`

## Market

| App | Playlist/Album | Queue |
|-----|----------------|-------|
| Spotify | highlight only | keep current in view |
| Apple Music | highlight / limited follow | center current context |
| YouTube Music | highlight; user owns scroll | follow current |
| Amazon / Tidal | similar split | queue follows |

Norm: follow playback-context lists; never yank during user scroll. Toggle rare in market — we ship configurable, default ON.

## Contract

- Gate: Settings → Appearance → "Scroll lists to now playing"
- Surfaces: Daily Mix, Playlist, Album, Artist tracks tab, Queue sheet
- Scroll when: pref ON + `currentTrackId` in **loaded** list
- Triggers: enter / `currentTrackId` change
- Suppress: ~3s after user drag (lyrics pattern); **wait loop** — extended suppress does not drop follow. Queue reorder = user scroll
- Center: `animateScrollToItem` + viewport offset; row-size estimate skips hero outliers; else near-top
- `onlyIfNotVisible`: skip yank when target already near middle of viewport (queue + detail / config restore)
- Paused: still scroll by `currentTrackId` (highlight may stay play-gated)
- Missing id / empty / artist paging gap: no-op (no infinite loadMore)
- Queue: keep pinned `now-playing-anchor`; re-scroll to anchor only if not reasonably visible

## Helper

`ScrollToNowPlayingEffect` + pure index/offset/visibility fns in `ui/components/ScrollToNowPlaying.kt`.

## Edge

- rapid skips → `LaunchedEffect` cancel/coalesce
- hero headers → lazy index = headerCount + trackIndex
- process death → SecureStorage hydrate; `rememberSaveable` suppress; skip if restored near target
- pref OFF mid-session → effect stops, no jump
- layout wait ~50 frames before silent no-op
