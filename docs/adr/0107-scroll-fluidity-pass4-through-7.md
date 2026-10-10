# ADR 0107 — Scroll Fluidity Passes 4–7

Date: 2026-10-10  
Status: Accepted  
Related: ADR-0096, ADR-0097, ADR-0102, ADR-0037  
Research: `docs/SCROLL_FLUIDITY_MARKET_RESEARCH.md`  
Behavior: `docs/SCROLL_FLUIDITY_PASS4_BEHAVIOR_REPORT.md`

## Context

After Passes 1–3 (Coil hygiene, SongListRow, contentType, EQ pause, enterAlways
header), residual fling hitch remained. Research pointed at:

1. Per-frame AppHeader **layout height** reclaim forcing Lazy remeasure.
2. No Macrobenchmark / Baseline Profile.
3. Compose BOM pre-1.9 (no `LazyLayoutCacheWindow`).
4. Library full-catalog dump into Compose state.
5. Nested LazyRow / decode RAM residual cost.

## Decision

### Pass 4 — Header viewport + measurement

- Shell uses **overlay header** + **fixed top inset** = full `headerHeightPx`
  (never `height - offset` per frame). Collapse is `graphicsLayer` translation
  only (ADR-0097 enterAlways math unchanged).
- Add `:macrobenchmark` with Home / Library fling `FrameTimingMetric` and
  Baseline Profile generator; app depends on `profileinstaller`.

### Pass 5 — Platform

- Compose BOM → `2025.08.00` (Compose Foundation 1.9).
- Home: `rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction=0.5f, behindFraction=0.2f))`.

### Pass 6 — Library windowing

- Albums alpha browse uses Room `LIMIT/OFFSET` (`getAlbumsPaged` /
  `getOfflineAlbumsPaged`) with page size 60; near-end `loadMoreAlphaAlbums`.
- Background API still refreshes Room; UI republishes only the loaded window.

### Pass 7 — Image / nested cost

- List `CoverArtImage` uses `RGB_565`.
- Library grid cover URL size matches decode (160).
- Recently Added (≤10) uses `Row` + `horizontalScroll` instead of nested
  `LazyRow`.
- Coil pause-on-fling **not** shipped (needs measured blanking tradeoff).

## Consequences

- Content no longer grows into header space mid-scroll (reserved inset stays).
- Large libraries no longer hold full album list in Compose state on first paint.
- Baseline Profile generation requires a connected device
  (`:app:generateBaselineProfile`).
- BOM bump may surface Compose API deprecations — fix on compile.
