# ADR 0045 — Adaptive Delta Library Sync

Date: 2026-09-28
Status: Accepted
Related: ADR 0013 (local-first / WorkManager), ADR 0022 (offline), ADR 0043 (reachability)

## Context

WorkManager periodic sync skipped entirely when `albumCount() > 0`, so Settings
“Sync Interval” never refreshed populated libraries. Album/artist/genre phases
always did full replace; only tracks were incremental. Track fetch concurrency
was fixed at 5 — equal to OkHttp default `maxRequestsPerHost` — saturating the
host pool during sync.

Users need background discovery of new music (delta) without burning the server,
plus occasional full catalog heal for deletions/renames. Concurrency knobs are
not appropriate end-user Settings.

## Decision

1. **LibrarySyncMode.DELTA / FULL** on `MetadataSyncWorker`.
2. **DELTA** — `getAlbumList2(type=newest)` (capped pages), upsert albums, refresh
   artists/genres, incremental tracks.
3. **FULL** — alphabetical replace (existing path). Auto every **7 days** from
   `last_full_sync_ms`. Settings Resync = FULL + `forceTrackResync`.
4. **SyncScheduleWorker** always attempts sync (no populated skip); chooses mode
   from watermark; gates on offline + reachability.
5. **AdaptiveSyncLimiter** — concurrency 2–4, delay 100–500ms; OkHttp
   `maxRequestsPerHost=6`. Not exposed in Settings.

## Consequences

- Sync Interval meaning matches UX (auto-check for new music).
- Weekly full heal restores deleted-album consistency without manual Resync.
- Stream/cover requests keep headroom during sync.
- CONTEXT.md / ADR 0013 “30-minute always-on sync” language updated to match.
