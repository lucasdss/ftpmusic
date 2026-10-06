# ADR 0073 — Queue Sheet Opaque Swipe Rows

Date: 2026-10-05
Status: Accepted
Related: ADR-0054 (design tokens), ADR-0070 (player surfaces UX parity),
`docs/QUEUE_SHEET_UI_UX_BEHAVIOR_REPORT.md`

## Context

Prod queue UI is the in-player `queue_sheet` in `PlayerBar` (ADR-0070). Rows wrap
`SwipeToDismissBox` with a destructive red `backgroundContent`. The foreground
row used `Color.Transparent`, so idle rows painted full delete-red bleed —
users saw every track as red. Legacy `QueueScreen` had the same stack with
`Color.Red`.

## Decision

1. **Idle / non-drag row fill** = opaque `SurfaceElevated` (matches sheet
   container). Dragging keeps `surfaceVariant`.
2. **Swipe / Clear chrome** = `DestructiveRed` token (never raw `Color.Red` or
   inline `#E84040`).
3. **Queue SoT UI** remains `PlayerBar` `queue_sheet`. `QueueScreen` mirrors the
   opaque-fill + token rule so the ADR-0070 harness cannot reintroduce bleed.
4. No shared global `TrackRow` extraction this cycle; no Dual/playback changes.

## Consequences

- Idle queue lists read as elevated surface + `Foreground` titles; red only on
  swipe reveal and Clear affordances.
- Compose tests may tag `queue_track_row` and assert opaque elevated fill.
- Future swipe rows must never use transparent foreground over colored dismiss bg.
