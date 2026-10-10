# ADR 0097 — AppHeader EnterAlways Scroll (YT Music)

Date: 2026-10-08  
Status: Accepted  
Related: ADR-0054 (header route visibility), ADR-0096 (scroll FPS)

## Context

Primary-tab `AppHeader` was always fully visible, wasting vertical space while
scrolling. Users expect YouTube Music behavior: scroll down hides the top bar;
scroll up reveals it immediately (enterAlways). Detail routes keep their own
chrome (choice A).

## Decision

1. **Shell only** — `NestedScrollConnection` in `FtpmusicNavHost` around
   primary-tab content. Bottom nav stays fixed. Mini may hide while primary-tab
   lists scroll (ADR-0114); idle debounce reveals it.
2. **Continuous offset** — `AppHeaderScrollState.offsetPx` in
   `0..headerHeight`; layout height = `height - offset` + clip +
   `translationY = -offset` so content reclaims space (no empty gap).
3. **Finger-follow** — `onPreScroll` updates offset synchronously; consume only
   the delta applied to the header. Horizontal nested scroll ignored.
4. **Settle** — `onPostFling` snaps fully shown or fully hidden with a short
   spring; mid-drag never animates against the finger.
5. **Reset** — instant `offset = 0` on route/tab change.
6. **Hits** — Settings clickable only when expanded (`offset < 4px`).

## Consequences

- Home / Library / Favorites / Search gain content height while flinging down.
- Detail TopAppBars / heroes unchanged (ADR-0054).
- Smoothness depends on ADR-0096 Lazy cell hygiene (no FittingText / unsized
  Coil on hot scroll paths).

### Amendment (ADR-0107 Pass 4)

Continuous **layout height** reclaim was a residual fling hitch. Shell now keeps
a fixed top inset = full header height and moves the header with
`graphicsLayer` only — enterAlways math unchanged; content no longer grows into
header space mid-drag.

### Amendment (ADR-0114)

Library/Favorites chrome above Lazy rides `-offsetPx` via `graphicsLayer` so
chips reclaim the collapsed header band without resizing the Lazy viewport.
Mini player hide-on-scroll is discrete (200ms idle reveal); nav remains fixed.
