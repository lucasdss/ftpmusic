# SCROLL_FPS — Behavior Report

Status: Delivered (pass 2)  
Date: 2026-10-08  
Related: ADR-0096, ADR-0097, ADR-0036, ADR-0037, ADR-0027

## Symptom

Continuous low FPS on lists (music off). Uneven song title sizes. Header always
visible on primary tabs.

## Pass 1 (d574855)

- Coil `crossfade(false)`; composition `existsNonEmpty`
- `SongListRow` on Album/Playlist/Mix/Artist/Favorites/Search/Downloads/Home songs
- Search/Genre: no global `cacheVersionState`

## Pass 2 (this)

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

## Edge cases

- Long titles → 2 lines → ellipsis (uniform size)
- Header fully collapsed → list scrolls freely
- Horizontal Search chips → header ignore
- Detail route → header hidden by ADR-0054; offset reset

## Verification

- `./gradlew :app:assembleDebug`
- `AppHeaderScrollStateTest`, `SongListRowComposeTest`, CoverArt* tests
- Manual: fling Home/Library/Search — header tracks finger, no gap/jump
