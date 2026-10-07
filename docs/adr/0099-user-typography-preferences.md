# ADR 0099 — User Typography Preferences

Date: 2026-10-08
Status: Accepted
Related: ADR-0057 (typography system), ADR-0058 (correctness),
docs/TYPOGRAPHY_SETTINGS_BEHAVIOR_REPORT.md

## Context

ADR-0057 fixed Outfit + Inter and adaptive `asp` tokens. Users still need
in-app control of size and face without breaking hierarchy or system a11y
fontScale. Free-form font pickers and per-token absolute sp editors risk
brand drift and layout breakage.

## Decision

1. **Settings → TYPOGRAPHY** section (not nested in APPEARANCE).
2. **Role scales** Heading / Body / Label: **0.85–1.30**, step **0.05**,
   default **1.0**. Applied in `asp(role)` and `buildFtpTypography`.
3. **Curated fonts only:** Outfit, Inter, System for UI and caption faces.
4. **Primary weight bias:** Regular / Medium / Bold (default Medium) via
   theme Medium roles + `primaryTextWeight()` on priority track rows.
5. Persist in **SecureStorage**; feed live via activity-scoped
   `SettingsViewModel` → `FtpmusicTheme(typographyPrefs)` →
   `LocalTypographyPrefs`.
6. Keep `.sp` so **system fontScale multiplies on top**.

## Consequences

- Token hierarchy preserved when roles scale independently.
- System a11y and FittingText remain valid.
- Call sites that hardcode `FontWeight.Medium` outside the helper may lag
  until migrated; Bold headings intentionally ignore bias.
