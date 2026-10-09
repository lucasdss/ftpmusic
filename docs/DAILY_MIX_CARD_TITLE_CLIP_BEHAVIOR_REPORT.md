# Daily Mix Card Title Clip — Behavior Report

Date: 2026-10-09
Related: `GenreMixCard` in `compose/app/.../ui/library/HomeScreen.kt`

## Problem

Home Daily Mix card titles had the first letter partially cut by the card’s
rounded corner (`cornerM()` = 12.dp).

## Cause

`GenreMixCard` applied `.clip(RoundedCornerShape(cornerM()))` on the outer
`Column`. Title text sits flush left under that clip; the bottom-left arc
intersected the first glyph. Cover art already had its own Box-level clip.

## Contract

- Round corners on **cover art only** (Box clip) — same as `AlbumCardDesign` /
  `HomePlaylistCard`.
- Title left-aligned to card/art left edge; no horizontal inset.
- Full-card hit target remains via Column `clickable`.
