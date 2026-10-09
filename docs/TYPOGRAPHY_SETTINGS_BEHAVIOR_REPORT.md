# Typography Settings — Behavior Report

Caveman style. ADR-0103 + ADR-0099. Extends ADR-0057 / ADR-0058.

## Goal

User control display density (text + icons + art) plus role size / curated
fonts / primary weight. Live app-wide. No arbitrary Google Font field. No italic.

## Surface

Settings → **Text & display size** (`settings/typography`).

### Display size

Chips: **Tiny | Small | Medium | Big | Bigger** (default Medium).

Preview: heading / body / label + sample art tile + icon (live tokens).

### Fine tune

- Sliders: Heading / Body / Label — **0.85–1.30**, step **0.05**, default **1.0**
- UI font: Outfit | Inter | System (default Outfit)
- Caption font: Inter | Outfit | System (default Inter)
- Primary weight: Regular | Medium | Bold (default Medium)
- Reset → Medium density + role/font/weight defaults

Root Settings shows nav row with current density label (not inline sliders).

## Data flow

```
TypographySettingsScreen → SettingsViewModel → SecureStorage
                ↓
         MainActivity collect state
                ↓
         FtpmusicTheme(typographyPrefs)
                ↓
         LocalTypographyPrefs → adp + asp(role) + ftpTypography()
```

## Scale math

| Token | Formula |
|-------|---------|
| `adp` | `base * widthFactor * densityScale` |
| `asp` / M3 | `base * widthFactor * densityScale * roleScale` then `.sp` |

System fontScale still applies on `.sp`.

Density scales: Tiny 0.80 · Small 0.90 · Medium 1.0 · Big 1.15 · Bigger 1.30.

## Persistence keys

- `typo_density`
- `typo_heading_scale` / `typo_body_scale` / `typo_label_scale`
- `typo_ui_font` / `typo_caption_font` / `typo_weight_bias`

Corrupt / missing → defaults. Out-of-range role → clamp + snap.

## Edge cases

| Case | Behavior |
|------|----------|
| Process death | Reload SecureStorage on SettingsVM init |
| Huge a11y fontScale + Bigger | FittingText / layout; never override density |
| Unknown density string | Medium |
| Reset | Medium + brand font/weight defaults |

## Out of scope

Free Google Font name, italic, per-token absolute sp, light theme, per-screen density.
