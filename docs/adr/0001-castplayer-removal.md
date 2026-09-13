# ADR 0001: CastPlayer Removal — ExoPlayer + RemoteMediaClient architecture

**Date:** 2025-06-24
**Status:** Superseded by [ADR 0006](0006-castplayer-reintroduction.md)

## Context

The media3 `CastPlayer` wraps `ExoPlayer` as its local player. When a Cast session starts,
CastPlayer's `TransferCallback` clears the ExoPlayer queue — items that previously held
proxy URLs (`127.0.0.1:9000/stream?...`) are replaced with cast URLs. This destroys the
canonical local queue and requires save/restore logic to recover after disconnect.

Additionally, CastPlayer's `setMediaItem()` triggers `onMediaItemTransition` callbacks
that cause UI recomposition storms — seek bar reset, title/artist flicker, star icon blink.

## Decision

**Remove `media3.cast.CastPlayer` entirely.** The architecture uses:

1. **`ExoPlayer`** as the sole playback engine — holds the canonical queue with proxy URLs
2. **`RemoteMediaClient`** (Cast SDK) directly to send tracks to Cast devices via `load()`
3. **Volume-based muting** — `exoPlayer.volume = 0f` during Cast, `1f` on disconnect. Never pauses ExoPlayer, so position tracking continues and auto-advance works from the local timeline
4. **`SessionManagerListener`** registered on `CastContext.sessionManager` directly — no CastPlayer needed for session lifecycle

## Consequences

**Positive:**
- One canonical queue — ExoPlayer items are never cleared or replaced
- Zero UI recomposition during Cast start (no `onMediaItemTransition` storms)
- `PlayerHolder.player` always references ExoPlayer — simplified control routing
- No queue save/restore needed on connect/disconnect

**Negative:**
- Cast session resume (error 2161) occurs if ExoPlayer has zero items at connect time — user must start local playback before connecting to Cast
- `RemoteMediaClient` has a less rich API than CastPlayer — some advanced features may need re-implementation
- Seeking remotely requires explicit `RemoteMediaClient.seek()` call alongside local `ExoPlayer.seekTo()`

## Alternatives Considered

1. **CastPlayer wrapping ExoPlayer with save/restore** — working solution but requires queue duplication logic
2. **CastPlayer standalone (no local player)** — broke Cast SDK session lifecycle entirely (error 2161)
3. **Two separate players** — added complexity with no benefit over RemoteMediaClient approach
