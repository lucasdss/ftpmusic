# ADR 0010: Bidirectional Cast Queue & Session Sync

## Status
Accepted

## Context
The app uses Media3's `CastPlayer` for Google Cast integration. The current implementation relies on `CastPlayer.onDeviceInfoChanged()` as the sole session lifecycle bridge, which misses critical events: network drops (`onSessionSuspended`), automatic reconnection (`onSessionResumed`), and proper pause-before-disconnect.

Additionally, queue sync is one-directional (local→remote only), causing the local queue to become stale if the Cast receiver changes tracks independently (auto-advance, second sender).

## Decision

### 1. SessionManagerListener Registration
Register a `SessionManagerListener<CastSession>` on `CastContext.sessionManager` in `MediaService.onCreate()`:
- `onSessionSuspended`: Set `PlayerHolder.isCasting = true` (maintains state), log suspension
- `onSessionResumed`: Sync queue, volume, and position from receiver to local state
- `onSessionEnded`: Fall back to `onDeviceInfoChanged(false)` path for cleanup

### 2. Pause-Before-Disconnect
Modify `onDisconnectRequested` to call `RemoteMediaClient.pause()` before:
- Saving the current position
- Calling `switchToLocalPlayback()`
- Calling `endCurrentSession(true)`

This ensures the Cast receiver stops playback before the session is torn down.

### 3. Remote→Local Queue Sync
On `onSessionResumed`:
1. Read `RemoteMediaClient.getMediaQueue().getItemIds()` for the receiver's current queue
2. Map each receiver `itemId` back to track metadata via `RemoteMediaClient.getMediaQueue().getItems()`
3. Convert Cast `MediaQueueItem[]` to Media3 `MediaItem[]` via `SubsonicMediaItemConverter.toMediaItem()`
4. Rebuild the ExoPlayer queue with these items
5. Seek to the receiver's current track and position

### 4. Volume Re-Sync on Resume
On `onSessionResumed`: Read `CastSession.volume` and update `PlayerHolder.castDeviceVolume`.

## Consequences
- Session survives network drops (automatic recovery)
- Queue stays in sync bidirectionally
- Disconnect is clean (pause before end)
- Additional listener registration must be cleaned up in `onDestroy()` to prevent leaks
- Remote→local sync may cause brief UI jitter when rebuilding the ExoPlayer queue
