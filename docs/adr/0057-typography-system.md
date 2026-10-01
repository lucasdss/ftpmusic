# ADR 0057 — Typography System (Outfit / Inter + Track textHeadingS)

Date: 2026-10-01
Status: Accepted
Related: ADR-0054 (design tokens), ADR-0058 (correctness follow-up),
docs/TYPOGRAPHY_BEHAVIOR_REPORT.md

## Context

Dimens offered adaptive `textMicro`…`textDisplay`, but Theme had **no**
`Typography`. Outfit/Inter lived in Fonts.kt yet almost unused (AppHeader +
few timestamps). Track titles used mixed sizes (`textBodyM`, `15.sp`, M3
ListItem defaults). Raw `.sp` on Profile/Library/Syncing bypassed width scale.

Market music apps: one UI face, list primary ~15–17sp Medium, captions for time.

## Decision

1. **`ftpTypography()`** wired into `FtpmusicTheme` — Outfit for UI roles;
   Inter for `labelSmall` (time/meta).
2. **Track list primary** = `textHeadingS()` (~16asp) + Medium everywhere
   (Home, Album, Artist, Favorites, Mix, Playlist, Queue, PlayerBar rows).
3. **Track subtitle** = `textLabelM()`.
4. **Section titles** = `textHeadingM()` Bold (heroes may use `textDisplay()`).
5. **Policy constants** in `TypographyPolicy` for tests (base sp / font names).
6. Outfit gains Regular; Inter gains Medium.

See **ADR-0058** for no baked colors, scaled lineHeight, and remember.

## Consequences

- `MaterialTheme.typography.*` inherits brand faces + adaptive sizes.
- FittingText inherits Outfit via `LocalTextStyle`.
- Decorative outliers (e.g. Syncing ♫ 48.sp) may remain intentional.
