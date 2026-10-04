# Library Sync Behavior Report

Caveman terse. Code truth after ADR-0045 + ADR-0068.

## Modes

| Mode | Albums | Tracks | When |
|------|--------|--------|------|
| **DELTA** | `getAlbumList2(newest)` ≤3 pages; **upsert** (no wipe) | New + field-diff only | WorkManager interval (default) |
| **FULL** | `getAlbumList2(alphabeticalByName)` all pages; **replace** | Incremental unless force / schema bump | Auto every **7 days**; empty DB |
| **FULL+force** | Same as FULL | **All** albums | Settings Resync Library |

Identity = Subsonic `id` only. Batch `distinctBy { id }` before upsert/replace. No name/artist semantic merge.

## Triggers

| Trigger | Mode |
|---------|------|
| First run (`albumCount==0`) | FULL (via `syncNow()` / empty cache → all tracks pending) |
| WorkManager Sync Interval | DELTA if last full &lt; 7d; else FULL |
| Settings Resync | FULL + `forceTrackResync=true` |
| Cooldown 5min | Blocks auto (not force) |
| Offline / unreachable | Skip network sync |

## WorkManager completion (ADR-0068)

`SyncScheduleWorker` **awaits** `syncNowAsync` Job (`join`).

| End state | Result |
|-----------|--------|
| Job null (cooldown / CAS / offline) | `success` (skip) |
| Unreachable before start | `retry` |
| `phase == "error"` after join | `retry` (fail after 3 attempts) |
| `phase == "complete"` | `success` |

## Watermarks (`ftpmusic_sync`)

- `last_metadata_sync_ms` / `metadata_sync_duration_ms` / `metadata_version`
- `last_delta_sync_ms` / `last_full_sync_ms`

Written **only** when sync ends `phase=complete`. Track-phase error / abort → prior watermarks untouched.

## Album list fail-closed

API `status=failed` mid-pagination → **no** upsert/replace. Keep cache. Next sync retries.

Empty response + populated cache → keep (both modes).

## Adaptive concurrency

Track phase uses `AdaptiveSyncLimiter`:

- Start concurrency **2**, delay **200ms**
- Success streak → up to **4** / delay **100ms**
- Failures → down to **2** / delay **500ms**
- Abort after **3** consecutive null fetches
- OkHttp `maxRequestsPerHost=6`

## User controls

- **Sync Interval** 1–24h — “Auto-check for new music every Nh”
- **Resync Library** — full force catalog + tracks

## Edge cases

- Deleted albums heal only on FULL replace + orphan track prune
- Sync CAS — second start returns false / WM skip success
- Process death mid-batch — WM can retry; Room batch txn; next sync resumes pending tracks
- Duplicate ids in one fetch → collapsed before write

## Not in this pass

- `getScanStatus` pause
- PlaylistSyncWorker
- Semantic (name/artist) dedupe
- Foreground/app-open DELTA
- User-facing concurrency Settings
