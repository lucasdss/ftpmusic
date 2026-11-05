# ADR 0147: Daily Mix sticky active pointer (DM-16)

## Status

Accepted — 2026-11-05

## Context

Home cards disappeared when auto-generation returned `SKIPPED_FRESH` for a still-fresh list whose `daily_mix.date` was older than yesterday (Nov 5 diagnostics). Freshness used `getLatestDailyMix` (any date); visibility used today|yesterday only. Settings rebuild (`manual=true`) rewrote today and restored cards.

Product requires sticky cards after first generation, background rebuild of new versions, Detail session Update/Ignore, and leave→reenter showing the new list.

## Decision

1. Versioned `daily_mix` rows: `(mix_id, version)` unique; `status` ∈ {building, ready, superseded}. `date` is a label only.
2. `custom_mixes.active_daily_mix_id` / `pending_daily_mix_id` are the sole visibility/activation pointers (no `status=published`).
3. Home/sync lazy gen builds pending only — never activates.
4. Activation writers: rebuildmix, Home refresh, Detail Update/PTR, Detail nav-pop (pending), filter/fill edit, first-create.
5. Empty/failed regen never deletes the active row.
6. Cache: `onSourceChanged` on activate only (not `onGenerated`).
7. Android Auto / Detail / Home all read the active pointer.

## Consequences

- Migration 68→69 backfills versions and sets `active_daily_mix_id` to the newest row per recipe.
- `replaceDailyMix` remains for tests/legacy helpers; production path is beginBuild → completeReady → pending/activate.
- Continuous-play journal stays recipe-scoped (`genremix`/`mixId`); Update does not rewrite the Media queue.
