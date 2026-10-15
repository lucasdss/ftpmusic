# ADR 0143 — Home/Search Mini Reveal List Bottom Padding

Date: 2026-10-14  
Status: Accepted (amended 2026-10-15)  
Related: ADR-0114, ADR-0107, ADR-0070  
Behavior: `docs/HOME_MINI_REVEAL_BEHAVIOR_REPORT.md`

## Context

ADR-0114 added discrete mini hide-on-scroll and Library/Favorites riding clip so
header collapse does not leave a bottom paint strip above nav/mini. Home and
Search originally used ADR-0107 top-only `LocalAppHeaderContentPadding` on Lazy
lists. Home later joined the riding path for opaque header reclaim (ADR-0114
amend 2026-10-15); Search remains top-only pad.

Scaffold remasures content when mini `AnimatedVisibility` expands/shrinks.
At list end after hide, reveal shrinks the viewport by ~`miniPlayerHeight()` while
LazyList keeps `firstVisibleItem` — last items clip and look blocked by the mini.
`scrollBy` compensation re-enters nested scroll and re-hides the mini (loop).

## Decision

1. **Reserve mini height in list scroll extent** — When a track is loaded
   (`PlaybackState.isVisible` and not full-player/login), shell provides
   `LocalMiniPlayerBottomContentPadding = miniPlayerHeight()` via
   `resolveMiniPlayerBottomContentPadding`. Independent of
   `MiniPlayerScrollVisibility.visible` so the pad absorbs reveal remasure.
2. **Consumers** —
   - **Home:** riding clip-host clears the header band; Lazy
     `contentPadding = PaddingValues(top = spacingS(), bottom = miniBottomPad)`.
   - **Search:** still ADR-0107 top pad —
     `PaddingValues(top = headerPad, bottom = miniBottomPad)`.
3. **Amend ADR-0114** — Riding Home still keeps the mini bottom reserve; Search
   (top-only header pad) requires it. Scaffold bottom inset alone is insufficient
   at scrolled-to-end reveal for these long Lazy surfaces.
4. **No scroll compensation** — Do not use `scrollBy`/`animateScrollBy` for this
   delta (nested-scroll hide feedback).

## Consequences

- Last Home/Search items remain readable above mini after idle reveal.
- ~miniHeight trailing blank at list end while a track is loaded (resting with
  mini shown) — accepted.
- Home opaque Settings reveal is an ADR-0114 concern (riding Background), not
  this ADR; mini pad remains independent.
- Pure helper keeps unit coverage without Robolectric for the rule itself.
