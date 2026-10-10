# ADR 0114 — Library Chrome Translate + Mini Hide-on-Scroll

Date: 2026-10-10  
Status: Accepted  
Related: ADR-0097, ADR-0107, ADR-0070  
Behavior: `docs/LIBRARY_SCROLL_CHROME_BEHAVIOR_REPORT.md`

## Context

After ADR-0107 hard-fix, Library/Favorites chrome sat under a **fixed** top inset
while AppHeader translated away — empty band, chips no longer “ride” to the top.
Users wanted prior reclaim feel plus more list space via hiding the mini player
while scrolling. Stock Spotify / YouTube Music keep the mini fixed during list
fling; this ADR records an intentional product divergence for mini only.

## Decision

1. **Chrome translate** — Keep fixed `LocalAppHeaderContentPadding`. Expose
   `LocalAppHeaderOffsetPx` (stable reader over `AppHeaderScrollState.offsetPx`).
   Library chips/search and Favorites mode chips apply
   `graphicsLayer { translationY = -offset }` so chrome occupies the collapsed
   header band without Lazy remeasure.
2. **Mini hide-on-scroll** — Shell `MiniPlayerScrollVisibility`: any vertical
   nested-scroll activity hides the mini slot (discrete height animation);
   **200ms** idle debounce reveals it. Route change forces visible. Bottom nav
   stays fixed.
3. **Amend ADR-0097 §1** — “Bottom nav stays fixed; mini may hide while
   primary-tab lists scroll.” Top enterAlways + ADR-0107 fixed inset unchanged.
4. **Market honesty** — Docs must not claim Spotify/YTM parity for mini hide.

## Consequences

- Library/Favorites regain YTM-like chrome reclaim without Pass-4 hitch.
- Scaffold bottom padding changes only on discrete mini show/hide (not per frame).
- Horizontal nested scroll ignored for mini (same rule as header).
