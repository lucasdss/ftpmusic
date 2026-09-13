# ADR 0013: Local-First Architecture & Offline Playback

## Status
Accepted (enforcement layer implemented 2026-08-24 — see ADR 0022 for the offline-enforcement and download-safety decisions that completed this ADR's contract)

## Context
The app currently has a hybrid architecture: the Library tab uses DB-first reads with API refresh, but Search uses API-first reads. Both sync workers (`MetadataSyncWorker`, `PlaylistSyncWorker`) use custom coroutine loops instead of `WorkManager`. `PendingPlaylistChangeEntity` demonstrates an offline action queue pattern but has no formal conflict resolution (only mark-as-conflicted when the server rejects a change).

## Decision

### 1. Room DB as Source of Truth (Partial — already done)
The Library tab already observes Room DB for albums, artists, playlists, genres, recently played, and stats. No change needed here. Search results should also be written to Room DB before displaying to the UI, enabling offline search.

### 2. WorkManager Sync Workers
Replace `MetadataSyncWorker`'s custom coroutine loop with a `PeriodicWorkRequest` (30-minute interval, `NetworkType.CONNECTED` constraint). Replace `DownloadManager`'s custom constraint checks with `WorkManager.Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)`.

### 3. Conflict Resolution — Last-Write-Wins (LWW)
For `PendingPlaylistChangeEntity`, add a `server_updated_at` timestamp column. When syncing, compare local `updated_at` with server's `updated_at`. If the server timestamp is newer, set `isConflicted = true` and preserve the local change. If the local change is newer, push to server.

### 4. Delta Sync
For playlist track changes, use `addIds` and `removeIndices` parameters instead of full `sync_tracks` replacement. This already exists in `PlaylistSyncWorker` for `add_tracks` and `remove_tracks` change types.

### 5. CacheDataSource (Already Done)
The existing `CacheDataSource` with `SimpleCache` already provides continuous offline playback. No change needed.

### 6. Proactive Offline Preloading (Already Done)
`DownloadManager` already has a priority queue (0=play-queue, 1=download, 2=cache-warming). No change needed.

## Consequences
- Search results cached to Room DB for offline availability
- WorkManager sync is more battery-friendly (Doze-aware)
- Conflict resolution provides clear user feedback (conflicted playlists)
- No architectural changes to the production-proven CacheDataSource or DownloadManager
