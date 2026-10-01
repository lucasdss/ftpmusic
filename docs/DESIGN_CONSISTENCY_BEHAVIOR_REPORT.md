# Design Consistency Behavior Report

Caveman terse. Post ADR-0054 visual pass. **Navbar tab list unchanged** (gate).

## Before → After (chrome matrix)

| Surface | Before | After |
|---------|--------|-------|
| Primary tabs (Home/Library/Favorites/Search/Settings) | AppHeader + content | Same |
| Album detail | No AppHeader; circle ChevronLeft | Same via `DetailBackButton` |
| Artist / Playlist / Genre / Mix / Profile / CustomMixes | AppHeader **+** back bar | **No AppHeader**; `DetailBackButton` |
| Full player | No AppHeader | Same |
| Queue (prod) | PlayerBar bottom sheet | Same (`queue_sheet`) |
| Queue (tests) | M3 default TopAppBar island | `SurfaceElevated` + brand tokens; KDoc points to sheet |

## Tokens

- Brand accents: `BrandTeal` / `BrandPurple` (was inline `#00C8B4` / `#B040E8`).
- Surfaces: `Background`, `BrandBg`, `Surface`, `SurfaceElevated`, `SurfaceChip`.
- Search genre purple `#7C4DFF` → `BrandPurple`.
- Shared: `SegmentedChip` (teal fill selected), `DetailBackButton`.

## Segmented controls

Library tabs + Favorites Liked/Disliked + Profile period chips → one teal-active language.

## Navbar (NOT changed — awaiting OK)

Current (shipped):

1. Home (BarChart)
2. Library
3. Favorites ← **kept** (product lock)
4. Search
5. Settings

### Proposal (implement only after explicit OK)

| Slot | Today | Proposed |
|------|-------|----------|
| 1 | Home (BarChart) | Home (**House**) |
| 2 | Library | **Search** |
| 3 | Favorites | Library |
| 4 | Search | **Favorites** (role unchanged) |
| 5 | Settings | **Off bar** → gear in AppHeader |

Fallback if Settings must stay: keep 5 tabs; only reorder + House icon.

Favorites remains a first-class bottom tab in both variants.

## Out of scope this pass

- Tab list / order / icons / Settings placement (Phase 2 gate).
- Full typography migration to Outfit/Inter everywhere.
- Exhaustive replacement of every non-brand gray hex.
