# ADR 0147 — Scroll Perf Pass 8: Consistent Prefetch + Likes

Date: 2026-10-18
Status: Accepted
Related: ADR-0107 (passes 4–7), ADR-0096 (image/row pipeline), ADR-0102 (pass 3)
Behavior: `docs/SCROLL_PERF_PASS8_BEHAVIOR_REPORT.md`

## Context

Passes 1–7 landed. Home + Library fling tuned. Residual inconsistency: detail
lists (Favorites, Artist, Playlist, Mix, Genre, Downloads, Search) still used
default `rememberLazyListState()` / `rememberLazyGridState()` — no viewport
prefetch. Likes rows over-decoded cover art (request 200, display 48). A few
scroll-path rows still used `FittingText` (shrink-to-fit remeasure), violating
ADR-0096. Favorites loadMore dedup was O(n*m) per append; near-end snapshotFlow
re-fired without `distinctUntilChanged`.

## Decision

Extend the ADR-0107 Pass 5/6/7 patterns uniformly, not one-offs:

1. **Prefetch** — add `LazyLayoutCacheWindow(aheadFraction=0.5f, behindFraction=0.2f)`
   to every primary vertical content list/grid (Favorites ×3, Artist tracks +
   albums grid, Playlist, Mix, Genre grid + artists, Downloads, Search).
2. **Decode alignment** — cover `size` (server thumb) == `decodeSize` (Coil):
   Favorites/Downloads 48dp rows, Artist/Genre 160dp grid cells, Home starred
   LazyRow cardDecode. Fling-cheap decode, half RAM on like rows.
3. **No FittingText on scroll rows** — fixed `Text(maxLines=1, ellipsis)` on
   repeated rows (Favorites artist/radio, Search genre/artist). Chrome
   FittingText (chips, headers, sheets) retained.
4. **loadMore hygiene** — hoist dedup id-set out of the filter (O(n+m));
   `distinctUntilChanged()` on near-end snapshotFlow (Favorites ×2, Downloads,
   Search).

## Threading

Audited. No change. Room suspend auto-dispatch + `viewModelScope` Main.immediate
is correct. Explicit `Dispatchers.IO` reserved for DailyMix CPU work. Parallel
DAO reads would serialize on Room's single query executor — rejected.

## Consequences

- Uniform prefetch window → uniform fling across all surfaces.
- Lower decode RAM + fewer redundant re-fires.
- Scroll-path typography consistent (ADR-0096 complete).
- Reorderable queue + settings CustomDailyMixes intentionally left at default
  cacheWindow (drag semantics / low volume).
- Behavior locked by tests: `loadMoreLiked dedups overlapping page append`.
