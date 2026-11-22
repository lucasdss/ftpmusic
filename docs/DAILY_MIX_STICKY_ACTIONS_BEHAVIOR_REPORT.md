# Daily Mix sticky Play / Shuffle / options — behavior report

## Goal

When opening a Daily Mix from Home (`mix/{mixId}` → `MixDetailScreen`), Play,
Shuffle, and ⋮ stay pinned under the fixed title while the track list scrolls —
Library-style chrome (chips/search stay above the list on `LibraryScreen`).

## Behavior

| Region | Scroll |
|--------|--------|
| Back + mix title (+ pending banner) | Fixed |
| Cover art + track count | Scrolls away |
| Play \| Shuffle \| ⋮ | **Sticky** (`LazyColumn.stickyHeader`) |
| Track rows | Scroll under sticky actions |

Opaque sticky background uses a short `bgTop → bgMid` gradient (ArtBackdrop upper
stops) so the pinned bar is not a flat mid band over the title region.

Now-playing auto-center uses `MIX_DETAIL_LAZY_HEADER_COUNT = 2` (cover + sticky
actions) via `resolveLazyIndex`, and passes measured sticky height as
`ScrollToNowPlayingEffect(topInsetPx=…)` so center math stays in the usable band.

Compose coverage: `MixDetailComposeTest` (render, play/shuffle/more, sticky after
swipe, loadMix, playTrack).

## Out of scope

Album / Playlist / Artist detail action pinning (unchanged; still scroll-away
headers unless requested later).
