# ADR 0127 — Shell Chrome Density (Nav Bar + AppHeader)

Date: 2026-10-12
Status: Accepted
Related: ADR-0103 (UI density), ADR-0049 (nav labels), ADR-0097 (AppHeader scroll)
Supplements: ADR-0103 (spacing/icons/art scale — now includes shell chrome height)

## Context

`UiDensityPreset` and hide-nav-labels scaled icons/text via `adp`/`asp`, but Material3
`NavigationBar` / `NavigationBarItem` kept `defaultMinSize(minHeight ≈ 80.dp)`.
AppHeader logo stayed hardcoded `56.dp`. Users saw no chrome height reclaim after
Tiny + hide labels.

## Decision

1. **Tokens:** `navBarHeight(hideLabels)`, `appHeaderLogoSize` / `appHeaderLogoCorner`,
   `interactiveFloor` (≥48.dp) in `Dimens.kt`.
2. **Nav:** Explicit `Modifier.height(navBarHeight)` on bar **and** each item.
   `windowInsets = WindowInsets(0)` because parent Column already applies
   `navigationBarsPadding()`.
3. **Height source:** Same `hideNavLabels` drives label nullability and height
   (labeled = `adp(80)` floored; icon-only = `adp(56)` floored).
4. **AppHeader:** Logo, corners, spacers, lineHeights use density tokens; gear hit
   uses `interactiveFloor(minTouchTarget())`.
5. **Measure remains source of truth** for header list inset (`onSizeChanged`).

## Consequences

- Tiny icon-only bar floors at 48.dp (a11y), not 44.8.
- Labeled bar stays taller than icon-only at every density.
- Double system-inset padding on the bar is removed.
- Detail `TopAppBar` density deferred.
