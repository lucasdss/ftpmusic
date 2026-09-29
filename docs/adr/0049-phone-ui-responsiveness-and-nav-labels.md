# ADR 0049 — Phone UI Responsiveness and Nav Labels

Date: 2026-09-29
Status: Accepted
Related: ADR 0044 (settings prefs), docs/UI_RESPONSIVENESS_BEHAVIOR_REPORT.md

## Context

App shell is phone `NavigationBar` only. `AdaptiveScale` only scaled **up** (1.0–1.25) from 360dp ref; `.sp` stacked system fontScale on top → narrow width + large a11y font broke rows and 5-tab labels. Unbounded `Text` (~79% without overflow) let long or unbreakable one-word strings clip layout. Users want icon-only bottom bar option.

## Decision

1. **Phone harden only** — no `WindowSizeClass` / `NavigationRail` this pass. Document tablet gap; defer adaptive suite.
2. **Downscale + absorb** — width factor clamp **0.85..1.25**; `asp` multiplies partial fontScale compensation.
3. **FittingText** — shrink font to min within constraints, then `TextOverflow.Ellipsis`. Protect even one-word chrome in constrained slots.
4. **Hide nav labels** — `KEY_NAV_HIDE_LABELS` (default false). Activity-scoped `SettingsViewModel` so Settings toggle updates bar live. Icon `contentDescription` retained for a11y.

## Consequences

- Narrow phones and high fontScale less likely to break chrome.
- Icon-only nav available from Settings → DISPLAY.
- Tablet/foldable NavigationRail remains future work.
