# ADR 0059: Reaction icon sizing (market bump)

## Status

Accepted — 2026-10-02

## Context

Interactive thumbs, radio bookmark, and rating stars used ad-hoc sizes
(11–18dp glyphs, 24–28dp hits). Market players (Spotify / YT Music / Apple
Music) use ~20–24dp glyphs with ≥40dp touch. FTP already had adaptive tokens
in `Dimens.kt` but reaction UI ignored them.

## Decision

Lock interactive reaction tokens (adaptive via `adp`):

| Token | Value | Use |
| :--- | :--- | :--- |
| `reactionGlyphSize()` | `adp(20)` | Thumbs / bookmark glyph |
| `reactionHitSize()` | `adp(40)` | Visible circle (FavoriteThumbButton, Player ReactionCircle) |
| `ratingStarInteractiveSize()` | `adp(20)` | Interactive 0–5★ glyph |

Dense rows: thumbs use glyph + `minimumInteractiveComponentSize()` (no
opaque circle). Interactive ★ live on full player + Album detail under art
(`expandTouchTarget=true`). Track-list ★ removed (title space).
Decorative / display-only stars and section headers stay unchanged.
`knobSize()` remains for download/more/drag chrome only.

Shared composables: `FavoriteThumbButton`, `ReactionGlyphButton`,
`InteractiveStarRating`, `RadioBookmarkIcon`.

## Consequences

- Larger player / album-under-art chrome; track rows no longer host ★.
- Call sites must not hardcode reaction `.dp`; use tokens/components.
- See `docs/RATING_SURFACE_BEHAVIOR_REPORT.md`.
