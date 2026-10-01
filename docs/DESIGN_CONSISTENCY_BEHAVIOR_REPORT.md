# Design Consistency Behavior Report

Caveman terse. Post ADR-0054 visual pass + ADR-0055 navbar.

## Before → After (chrome matrix)

| Surface | Before | After |
|---------|--------|-------|
| Primary tabs | AppHeader + content | Home/Search/Library/Favorites + **Settings gear** |
| Settings | Bottom tab #5 | **Off-bar** via AppHeader gear; route unchanged |
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

## Navbar (ADR-0055 shipped)

1. Home (House)
2. Search
3. Library
4. Favorites ← **kept** (product lock)
5. Settings → AppHeader gear (not a tab)

## Out of scope

- Full typography migration to Outfit/Inter everywhere.
- Exhaustive replacement of every non-brand gray hex.
