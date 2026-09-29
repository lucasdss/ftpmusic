# ADR 0049 — Phone UI Responsiveness and Nav Labels

Date: 2026-09-29
Status: Accepted
Related: ADR 0044 (settings prefs), docs/UI_RESPONSIVENESS_BEHAVIOR_REPORT.md

## Context

App shell is phone `NavigationBar` only. `AdaptiveScale` only scaled **up** (1.0–1.25) from 360dp ref; unbounded `Text` let long or unbreakable one-word strings clip layout. Users want icon-only bottom bar option. An early harden briefly canceled a11y fontScale inside `asp()` (layout-safe but a11y-hostile).

## Decision

1. **Phone harden only** — no `WindowSizeClass` / `NavigationRail` this pass. Document tablet gap; defer adaptive suite.
2. **Width-only tokens** — width factor clamp **0.85..1.25** for `adp`/`asp`. `asp` uses `.sp` so **system fontScale is honored**.
3. **FittingText owns overflow** — shrink font to min within constraints, then `TextOverflow.Ellipsis`. Protect constrained slots (including one-word chrome). Do **not** cancel a11y globally.
4. **Hide nav labels** — `KEY_NAV_HIDE_LABELS` (default false). Activity-scoped `SettingsViewModel` so Settings toggle updates bar live. Icon `contentDescription` only when labels hidden.

## Consequences

- Narrow phones get width downscale; large a11y fonts still grow tokens.
- Constrained chrome stays layout-safe via FittingText.
- Icon-only nav available from Settings → APPEARANCE.
- Tablet/foldable NavigationRail remains future work.
