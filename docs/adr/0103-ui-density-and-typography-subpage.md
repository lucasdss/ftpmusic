# ADR 0103 — UI Density Ladder + Typography Subpage

Date: 2026-10-09
Status: Accepted
Related: ADR-0099 (user typography prefs), ADR-0057 / ADR-0058
Supplements / updates: ADR-0099 navigation (section → nested page)

## Context

ADR-0099 shipped Heading/Body/Label sliders and font/weight on the root Settings
scroll. Users need smaller sizes and a clear Tiny…Bigger ladder that also scales
icons and cover art. Root Settings is already dense; fine typography belongs on
a second-level page.

## Decision

1. **UiDensityPreset:** Tiny `0.80`, Small `0.90`, Medium `1.0`, Big `1.15`,
   Bigger `1.30`. Persisted as `typo_density`.
2. **Tokens:** `adp` = widthFactor × density; `asp` / `buildFtpTypography` =
   widthFactor × density × roleScale. System `fontScale` still via `.sp`.
3. **Navigation:** Settings → “Text & display size” → `settings/typography`.
   Role sliders, fonts, weight, and reset live on that page under Fine tune.
4. **Reset** restores Medium density + ADR-0099 role/font/weight defaults.

## Consequences

- Tiny × role 0.85 ≈ 0.68 effective text scale (intentional “smaller”).
- Spacing, icons, and art resize with density so layouts adapt.
- ADR-0099 “section on root Settings” superseded for placement only.
