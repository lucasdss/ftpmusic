# Navbar IA Proposal (Favorites kept)

Status: **AWAITING EXPLICIT OK** — do not edit `NavHost.kt` `tabs = listOf(...)` until approved.

Related: ADR-0054, `docs/DESIGN_CONSISTENCY_BEHAVIOR_REPORT.md`, ADR-0014.

## Locked

- Favorites / Liked stays on the bottom navigation bar as a peer tab.

## Current (shipped, unchanged)

1. Home — `BarChart`
2. Library — `MusicNote`
3. Favorites — `Favorite`
4. Search — `Search`
5. Settings — `Settings`

## Recommended (market-aligned, Favorites kept)

1. Home — **House** icon (reads “listen now”, not stats)
2. **Search** (earlier, like Spotify / YTM)
3. Library
4. **Favorites** (unchanged role)
5. Settings **removed from bar** → gear icon in `AppHeader` (Profile still under Settings route)

Deep links / banners that navigate to `settings` keep working.

## Alternate (if Settings must remain a tab)

Keep 5 tabs; only:

1. Home (House)
2. Search
3. Library
4. Favorites
5. Settings

## Approve by saying

- `OK navbar recommended` — implement recommended (Settings off-bar + reorder + House)
- `OK navbar alternate` — reorder + House only, keep Settings tab
- `Keep navbar as-is` — no Phase 2 code
