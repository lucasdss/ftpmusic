# Library Scroll Perf — Behavior Report

Date: 2026-10-09
Related: ADR-0102, ADR-0096, ADR-0037, ADR-0097

## Structure

Tabbed surfaces: Albums `LazyVerticalGrid`; Artists / Playlists / Radio
`LazyColumn`. Full catalog dump remains (Paging deferred ADR-0037).

## Pass 3 contracts (vertical fling)

- Albums: `key` + `contentType` + `decodeSize = 160.dp` (unchanged).
- Artists / Playlists / Radio rows: fixed `Text` + ellipsis (no FittingText).
- Active-album EQ bars pause while grid/list `isScrollInProgress`.
- Tab state slices keep unrelated Home ticks from recomposing Library.

## Manual check

Fling Library Albums + Artists with music on — subtle hitch reduced vs Pass 2;
EQ freezes mid-fling (acceptable).
