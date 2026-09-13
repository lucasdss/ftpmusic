# ADR 0017 — Daily Mix Lazy Generation on Home Open

Date: 2026-08-19
Status: Accepted
Related: ADR 0034 (custom daily mix architecture), ADR 0035 (composite filters)

## Context

Daily Mix generation previously ran exclusively from the syncing screen
(`SyncingViewModel.generateDailyMixesSync`). The Home screen only read persisted
mixes (today with yesterday fallback); with no mixes it showed a static placeholder
that could persist indefinitely — after 48h a mix became invisible (neither today's
nor yesterday's row existed) and nothing regenerated it until the user visited a
sync flow.

## Decision

1. **Home-triggered lazy generation**: `LibraryViewModel.loadGenreMixes()` — called
   on every Home resume — generates/regenerates when the visible mixes are empty or
   incomplete relative to the selected genres, then reloads so cards appear in place.
2. **Generation policy**: identical to the sync screen — generate when no mix exists
   for today or yesterday (fixes the 48h+ gap), regenerate per
   `DailyMixGenerator.shouldRegenerate` (≥48h, or ≥24h with ≥10% listened). No
   churn on fresh mixes.
3. **Coordination**: a new `DailyMixGenerationCoordinator` object provides a global
   in-flight gate (`AtomicBoolean`) and a same-day empty-outcome suppression so an
   empty library doesn't trigger generation on every open.
4. **Sync-race guard**: generation skips while `MetadataSyncWorker.status.isRunning`
   (the worker repopulates `tracks`); the next Home resume retries.
5. **Off-main execution**: generation runs on an injectable `ioDispatcher`
   (default `Dispatchers.IO`, Hilt-provided binding added in `DatabaseModule`).
6. **Empty-mix policy**: no path persists an empty mix (lazy path, sync screen,
   `refreshGenreMix`) — an empty row previously deadlocked regeneration for 48h.
7. **UI feedback**: `LibraryState.isGeneratingMixes` drives the existing "Building
   your Daily Mixes…" message on Home; the per-card "N songs" label is removed
   (the count is not part of the card design).

## Consequences

- Mixes appear on Home without any sync-screen visit; the 48h "disappeared mix"
  symptom is fixed.
- The Home refresh button and detail-screen refresh remain the forced-regeneration
  paths; the sync screen keeps its progress UI and per-genre delays.
- `DailyMixGenerationCoordinator` is global state — tests reset it via
  `resetForTest()`.
- Periodic WorkManager sync still does not generate mixes (finding #1) — tracked as
  a follow-up.
