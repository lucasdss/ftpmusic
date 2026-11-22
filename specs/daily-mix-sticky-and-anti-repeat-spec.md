# Spec: Daily Mix sticky Home card + rebuild anti-repeat

## Sticky card during pending regen

### Problem

`deleteUnpublishedBuilds` deleted every `status IN ('building','ready')` row.
Active lists stay `ready` (pointer, not status), so Home’s
`active_daily_mix_id` JOIN failed after `pending set` until later `activate`.

### Contract

- `deleteUnpublishedBuilds(mixId)` must **never** delete rows whose ids are
  `custom_mixes.active_daily_mix_id` or `pending_daily_mix_id`.
- Home continues to show the sticky active card while a pending version waits
  (ADR-0147). Do **not** auto-activate on Home/sync.

## Rebuild anti-repeat

### Problem

Rebuilds soft-excluded only *scrobbled* prior-mix tracks, cliffed off exclusion
when fresh &lt; 30, and demoted via calendar-yesterday lookup that sticky
versioning often misses. Users saw the same songs after rebuild.

### Contract

1. Prefer-exclude set = **all** track IDs from active ∪ last 2 superseded lists
   ∪ honest global listens in the last 7 days.
2. Generator: no binary cliff — always prefer fresh; re-admit excluded IDs only
   via hard-fill when needed to hit `targetSize`.
3. Soft weight penalty uses active ∪ last superseded (not calendar yesterday).
4. On activate, retain up to 2 superseded rows for anti-repeat depth.

### Edge cases

- Tiny pool (all tracks excluded) → hard-fill re-admits; mix non-empty.
- 29 fresh of 100 → all 29 fresh used before any familiar hard-fill.
- Pool ≫ prior list → overlap with prior &lt; 25%.
