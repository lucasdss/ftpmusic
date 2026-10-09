# SCROLL_FPS — Behavior Report

Status: Delivered (pass 3)  
Date: 2026-10-09  
Related: ADR-0102, ADR-0096, ADR-0097, ADR-0036, ADR-0037, ADR-0027  
Also: `docs/HOME_SCROLL_PERF_BEHAVIOR_REPORT.md`, `docs/LIBRARY_SCROLL_PERF_BEHAVIOR_REPORT.md`

## Symptom

Continuous low FPS on lists (music off). Uneven song title sizes. Header always
visible on primary tabs. Pass 3: subtle vertical fling hitch remaining after Pass 2.

## Pass 1 (d574855)

- Coil `crossfade(false)`; composition `existsNonEmpty`
- `SongListRow` on Album/Playlist/Mix/Artist/Favorites/Search/Downloads/Home songs
- Search/Genre: no global `cacheVersionState`

## Pass 2

### Residual FPS closed

- Queue / history: fixed-size titles + `CoverArtImage`
- AddSongs: `SongListRow` + sized cover
- Search albums/playlists: `CoverArtImage` + fixed Text
- Favorites albums: sized cover + fixed Text
- Genre album grid labels: fixed Text

### AppHeader YT Music (ADR 0097)

- Primary tabs only: scroll down collapses header (layout height reclaim);
  scroll up reveals (enterAlways); fling end snaps; tab reset expands.
- Bottom chrome fixed. Smoothness: sync offset, no per-frame coroutine,
  isolated `mutableFloatStateOf`.

## Pass 3 (ADR-0102)

- Home: LazyListState, `contentType`, card-sized decode, hoisted `take(10)`,
  fixed Text on scroll-path titles, EQ pauses while scrolling.
- Library: FittingText → fixed Text on Artists/Playlists/Radio; EQ scroll gate.
- Favorites / Queue: `contentType` on vertical items.
- Shared `ScrollAwareEqBars(scrollInProgress)`.

## Edge cases

- Long titles → 2 lines → ellipsis (uniform size)
- Header fully collapsed → list scrolls freely
- Horizontal Search chips → header ignore
- Detail route → header hidden by ADR-0054; offset reset
- EQ freezes mid-fling (intentional)

## Verification

- `./gradlew :app:assembleDebug`
- `AppHeaderScrollStateTest`, `SongListRowComposeTest`, CoverArt* tests,
  `ScrollAwareEqBarsTest`
- Manual: fling Home/Library/Search — header tracks finger, no gap/jump
