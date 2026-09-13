# 0036 — Scoped Cover Cache Invalidation

Status: Accepted
Date: 2026-09-11

## Context

`CoverArtFallbackService` bumped a single Compose `cacheVersionState` on every
disk write. Home `LazyRow` cells (`rememberPreferredCoverArt`, `ArtistAvatar`)
all read that counter, so caching one favorite-artist image recomposed every
visible album/mix/playlist cover and re-ran `looksLikeImage` on the main
thread mid-fling.

## Decision

1. Per-key `MutableIntState` map (`ConcurrentHashMap`). Keys match disk
   identity prefixes: `artist|name`, `artist|album`, `navidrome|<coverArtId>`.
   Compose cells read only their own state's `.intValue` (SnapshotStateMap
   was rejected — map writes still fanout-invalidated readers).
2. UI observes only keys relevant to that cell via `observeVersion(key)`.
3. `cacheVersionState` bumps only on `clearCache()` (full wipe), not per write.
4. Home Daily Mix / playlist cards bind a **single primary cover** on LazyRow
   (detail screen may keep 4-tile montage).

## Consequences

- Artist fetch no longer poisons album/mix fling.
- Cells still refresh when *their* file lands.
- Clear-cache still forces a broad refresh via global generation.
- See `docs/HOME_SCROLL_PERF_BEHAVIOR_REPORT.md`.
