# ADR 0054 — Design Tokens and Detail Chrome Consistency

Date: 2026-10-01
Status: Accepted
Related: ADR-0014 (header Search removed), ADR-0049 (nav labels),
docs/DESIGN_CONSISTENCY_BEHAVIOR_REPORT.md, docs/NAVBAR_IA_PROPOSAL.md

## Context

Theme.kt declared `BrandTeal` / `BrandPurple` / `Background` but UI used ~250
inline hex copies. Detail routes stacked `AppHeader` + screen TopAppBar (album
only hid the logo bar). Library chips used gray-selected; Favorites used teal.
Back affordances forked (ChevronLeft circle vs ArrowBack vs Close). Production
queue lives in `PlayerBar` sheet; `QueueScreen` was an M3-default island used
by tests.

## Decision

1. **Tokens:** UI references Theme tokens (`BrandTeal`, `BrandPurple`,
   `Background`, `Surface`, `SurfaceElevated`, `SurfaceChip`, `NavUnselected`,
   …) instead of inline brand hex. Search genre accent uses `BrandPurple`
   (was `#7C4DFF`).
2. **AppHeader:** shown on primary tabs only. Hidden on detail routes:
   `album/`, `artist/`, `playlist/`, `genre/`, `mix/`, `profile`,
   `customMixes`, plus splash/connect/nowplaying/syncing/rebuildmix.
3. **Back:** shared `DetailBackButton` (circle + ChevronLeft) on detail
   screens; `inset=false` when embedded in TopAppBar / title rows.
4. **Chips:** shared `SegmentedChip` / `SegmentedChipRow` — teal fill when
   selected (Library tabs, Favorites modes, Profile period).
5. **Queue:** PlayerBar `queue_sheet` is canonical production UI. `QueueScreen`
   remains for tests; styled with same brand surfaces and documented as
   non-nav.

## Non-goals (this ADR)

- Bottom nav tab order / icons / Settings placement — **ADR-0055** (Home→Search→Library→Favorites; Settings = header gear).

## Consequences

- One visual language for colors, segmented controls, and detail back chrome.
- Nested logo + back bar no longer stacks on drill-downs.
- Navbar IA shipped in ADR-0055; Settings stack-back + 48dp in ADR-0056.
