# Library Sync Behavior Report

Caveman terse. Code truth after ADR-0045.

## Modes

| Mode | Albums | Tracks | When |
|------|--------|--------|------|
| **DELTA** | `getAlbumList2(newest)` ≤3 pages; **upsert** (no wipe) | New + field-diff only | WorkManager interval (default) |
| **FULL** | `getAlbumList2(alphabeticalByName)` all pages; **replace** | Incremental unless force / schema bump | Auto every **7 days**; empty DB |
| **FULL+force** | Same as FULL | **All** albums | Settings Resync Library |

## Triggers

| Trigger | Mode |
|---------|------|
| First run (`albumCount==0`) | FULL (via `syncNow()` / empty cache → all tracks pending) |
| WorkManager Sync Interval | DELTA if last full &lt; 7d; else FULL |
| Settings Resync | FULL + `forceTrackResync=true` |
| Cooldown 5min | Blocks auto (not force) |
| Offline / unreachable | Skip network sync |

## Adaptive concurrency (abstracted)

Track phase uses `AdaptiveSyncLimiter`:

- Start concurrency **2**, delay **200ms**
- Success streak → up to **4** / delay **100ms**
- Failures → down to **2** / delay **500ms**
- Abort after **3** consecutive null fetches
- OkHttp `maxRequestsPerHost=6` (headroom for stream/cover)

No Settings knobs for concurrency.

## Prefs (`ftpmusic_sync`)

- `last_metadata_sync_ms` / `metadata_sync_duration_ms` / `metadata_version`
- `last_delta_sync_ms` / `last_full_sync_ms` — watermarks for mode choice + metrics

## User controls

- **Sync Interval** 1–24h — “Auto-check for new music every Nh”
- **Resync Library** — full force catalog + tracks

## Edge cases

- DELTA empty newest + populated cache → keep albums (no wipe)
- FULL empty response + populated → keep albums
- Deleted albums heal only on FULL replace + orphan track prune
- Sync CAS — second start returns false / WM no-ops
- Process death mid-batch — Room batch txn; next sync resumes pending

## Not in this pass

- `getScanStatus` pause
- PlaylistSyncWorker
- User-facing concurrency Settings
