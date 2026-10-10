# ADR 0068 — Library Sync Completion and Idempotency

Date: 2026-10-04
Status: Accepted
Related: ADR 0045 (adaptive delta), ADR 0013 (local-first / WorkManager), ADR 0043 (reachability)

## Context

ADR-0045 introduced DELTA/FULL modes and periodic WorkManager sync. Three
correctness holes remained:

1. `SyncScheduleWorker` fired `syncNow()` and returned `Result.success` without
   awaiting the Job — process death aborted sync with no retry.
2. Prefs watermarks (`last_full_sync_ms` / `last_delta_sync_ms`) were written even
   when track phase ended in `phase=error`, advancing heal cadence falsely.
3. FULL `getAlbumList2` mid-pagination `status=failed` still called
   `replaceAlbums` with a partial page list, shrinking the catalog.

Dedupe requirement is Subsonic `id` only (not name/artist). Latency goal is
honest completion of the existing interval — not faster polling.

## Decision

1. **Await** — `SyncScheduleWorker` calls `syncNowAsync`, `join()`s the Job,
   returns `retry` when end `phase == "error"` (cap via `runAttemptCount`).
2. **Honest watermarks** — write `last_*_sync_ms` only on `phase=complete`.
3. **Fail-closed album list** — API failed mid-pagination aborts album write;
   keep cache for both DELTA and FULL.
4. **Idempotent batch** — `distinctBy { id }` before upsert/replace.

## Consequences

- Periodic sync survives to completion or gets WM retry.
- Failed track phases do not postpone weekly FULL heal.
- Partial FULL pages cannot wipe the library.
- Mid-pagination album-list failure aborts the whole sync (`phase=error`) so
  watermarks do not advance after a keep-cache early return.
- Same Subsonic id cannot appear twice from one fetch batch.
- Semantic duplicates (same title, different ids) still both kept by design.
- Amend (ADR-0108): FULL completion prunes stale `tracks` densify rows not in
  album/genre caches (local star/download/play weight preserved).
