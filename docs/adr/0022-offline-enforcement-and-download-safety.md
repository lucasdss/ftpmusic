# ADR 0022: Offline Enforcement & Download Safety

## Status
Accepted

## Context
ADR 0013 (Proposed) described a local-first architecture, but the enforcement layer was missing: the software offline toggle was only consulted by UI ViewModels. Downloads, sync workers, scrobbles, the star mirror and playback streaming all hit the network while the user had offline mode on. Three hard contract violations were confirmed:

1. The offline flag was not restored after process death — the user believed they were offline while the app streamed.
2. A playback error auto-skip deleted **pinned downloads** (permanent, user-requested files).
3. The documented "queue always cached" behavior only cached the next 3 tracks.

## Decision

### 1. Single choke point for offline enforcement
`OfflineModeManager` is the ONLY owner of the offline state. Every network caller consults `isOfflineEnabled()` (a plain Boolean read — no StateFlow dereference on hot paths):

- `DownloadManager.checkConstraints()` + the priority-0 urgent-window poll (offline blocks ALL downloads, even the urgent window).
- `MetadataSyncWorker.syncNowAsync`, `PlaylistSyncWorker.flushPending`, `ScrobbleService` API calls, `FavoriteRepository.mirrorStar`.
- `OfflineAwareHttpDataSource.open()` — the ExoPlayer upstream fails fast (IOException) BEFORE opening a socket while offline, so uncached tracks error in milliseconds and the auto-skip guard advances the queue; cached tracks never reach the upstream (CacheDataSource serves from disk).

### 2. Offline flag survives process death
`OfflineModeManager.initialize()` restores the persisted flag from `SecureStorage` in `FtpmusicApp.onCreate`, ordered BEFORE `downloadManager.start()` and the sync workers.

### 3. Downloads are sacred
`CacheService.removeCached()` refuses to remove a track whose Room row has `isDownloaded = true`. The ONLY deletion paths for downloads are user-initiated (`clearDownloads` from Settings, per-track download removal). The playback error auto-skip may only clear auto-cached content.

### 4. Cache writes never destroy active streams
`CacheService.importIntoCache()` probes the SimpleCache lock with `startReadWriteNonBlocking` first; a locked resource (actively streaming) defers the import and the DownloadManager retries. `removeResource` is only called on unlocked resources.

### 5. Enqueue is atomic
`cache_queue_items.track_id` is UNIQUE (migration 44→45, dedupes pre-existing duplicates). `CacheQueueDao.insertIgnore` returns -1 when a row exists, and the caller upgrades priority/isDownload on the winner. Temp download files are keyed by row id, never by track id alone.

### 6. Queue-always-cached (whole queue)
`PlaybackManager.enqueuePlayQueue` enqueues the ENTIRE queue: the next 3 tracks at priority 0 (streaming-continuity window — always downloaded, no constraint gating) and the remainder at priority 1 (Wi-Fi/battery/mobile-data gated). Completed rows still present in the cache are a no-op — no re-download on every track advance.

## Consequences

- Offline mode is now enforced at every network boundary; the toggle is honest after process death.
- Downloads can no longer be destroyed by playback errors, cache imports, or eviction (pinned).
- The queue progressively caches within quota; long queues on cellular only download the next 3 tracks unless the user allows more.
- Migration 44→45 is required; it is registered in `DatabaseModule` (no destructive fallback).
- Follow-ups (not in this ADR): streaming-floor reservation, cover-art quota wiring to Coil, server scrobble queue reintroduction.
