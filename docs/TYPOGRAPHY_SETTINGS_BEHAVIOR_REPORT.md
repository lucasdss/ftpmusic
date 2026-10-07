# Typography Settings — Behavior Report

Caveman style. ADR-0099. Extends ADR-0057 / ADR-0058.

## Goal

User control text size by role + curated font faces + primary weight. Live app-wide. No arbitrary Google Font field. No italic.

## Surface

Settings → **TYPOGRAPHY** (below APPEARANCE).

- Preview: heading / body / label samples (live tokens)
- Sliders: Heading / Body / Label — **0.85–1.30**, step **0.05**, default **1.0**
- UI font: Outfit | Inter | System (default Outfit)
- Caption font: Inter | Outfit | System (default Inter)
- Primary weight: Regular | Medium | Bold (default Medium)
- Reset → defaults

## Data flow

```
Settings UI → SettingsViewModel → SecureStorage
                ↓
         MainActivity collect state
                ↓
         FtpmusicTheme(typographyPrefs)
                ↓
         LocalTypographyPrefs → asp(role) + ftpTypography()
```

## Role map

| Role | Dimens tokens | M3 roles |
|------|---------------|----------|
| Heading | textHeading*, textDisplay | display*, headline*, titleLarge/Medium |
| Body | textBody* | body*, titleSmall |
| Label | textMicro, textLabel* | label* |

## Scale math

`finalSp = base * widthFactor * roleScale` then `.sp` → **system fontScale still applies**.

## Persistence keys

- `typo_heading_scale` / `typo_body_scale` / `typo_label_scale`
- `typo_ui_font` / `typo_caption_font` / `typo_weight_bias`

Corrupt / missing → defaults. Out-of-range → clamp + snap.

## Weight bias

Drives `titleMedium` / `titleSmall` / `labelMedium` in theme + `primaryTextWeight()` on SongListRow + PlayerBar track titles. Bold section titles stay Bold.

## Edge cases

| Case | Behavior |
|------|----------|
| Process death | Reload SecureStorage on SettingsVM init |
| Huge a11y fontScale + 1.30 | FittingText / layout; never override density |
| Google Fonts fail | System preset always available |
| Partial weight coverage | Priority track rows use helper; rest keep call-site weight |

## Out of scope

Free Google Font name, italic, per-token absolute sp, light theme.
