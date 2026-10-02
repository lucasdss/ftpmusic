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
opaque circle). Track-row ★ use `InteractiveStarRating(expandTouchTarget=false)`
so min-touch stars do not steal title taps. Player/hero ★ keep expanded touch.
Decorative / display-only stars and section headers stay unchanged.
`knobSize()` remains for download/more/drag chrome only.

Shared composables: `FavoriteThumbButton`, `ReactionGlyphButton`,
`InteractiveStarRating`, `RadioBookmarkIcon`.

## Consequences

- Larger player/overlay chrome; denser track rows keep visual glyph scale but
  expand invisible hit.
- Call sites must not hardcode reaction `.dp`; use tokens/components.
