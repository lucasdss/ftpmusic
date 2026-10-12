# Shell Chrome Density — Behavior Report

Caveman style. Supplements ADR-0103. ADR-0127.

## Goal

Density + hide-nav-labels shrink **shell chrome height** (bottom nav, AppHeader),
not only icons/text. Reclaim list space. Keep design + ≥48dp touch.

## Symptom (before)

Hide labels + Tiny density → icons shrink, **NavigationBar still ~80dp**.
AppHeader logo stuck at **56.dp** → header barely shrinks.

## Root cause

| Surface | Bug |
|---------|-----|
| M3 `NavigationBar` + `NavigationBarItem` | `defaultMinSize(minHeight ≈ 80.dp)` always |
| `label = null` | recenters icon only; no height change |
| AppHeader logo / corners / gaps / lineHeight | hardcoded `.dp` / `.sp` |
| Double inset risk | `Column.navigationBarsPadding()` + M3 bar default insets |

## Scale map

| Chrome | Token | Tiny @360 | Medium | Bigger |
|--------|-------|-----------|--------|--------|
| Nav labeled height | `navBarHeight(false)` | max(64,48)=64 | 80 | 104 |
| Nav icon-only height | `navBarHeight(true)` | max(44.8,48)=**48** | 56 | 72.8 |
| Nav icon | `adp(22)` | 17.6 | 22 | 28.6 |
| Mini player | `miniPlayerHeight` | 51.2 | 64 | 83.2 |
| Header logo | `appHeaderLogoSize` | 44.8 | 56 | 72.8 |
| Header gear hit | `interactiveFloor(minTouchTarget())` | **48** | 48 | 62.4 |
| System nav inset | device | once via Column padding | | |

**Floor rule:** `interactiveFloor` → never below **48.dp** for interactive chrome
containers/hit boxes. Tiny icon-only bar bottoms at 48 — expected, not a bug.

## Wiring

```
hideNavLabels + LocalTypographyPrefs.density
        ↓
navBarHeight(hideLabels)  →  Modifier.height on NavigationBar AND each item
        ↓
windowInsets = WindowInsets(0)  (Column already navigationBarsPadding)
        ↓
AppHeader logo/spacers/lineHeight via adp/asp/spacing*
        ↓
onSizeChanged → headerHeightPx (enterAlways measure still owns inset)
```

## Edge cases

| Case | Behavior |
|------|----------|
| Labeled + short height | Impossible — height derived from same `hideNavLabels` |
| Gesture vs 3-button | Single `navigationBarsPadding` on Column; bar insets cleared |
| TalkBack icon-only | Icon CD = tab label (existing ADR-0049) |
| Bigger + labeled + mini | Tall chrome OK (same as mini `adp`); no chrome density cap |
| Header collapse | Translate-only; denser logo → smaller measured inset |
| Detail TopAppBar | Out of scope this pass |

## Non-goals

Full ShellScaffold rewrite. App-wide Detekt ban on raw `.dp`. System inset height.
