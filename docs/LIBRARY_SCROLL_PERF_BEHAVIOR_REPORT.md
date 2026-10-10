# Library Scroll Perf — Behavior Report

Date: 2026-10-09
Related: ADR-0102, ADR-0096, ADR-0037, ADR-0097

## Structure

Tabbed surfaces: Albums `LazyVerticalGrid`; Artists / Playlists / Radio
`LazyColumn`. Albums alpha browse is windowed (ADR-0107 Pass 6); full dump removed.

## Pass 3 contracts (vertical fling)

- Albums: `key` + `contentType` + `decodeSize = 160.dp` (unchanged).
- Artists / Playlists / Radio rows: fixed `Text` + ellipsis (no FittingText).
- Active-album EQ bars pause while grid/list `isScrollInProgress`.
- Tab state slices keep unrelated Home ticks from recomposing Library.

## Pass 6 contracts (windowed alpha)

- First page `LibraryPaging.ALPHA_PAGE_SIZE` (60) from Room `LIMIT/OFFSET`.
- Near-end → `loadMoreAlphaAlbums`; search mode disables append.
- Background API refreshes Room then republishes loaded window only.
- Hard-fix: `loadAlbums` must not stomp `_state.albums` while alpha browse active.

## Manual check

Fling Library Albums + Artists with music on — subtle hitch reduced vs Pass 2;
EQ freezes mid-fling (acceptable). Large libraries: first paint not full dump.
