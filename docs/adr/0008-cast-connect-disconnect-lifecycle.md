# ADR 0008: Cast Connect/Disconnect Lifecycle

**Date:** 2025-07-10
**Status:** Accepted
**Deciders:** lucasdss

## Context

When a user casts playback, the app must transition from local ExoPlayer to CastPlayer (remote). When they disconnect, it must transition back. This transition involves player references, state flags, volume, persistence, listener registration, and delayed queue restoration. The flow must handle two disconnect paths (user-initiated via CastButtonState, SDK-initiated via onDeviceInfoChanged) and survive rapid connect/disconnect cycles.

## Decision

### Architecture: CastPlayer as Primary Wrapper

`PlayerHolder.player` is always CastPlayer (wrapping ExoPlayer + RemoteCastPlayer) except after disconnect when it's switched to ExoPlayer directly. This avoids CastPlayer's unreliable delegate switching after `endCurrentSession`.

Media3's CastPlayer handles:
- Delegate routing: ExoPlayer when local, RemoteCastPlayer when remote
- Queue sync to Cast receiver via SubsonicMediaItemConverter

We implement:
- Player reference switching on mode change
- ExoPlayer mute/unmute
- State flag propagation
- Listener re-registration on ExoPlayer after disconnect
- Delayed queue restoration from DB
- Position seek after restore

### Dual Disconnect Paths

1. **User-initiated** (`CastButtonState.disconnect()`):
   - Stop CastPlayer → clear UI → `onDisconnectRequested` callback → end session
   - Callback: clear flags, unmute ExoPlayer, send CastDisconnected + VolumeChanged + PauseRequested, switch to ExoPlayer + addListener, seek to saved position

2. **SDK-initiated** (`onDeviceInfoChanged(false)`):
   - 300ms delay for CastPlayer internal transition
   - Same cleanup + ExoPlayer switch
   - Full queue restore from DB (playAlbum + seekTo)
   - Guarded: `if (PlayerHolder.isCasting) return@launch` to skip if reconnected

### Position Persistence

- **During playback**: Saved every ~5s via `positionPoller` reading `PlayerHolder.player?.currentPosition`
- **On connect**: NOT explicitly saved (position already in DB from periodic saves; sync save caused crashes — see Consequences)
- **On disconnect**: Position restored from DB via `persistenceManager.restore()` → `exoPlayer.seekTo(saved.index, saved.positionMs)`

### Listener Management

- `playerListener` is registered on CastPlayer at startup only
- On disconnect switch to ExoPlayer: `exoPlayer.addListener(playerListener)` to receive `onIsPlayingChanged`
- Listener handles BufferReady, PlayRequested, PauseRequested state transitions

### PLAY_PAUSE Resilience

The PLAY_PAUSE handler in `setControlCallback` guards against STATE_IDLE:

```kotlin
if (player.playbackState == STATE_IDLE) {
    if (player.mediaItemCount == 0) {
        // CastPlayer is stale — switch to ExoPlayer
        ep.addListener(playerListener)
        PlayerHolder.player = ep
        mediaSession.player = ep
        ep.prepare()
        ep.play()
    } else {
        player.prepare()
        player.play()
    }
}
```

This handles system-initiated disconnects where none of our cleanup paths fire.

## Consequences

### Positive
- Connect/disconnect works reliably on device (verified 2025-07-08)
- Position preserved across Cast cycles
- Rapid reconnect guarded (stale queue restore skipped)
- PLAY_PAUSE recovers from any IDLE state

### Negative / Trade-offs
- Removed: `saveQueueState()` on connect — caused process crashes (`runBlocking` interference). Position relies on periodic saves only
- Removed: `savePositionOnly()` in disconnect handlers — async `scope.launch` crashed. Position relies on periodic saves
- Removed: `castPlayer.seekTo()` on connect — index out of bounds (CastPlayer had different queue). Position sync on connect is unreliable
- Listener double-registration risk if both disconnect paths fire (mitigated: `addListener` is idempotent in Media3)
- Duplicated disconnect logic between `onDisconnectRequested` and `onDeviceInfoChanged(false)` (near-identical code in both paths)
- Volume mute (0f) and unmute (1f) on ExoPlayer have no test coverage

### Test Coverage Gaps
- No tests for ExoPlayer volume mute on connect / unmute on disconnect
- No tests for PLAY_PAUSE fallback to ExoPlayer when CastPlayer has 0 items
- No tests for rapid connect→disconnect→reconnect cycle with different devices
- No tests for position poller correctness during Cast

## Related
- ADR 0006: CastPlayer Reintroduction
- ADR 0007: Queue Architecture
- ADR 0001: Cast URL Resolution
