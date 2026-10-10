# SCROLL_FLUIDITY Hard-Fix — Behavior Report

Status: Delivered  
Date: 2026-10-10  
Related: ADR-0107, Pass 4–7 audit

## Problems closed

1. **`loadAlbums` stomped alpha window** — full `getAllAlbums()` dump raced with
   `loadAlphaAlbums` / `loadMoreAlphaAlbums` (init, offline flip, connect).
2. **Empty band** — NavHost `padding(top)` left dead Background when header
   collapsed (design regress vs YTM reclaim).
3. **Blind tests** — alpha path still stubbed `getAllAlbums`.

## Contracts

### Alpha ownership

- `alphaBrowseActive` set on `loadAlphaAlbums`; `loadAlbums` early-returns browse
  publish (and mid-flight re-check).
- Offline flip reloads via `loadAlphaAlbums` when alpha active.
- Shared `alphaBrowseMutex` + `alphaBrowseGen` for load / more / API republish.
- Empty first page publishes `albums = emptyList()` (clears stale dump).
- Compose state never receives full API catalog; Room may still be warmed in heap
  during sync (documented follow-up).

### Header / list layout

- NavHost **full height** (no top pad).
- `LocalAppHeaderContentPadding` → Home / Library tabs / Search / Favorites Lazy
  `contentPadding(top)`.
- Overlay `AppHeader` + `graphicsLayer` translation; list scrolls into header band
  when collapsed.
- Library Albums grid: `LazyLayoutCacheWindow(0.5 / 0.2)`.

### Measurement

- Macrobenchmark asserts scroll nodes exist (no silent skip).

## Verification

- `./gradlew :app:assembleDebug`
- Unit: `LibraryViewModelTest` alpha/stomp/loadMore; `AppHeaderContentPaddingTest`;
  `AppHeaderScrollStateTest`; `LibraryPagingTest`

## Manual

- Albums fling after offline toggle — stays windowed (≤60 then append).
- Collapse header — list visible in top band; Settings not tappable when collapsed.
- Home fling — no empty strip above content.
