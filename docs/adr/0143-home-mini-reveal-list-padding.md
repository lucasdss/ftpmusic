# ADR 0143 — Home/Search Mini Reveal List Bottom Padding

Date: 2026-10-14  
Status: Accepted  
Related: ADR-0114, ADR-0107, ADR-0070  
Behavior: `docs/HOME_MINI_REVEAL_BEHAVIOR_REPORT.md`

## Context

ADR-0114 added discrete mini hide-on-scroll and Library/Favorites riding clip so
header collapse does not leave a bottom paint strip above nav/mini. Home and
Search still use ADR-0107 top-only `LocalAppHeaderContentPadding` on Lazy lists.

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
2. **Consumers** — Home and Search LazyColumns set
   `contentPadding = PaddingValues(top = headerPad, bottom = miniBottomPad)`.
3. **Amend ADR-0114 consequence** — Primary-tab lists that use top-only header
   pad (not riding clip) must also reserve mini bottom pad while a track is
   loaded; Scaffold bottom inset alone is insufficient at scrolled-to-end reveal.
4. **No scroll compensation** — Do not use `scrollBy`/`animateScrollBy` for this
   delta (nested-scroll hide feedback).

## Consequences

- Last Home/Search items remain readable above mini after idle reveal.
- ~miniHeight trailing blank at list end while a track is loaded (resting with
  mini shown) — accepted.
- Library/Favorites riding path unchanged.
- Pure helper keeps unit coverage without Robolectric for the rule itself.
