# Home Scroll Perf — Behavior Report

Date: 2026-10-09
Related: ADR-0102, ADR-0096, ADR-0036, ADR-0097

## Structure

Outer vertical `LazyColumn` + nested horizontal `LazyRow`s (Daily Mixes,
Playlists, Fav Artists/Albums/Radio, Recently Added). Tuned In = non-lazy
`FlowRow` inside a LazyColumn item.

## Pass 3 contracts (vertical fling)

- LazyListState owned; EQ bars pause while `isScrollInProgress`.
- Section items + LazyRow items use stable `key` + `contentType`.
- Cover decode size matches `albumCardWidth()` (not 300.dp default).
- Scroll-path titles use fixed `Text` + ellipsis (no FittingText remasure).
- `randomAlbums.take(10)` hoisted via `remember`.
- Single primary cover per mix/playlist card (no 4-tile montage).

## Manual check

Fling Home vertically with music on/off — header tracks; no EQ fighting fling;
titles uniform size.
