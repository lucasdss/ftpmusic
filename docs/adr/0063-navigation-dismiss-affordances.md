# ADR 0063 — Navigation Dismiss Affordances

Date: 2026-10-03
Status: Accepted
Related: ADR-0054 (detail chrome), ADR-0055 (navbar IA), ADR-0056 (settings stack-back),
docs/NAVIGATION_DISMISS_BEHAVIOR_REPORT.md, docs/DESIGN_CONSISTENCY_BEHAVIOR_REPORT.md

## Context

FTP Music mixed top-back (detail routes) with swipe-down minimize (Now Playing).
Settings kept AppHeader gear selected chrome and had no in-screen back, unlike
Profile / customMixes. Risk: treat dual pattern as bug, or leave Settings
visually orphaned.

Market (Spotify / Apple Music / YT Music): content stack = back chrome; full
player = overlay demote via swipe-down + down control.

## Decision

1. **Taxonomy (locked):**
   - **PUSH_WITH_BACK** — `DetailBackButton` (ChevronLeft) + system Back → `popBackStack`.
   - **PLAYER_SWIPE_MINIMIZE** — swipe-down and/or ↓ chevron → pop `nowplaying` → mini bar.
   - **SHEET** — M3 / in-player sheet swipe; not a nav destination.
   - **TAB** — no back chrome.
2. **Settings chrome:** treat as PUSH_WITH_BACK. Hide `AppHeader` on `settings`
   (`HEADER_HIDDEN_EXACT`). Title row = `DetailBackButton` + "Settings".
   Entry still `navigateToSettings()` / ADR-0056 stack policy.
3. **Non-goals:** do not replace player ↓ with ChevronLeft; do not add
   swipe-dismiss on album/artist/playlist; lyrics Close-only unchanged this pass.

## Consequences

- Dual dismiss documented as intentional, market-aligned.
- Settings visually matches Profile; no stacked logo+gear+back.
- Gear remains entry from primary-tab AppHeader only.
- Supersedes ADR-0054/0055 implication that Settings keeps AppHeader while
  visible on that route.
