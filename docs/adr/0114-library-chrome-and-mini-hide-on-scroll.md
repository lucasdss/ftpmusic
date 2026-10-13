# ADR 0114 — Library Chrome Translate + Mini Hide-on-Scroll

Date: 2026-10-10  
Status: Accepted (amended 2026-10-14)  
Related: ADR-0097, ADR-0107, ADR-0070  
Behavior: `docs/LIBRARY_SCROLL_CHROME_BEHAVIOR_REPORT.md`

## Context

After ADR-0107 hard-fix, Library/Favorites chrome sat under a **fixed** top inset
while AppHeader translated away — empty band, chips no longer “ride” to the top.
Users wanted prior reclaim feel plus more list space via hiding the mini player
while scrolling. Stock Spotify / YouTube Music keep the mini fixed during list
fling; this ADR records an intentional product divergence for mini only.

Paint-only `fillMaxSize` + `translationY = -offset` left a bottom Background strip
≈ `offsetPx`. Mini hide expanded Scaffold content into that strip; mini reveal left
the strip above the mini — unacceptable black bar.

`5d2fb5d` paired over-measure with parent `clipToBounds()` after `padding(top)`,
which fixed the bottom strip but clipped upward reclaim — top Background row hid
Library tabs. Clip **must precede** top padding (`appHeaderRidingClipHost`).

## Decision

1. **Chrome + list translate** — Keep fixed `LocalAppHeaderContentPadding`. Expose
   `LocalAppHeaderOffsetPx` (stable reader over `AppHeaderScrollState.offsetPx`).
   Library chips/search and Favorites mode chips **plus** the list body share
   `Modifier.appHeaderRidingContent`: measure child at `H + maxHeaderPx` (fixed from
   header pad), report layout `H`, `graphicsLayer { translationY = -offset }`, parent
   `appHeaderRidingClipHost` (`clipToBounds` **before** `padding(top)`). Occupies
   collapsed header band without per-frame Lazy remasure, no mid/top Background gap,
   **and no bottom paint strip**.
2. **Mini hide-on-scroll** — Shell `MiniPlayerScrollVisibility`: any vertical
   nested-scroll activity hides the mini slot (discrete height animation);
   **200ms** idle debounce reveals it. Route change forces visible. Bottom nav
   stays fixed.
3. **Amend ADR-0097 §1** — “Bottom nav stays fixed; mini may hide while
   primary-tab lists scroll.” Top enterAlways + ADR-0107 fixed inset unchanged.
4. **Market honesty** — Docs must not claim Spotify/YTM parity for mini hide.

## Consequences

- Library/Favorites regain YTM-like chrome reclaim without Pass-4 hitch.
- Mid-row, top, and bottom Background gaps under translated chrome are avoided
  when clip precedes header pad.
- Scaffold bottom padding is scroll-stable (not tied to header offset); mini
  show/hide `AnimatedVisibility` may remeasure during its short tween only.
- Horizontal nested scroll ignored for mini (same rule as header).
- Lazy viewport height is `H + maxHeaderPx` constantly while header pad is set —
  still stable during fling (extra not tied to live `offsetPx`).
