# ADR 0009: Remove dead `pending_scrobbles` table

## Status
Accepted

## Context
The `pending_scrobbles` table was created in migration 1→2 as part of an offline-first scrobble queue. The associated methods `scrobbleTrack()` and `flushPendingScrobbles()` were intended to allow scrobbles to accumulate locally when offline and flush to the Subsonic server when connectivity returned.

However, `scrobbleTrack()` was never called anywhere in the codebase. The `flushPendingScrobbles()` method ran every 60 seconds from `MediaService` but always found 0 pending rows because nothing ever inserted into `pending_scrobbles`. The entire table and all associated DAO methods were dead code.

## Decision
Remove the `pending_scrobbles` table, its entity (`PendingScrobbleEntity`), all associated DAO methods (`insertPendingScrobble`, `getPendingScrobbles`, `markScrobbleFlushed`), and the dead methods `scrobbleTrack()` and `flushPendingScrobbles()`.

Add migration 32→33 to `DROP TABLE IF EXISTS pending_scrobbles`.

## Consequences
- Migration 32→33 runs on next app start, dropping the table (no data loss — table was always empty).
- `flushPendingScrobbles()` removed — the 60-second periodic flush job in `MediaService` is also removed.
- The real scrobble path (direct `api.scrobble(submission=true)` + `incrementPlayCount`) is unaffected.
- Database schema simplifies by removing a never-populated table.
