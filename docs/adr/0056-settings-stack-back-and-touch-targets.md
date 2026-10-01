# ADR 0056 — Settings Stack-Back + 48dp Chrome Targets

Date: 2026-10-01
Status: Accepted
Related: ADR-0055 (navbar IA), ADR-0054 (detail chrome),
docs/DESIGN_CONSISTENCY_BEHAVIOR_REPORT.md

## Context

Deep review after ADR-0054/0055 found:
1. AppHeader gear opened Settings with `popUpTo("home")` while banners used
   `launchSingleTop` only → divergent system Back.
2. Splash/connect navigated bare `"syncing"` vs graph `syncing?returnTo={…}`.
3. Gear and `DetailBackButton` hit boxes were 36dp (< Material 48dp).

Market (Spotify / YT Music / Apple): Settings is a **stack push** from
avatar/menu; Back returns to the prior screen — not a tab-sibling pop.

## Decision

1. **Settings entry:** all callers use `NavController.navigateToSettings()` →
   `navigate(SETTINGS_ROUTE) { launchSingleTop = true }`. No `popUpTo("home")`.
2. **Syncing:** always `syncingRoute(returnTo)` / `SYNCING_ROUTE_PATTERN`.
   Splash empty-library → `syncing?returnTo=home`.
3. **Touch:** gear + DetailBackButton outer size ≥ `adp(48f)`
   (`MIN_TOUCH_TARGET_DP`).

## Consequences

- Back from Settings returns to the screen that opened it (stack).
- Syncing routes unambiguous and testable.
- Larger header/back controls on phones.
