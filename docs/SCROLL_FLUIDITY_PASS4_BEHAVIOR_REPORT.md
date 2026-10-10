# SCROLL_FLUIDITY Passes 4–7 — Behavior Report

Status: Delivered  
Date: 2026-10-10  
Related: ADR-0107, ADR-0097, ADR-0037  
Research: `docs/SCROLL_FLUIDITY_MARKET_RESEARCH.md`

## Symptom (pre)

Subtle vertical fling hitch / low-refresh feel after Pass 3 list hygiene.

## Contracts shipped

### Pass 4a — Header viewport

- Primary-tab AppHeader overlays content; Lazy lists use **fixed**
  `contentPadding(top)` = full header height (`LocalAppHeaderContentPadding`).
- Collapse/reveal still enterAlways via nested scroll + `graphicsLayer`
  translation; snap on post-fling unchanged.
- Hard-fix: list scrolls into header band when collapsed (no empty strip).
  See `docs/SCROLL_FLUIDITY_HARDFIX_BEHAVIOR_REPORT.md`.

### Pass 4b — Measurement

- Module `:macrobenchmark` — `ScrollBenchmark` (Home / Library flings),
  `BaselineProfileGenerator`.
- App: `profileinstaller` + `baselineProfile(project(":macrobenchmark"))`.
- Root: `testTagsAsResourceId` for UiAutomator (`home_scroll`,
  `library_albums_grid`).

### Pass 5 — Compose platform

- BOM `2025.08.00` (Foundation 1.9).
- Home `rememberLazyListState(LazyLayoutCacheWindow(ahead=0.5, behind=0.2))`.

### Pass 6 — Library window

- First paint: ≤60 alpha albums from Room paged query.
- Near-end grid → `loadMoreAlphaAlbums`.
- Search results path unchanged (no loadMore while searching).

### Pass 7 — Images / nested

- `CoverArtImage` → `RGB_565`.
- Library grid cover fetch size 160 (matches decode).
- Recently Added: `Row` + horizontalScroll (≤10 cards).
- Fallback network fetch gated while `isScrollInProgress` on Home/Library covers.
- Coil pause-all-requests **not** shipped (blank-tile risk without device A/B).
- Home slim state-slice deferred (larger ViewModel cut; paging + header first).

## Verification

- `./gradlew :app:assembleDebug`
- Unit: `AppHeaderScrollStateTest`, `LibraryPagingTest`
- Device (optional): `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`
- Device (optional): `./gradlew :app:generateBaselineProfile`
- Manual release: fling Home/Library — header tracks finger; no mid-scroll
  viewport resize; albums append near end.

## Edge cases

- Header height 0 until first measure → inset 0 then jumps once (acceptable).
- Tab/route change → `resetExpanded()` instant.
- Offline albums use `getOfflineAlbumsPaged`.
- Coil pause-on-fling deferred (blank tiles risk).
