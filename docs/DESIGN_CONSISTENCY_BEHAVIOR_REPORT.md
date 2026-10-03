# Design Consistency Behavior Report

Caveman terse. Post ADR-0054 / 0055 / 0056 / **0063**.

## Chrome matrix

| Surface | Behavior |
|---------|----------|
| Tabs Home/Search/Library/Favorites | AppHeader + Settings gear |
| Settings | Off-bar; **no** AppHeader; `DetailBackButton` + title; **stack push** `launchSingleTop` (ADR-0056 / **0063**) |
| Detail routes / Profile / customMixes | No AppHeader; `DetailBackButton` ≥48dp |
| Full player | No AppHeader; swipe-down / ↓ minimize (ADR-0063) |
| Queue (prod) | PlayerBar `queue_sheet` |

Dismiss taxonomy: [docs/NAVIGATION_DISMISS_BEHAVIOR_REPORT.md](NAVIGATION_DISMISS_BEHAVIOR_REPORT.md).

## Navbar (ADR-0055)

1. Home (House) 2. Search 3. Library 4. Favorites  
Settings → AppHeader gear. Entry: `navigateToSettings()` — Back → prior screen.

## Syncing (ADR-0056)

Always `syncing?returnTo=…` via `syncingRoute()`. Splash empty lib → `returnTo=home`.

## Touch (ADR-0056)

Gear + DetailBackButton outer size ≥ `adp(48f)`.

## Tokens

BrandTeal/Purple/surfaces; AppHeader uses Foreground + OfflineYellow.

## Out of scope

Full hex purge; typography migration — **done ADR-0057** (see
docs/TYPOGRAPHY_BEHAVIOR_REPORT.md); AppHeader off playback recompose hoist.

