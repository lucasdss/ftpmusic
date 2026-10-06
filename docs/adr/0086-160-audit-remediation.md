# ADR 0086 — 1.6.0 audit remediation (search / queue / sync)

Date: 2026-10-06
Status: Accepted
Related: ADR 0015, 0076, 0078, 0084, 0085

## Context

Deep review of 2026-10-06 queue + search FTS + sync enrich work found ship
blockers: cancel→error UI, search cache wiping enrich, stale queue sheet after
reorder, Subsonic `failed` still `markFlushed`, share chooser crash on
applicationContext, multi-snapshot batch remove, year filter keeping orphans,
enrich KEEP skipping, densify dropping path/mbid.

## Decision

1. **Search:** rethrow `CancellationException`; `cacheServerResults` uses
   preserve-enrich album/artist upserts; loadMore is a cancelable job and
   bumps offsets only when query still matches; year-active filters **drop**
   null-`albumId` tracks (cannot verify year).
2. **Queue UI:** prod `NavHost` keys `nextTracks` on
   `QueueRevisionTracker.revision` (+ size/priority/index/id).
3. **Playlist sync:** every flush mutation requires
   `authHelper.checkResponseStatus` before `markFlushed`; soft fail leaves
   pending (share must not publish empty/partial).
4. **Share:** `FLAG_ACTIVITY_NEW_TASK` on chooser; single-flight mutex.
5. **Batch remove:** one optimistic snapshot; Cast uses ClearAndPlay for the
   remaining Dual queue.
6. **Enrich:** `ExistingWorkPolicy.REPLACE` so post-sync enqueue is not skipped.
7. **Densify merge:** `upsertTracksPreserveCache` fills `path` /
   `musicbrainzId` when incoming has them.

## Consequences

- Share/Save still local-first (ADR 0076); sync honesty stricter.
- Year+text queries may hide true singles until album year exists — preferred
  over leaking modern orphans into decade results.
- Second enrich enqueue cancels/replaces in-flight WorkManager unique work.
