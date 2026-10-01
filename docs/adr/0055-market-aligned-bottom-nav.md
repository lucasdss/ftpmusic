# ADR 0055 — Market-Aligned Bottom Nav (Favorites Kept)

Date: 2026-10-01
Status: Accepted
Related: ADR-0014 (no header Search), ADR-0054 (chrome),
docs/NAVBAR_IA_PROPOSAL.md, docs/DESIGN_CONSISTENCY_BEHAVIOR_REPORT.md

## Context

Five bottom tabs (Home/BarChart, Library, Favorites, Search, Settings) crowded
the bar and put Search late vs Spotify / YT Music / Apple Music. Product requires
**Favorites as a bottom-tab peer**. Settings does not need peer-tab status.

## Decision

1. **Tabs (order):** Home (**House**) → Search → Library → Favorites.
2. **Settings off-bar:** gear in `AppHeader` navigates to `settings` (same route;
   banners / Profile / sync returnTo still work).
3. **Favorites kept** as first-class bottom tab.
4. **Search stays a tab only** — no header Search pill (ADR-0014 unchanged).

## Consequences

- Four-tab bar closer to market IA while preserving Liked as a dedicated tab.
- Settings discoverability moves to header gear (teal when on `settings`).
- No bottom-tab highlight while on Settings (none of the four routes match).
