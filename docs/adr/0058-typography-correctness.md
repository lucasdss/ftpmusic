# ADR 0058 — Typography Correctness (No Baked Color + Scaled LineHeight)

Date: 2026-10-01
Status: Accepted
Related: ADR-0057 (typography system), docs/TYPOGRAPHY_BEHAVIOR_REPORT.md

## Context

ADR-0057 wired Outfit/Inter into `ftpTypography()`, but every role set
`color = Foreground` / `Muted`. Compose `Text` / `FittingText` with
`Color.Unspecified` inherit that baked color, so **nav selected teal** and
other `LocalContentColor` / `contentColor` tints never applied.

Also: `fontSize` used adaptive scale (`asp`) while `lineHeight` was fixed
`.sp`, so line-height ratio tightened on wide screens. Track Medium / subtitle
`textLabelM` was incomplete on Profile Recently Played, mini players, Library
list rows, Search rows, and Mix/Artist sheets.

## Decision

1. **No baked theme colors** — `ftpTypography` / `buildFtpTypography` styles
   leave `TextStyle.color` Unspecified. Muted/brand colors stay at call sites.
2. **Track primary** = `textHeadingS()` + `FontWeight.Medium` (mandatory).
3. **Track / sheet subtitles** = `textLabelM()`.
4. **Scaled lineHeight** — `fontBase * LINE_HEIGHT_MULT * widthFactor` (pairs
   with asp); policy constant `TypographyPolicy.LINE_HEIGHT_MULT`.
5. **Remember** typography on font families + width factor inside
   `ftpTypography()`.

## Consequences

- Nav labels can show BrandTeal via NavigationBarItem contentColor.
- Line-height ratio stays stable across phone widths.
- Tests assert theme styles omit color and policy line-height scaling.
