# ADR 0007: Unified Queue Architecture — Database as Source of Truth

**Date:** 2026-07-07  
**Status:** Accepted

## Context

The queue architecture had three separate concerns that diverged during Cast:

1. **Queue display** was split between `PlayerHolder.player` (CastPlayer during Cast, ~3-4 items from receiver's windowed queue) and `PlayerHolder.exoPlayer` (full ~11 items). Track count labels, queue item lists, and "now playing" highlights read from different players depending on timing.

2. **Queue persistence** saved from `PlayerHolder.player` (CastPlayer's windowed queue) during Cast, corrupting the Room database with only 3-4 items. Disconnect restore then compared the corrupted DB size against `exoPlayer.mediaItemCount` and skipped restore entirely.

3. **Cast state** was stored in SharedPreferences while queue track data was in Room. Inconsistent persistence layers.

The user's stated requirement was clear: "The database is the source of truth for queue control for both players."

## Decision

### 1. Single persistence layer — Room

- **`queue_items`** table stores track metadata (id, title, artist, url, position) — was already in Room
- **`queue_state`** table (NEW) stores playback state in a single row: `currentIndex`, `positionMs`, `isCasting`, `castDeviceName`
- `QueuePersistenceManager` no longer depends on `Context`/`SharedPreferences` — all reads/writes go through `QueueDao`

### 2. Full queue saved during Cast

- `saveQueueState()` and `saveQueueStateSync()` in `MediaService` check `PlayerHolder.isCasting` and read from `PlayerHolder.exoPlayer` when true, ensuring the full queue is persisted
- `PlaybackManager.buildQueueStateFromPlayer()` made accessible for direct ExoPlayer queue building during Cast

### 3. Windowed auto-loading for both players

- **ExoPlayer** (local): loads the full queue via `playAll()` → `setMediaItems(all)`. Typically 11-100 items. Memory-efficient for local playback.
- **CastPlayer** (remote): receiver holds ~3-4 items via RemoteCastPlayer's MediaQueue windowing. `QueueAutoLoader` loads next `CHUNK_SIZE=5` from DB when within `LOAD_AHEAD_THRESHOLD=2` of window end.
- Both players share the same `onMediaItemTransition` → `QueueAutoLoader.shouldLoadMore()` → `addAllToQueue()` path

### 4. Queue display reads from full queue

- `QueueScreen`: reads from `PlayerHolder.exoPlayer` during Cast (full queue items + full index), not CastPlayer's windowed data
- `getUpcomingTracks()` and `refreshQueueDownloadStatus()`: same — check `isCasting`, read from `exoPlayer`
- `QueueChanged` events in `onMediaItemTransition`: report `exoPlayer.mediaItemCount` during Cast

### 5. Disconnect restores from DB

- `onDeviceInfoChanged(false)` handler reads `persistenceManager.restore()` (full queue from Room), compares with current `exoPlayer.mediaItemCount`, and calls `playAlbum()` to restore if needed
- Only one restore path — duplicate `onDisconnectRequested` callback queue restore removed
- Handler wrapped in `postDelayed(300ms)` to let CastPlayer finish internal remote→local transition

## Consequences

### Positive

- **Single source of truth**: Room DB is the canonical queue state. All reads and writes go through the same persistence layer.
- **No data loss during Cast**: Full queue is always preserved, regardless of CastPlayer's receiver window size.
- **Unified auto-loading**: Both ExoPlayer and CastPlayer use the same `QueueAutoLoader` mechanism. No Cast-specific queue logic.
- **Correct queue display**: Track count, queue items, and "now playing" highlight all show the full queue during Cast.

### Negative

- `QueueAutoLoader` thresholds are tuned for small windows (threshold=2, chunk=5). If queue sizes grow significantly, these may need adjustment.
- `QueuePersistenceManager.saveCastState()` uses `runBlocking` for synchronous API compatibility. Acceptable since casts are user-initiated, infrequent operations.
- Migration 29→30 adds `queue_state` table. `QueueStateEntity` must be in the Room entity list.

### Risks

- `QueueAutoLoader` relies on `onMediaItemTransition` firing correctly from both players. CastPlayer's transition callbacks are tested in ADR 0006. If a player implementation stops firing this event, the auto-loader silently stops.
- Rapid Cast connect/disconnect cycles could trigger overlapping `postDelayed` handlers. Mitigated by `isCasting` guard in save methods.
- `localQueueSize` in `QueueManager` is the canonical queue count. It can theoretically diverge from actual player state if external code modifies the player directly. All production mutations go through `QueueManager` methods, so divergence is unlikely in practice. No reconciliation mechanism exists.
- **Known duplication**: `playback_state` table stores `trackId`, `title`, `artist`, `album`, `positionMs`, `isCasting`, `castDeviceName` — all also present in `queue_items` + `queue_state`. This duplication exists because `playback_state` provides fast path restore for QuickSettings/lock screen state on app restart, while `queue_items` + `queue_state` provide full queue restore. Planned: merge into single `queue_state` table (add `isPlaying`, `repeatMode`, `shuffleEnabled` to `QueueStateEntity`, drop `playback_state` table).

## Alternatives Considered

1. **Keep ExoPlayer full-queue, skip auto-loading**: Simpler but Cast queue display would remain wrong.
2. **Window both players to 5 items**: Implemented `QueueManager.playAll()` with window size, but test breaks and complexity from `buildMediaItem` signature mismatches made it impractical in one pass. Deferred.
3. **SharedPreferences for Cast state**: Rejected in favor of Room consistency.

## Related

- ADR 0006: CastPlayer Re-introduction with MediaItemConverter
- ADR 0004: Dual-URL Queue Persistence
- ADR 0005: Playback State Machine
