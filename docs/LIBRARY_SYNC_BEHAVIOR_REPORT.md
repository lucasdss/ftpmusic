# Library Sync Behavior Report

Caveman terse. Code truth after ADR-0045 + ADR-0068 + ADR-0085 + ADR-0108 + ADR-0109.

## Modes

| Mode | Albums | Tracks | When |
|------|--------|--------|------|
| **DELTA** | `getAlbumList2(newest)` ≤3 pages; **upsert-preserve enrich** | New + field-diff only; drain cap 400 | WorkManager interval (default) |
| **FULL** | `getAlbumList2(alphabeticalByName)` all pages; **diff-replace preserve enrich** | Incremental unless force / schema bump | Auto every **7 days**; empty DB |
| **FULL+force** | Same as FULL | **All** albums | Settings Resync Library |

Identity = Subsonic `id` only. Batch `distinctBy { id }` before upsert/replace. No name/artist semantic merge.

## Phase order

albums → artists → genres → **orphans** → stars → album-tracks → artist counts →
daily-mix seed → populate `tracks` → FTS rebuild → **enqueue enrich** (async)

## Orphan densify (ADR-0085)

| Source | Behavior |
|--------|----------|
| Genre offset | When server songCount > local `cached_genre_songs`, page `getSongsByGenre` with offset |
| Random songs | `getRandomSongs(size=500)` rounds → `tracks` (null album_id OK) |
| DELTA budget | 3 genre pages + 2 random rounds |
| FULL/force | 20 genre pages + 6 random rounds |

## Enrich (async)

`MetadataEnrichRunner` / WM one-shot after sync complete. Sync watermarks do
**not** await MB/Last.fm. Enrich schedules FTS rebuild.

## Triggers

| Trigger | Mode |
|---------|------|
| First run (`albumCount==0`) | FULL (via `syncNow()` / empty cache → all tracks pending) |
| WorkManager Sync Interval | DELTA if last full &lt; 7d; else FULL |
| Settings Resync | FULL + `forceTrackResync=true` |
| Home Recently Added change (ADR-0109) | DELTA when TTL refresh sees new ordered ids |
| Cooldown 5min | Blocks auto (not force) |
| Offline / unreachable | Skip network sync |

## Home Recently Added (ADR-0109)

- Snapshot: prefs ordered ids + `fetched_at`; paint from `cached_albums`.
- TTL default 5m; clamp **1 … Sync Interval minutes**.
- Home open: local first; network only if stale.
- Sync album phase also writes snapshot (DELTA reuse newest pages; FULL `newest`×10).

## WorkManager completion (ADR-0068)

`SyncScheduleWorker` **awaits** `syncNowAsync` Job (`join`). Enrich is separate.

| End state | Result |
|-----------|--------|
| Job null (cooldown / CAS / offline) | `success` (skip) |
| Unreachable before start | `retry` |
| `phase == "error"` after join | `retry` (fail after 3 attempts) |
| `phase == "complete"` | `success` |

## Watermarks (`ftpmusic_sync`)

- `last_metadata_sync_ms` / `metadata_sync_duration_ms` / `metadata_version`
- `last_delta_sync_ms` / `last_full_sync_ms` (mutual exclusion by mode)
- `last_sync_mode` = `FULL` \| `DELTA`
- `last_sync_skip_reason` = offline / unreachable / cellular_policy / cooldown / already_running (cleared on start)

Written **only** when sync ends `phase=complete`. Track-phase error / abort → prior watermarks untouched.
Settings Resync → FULL watermark only → **Last delta** stays Never until WM DELTA completes.

## Album list fail-closed

API `status=failed` mid-pagination → **no** upsert/replace. Keep cache. Next sync retries.

Empty response + populated cache → keep (both modes).

## Counts

| Metric | Source |
|--------|--------|
| Album progress | `COUNT(cached_albums)` |
| Syncing **Tracks** | `COUNT(tracks)` search corpus (always) |
| Settings Album track meta | `COUNT(cached_album_tracks)` |
| Settings Search corpus | `COUNT(tracks)` |
| Settings Album song_count sum | `SUM(cached_albums.song_count)` |
| Album song_count | server field; overwritten from fetched `getAlbum` size |
| Artist album_count | recomputed SQL post tracks |

Meta ≠ corpus. Resync FULL can prune corpus (ADR-0108); meta only drops if
server `getAlbum` unique ids drop or album heal deletes orphans.

## FULL corpus reconcile (ADR-0108)

After populate on FULL: `DELETE FROM tracks` where id ∉ album-tracks ∪ genre-songs
and no local weight (star / dislike / download / cache path / play_count>0).
DELTA: no prune.

## Adaptive concurrency

Track phase uses `AdaptiveSyncLimiter`:

- Start concurrency **2**, delay **200ms**
- Success streak → up to **4** / delay **100ms**
- Failures → down to **2** / delay **500ms**
- Abort after **3** consecutive null fetches
- OkHttp `maxRequestsPerHost=6`

## User controls

- **Sync Interval** 1–24h — “Auto-check for new music every Nh”
- **Library sync on Wi‑Fi only** — FULL **and** DELTA skip on cellular (ADR-0105)
- **Resync Library** — full force catalog + tracks; warn on cellular when Wi‑Fi-only ON
- **First-login** — warn on cellular before FULL; optional override

## Network gates (ADR-0105)

| Condition | Auto FULL/DELTA | Manual / first-login |
|-----------|-----------------|----------------------|
| Offline / unreachable | skip | skip |
| Cellular + cellular media `local_only` | skip | blocked (hard local) |
| Cellular + sync Wi‑Fi-only | skip | warn → override or cancel |
| Cellular + sync any | run | first-login warn; manual no warn |

WM constraint: `UNMETERED` when sync Wi‑Fi-only else `CONNECTED`.

## Edge cases

- Deleted albums heal on FULL diff-replace + `cached_album_tracks` orphan prune
- FULL also reconciles `tracks` corpus (ADR-0108); played/starred ghosts kept
- Sync CAS — second start returns false / WM skip success + skip reason
- Process death mid-batch — WM can retry; Room batch txn; next FULL heals corpus
- Duplicate ids in one fetch → collapsed before write
- Enrich columns survive catalog refresh (preserve upsert)
- `LibraryViewModel.resyncAll` = default DELTA, **no production UI caller**

## Not in this pass

- `getScanStatus` pause
- PlaylistSyncWorker
- Semantic (name/artist) dedupe
- Foreground/app-open DELTA
- User-facing concurrency Settings
