# ADR 0005: Playback State Machine

**Date:** 2025-06-29  
**Status:** Accepted  
**Supersedes:** None

## Context

Playback state (`isPlaying`, `title`, `artist`, `position`, `duration`, `queueSize`, etc.) is currently written from 40+ scattered call sites across `PlaybackManager`, `PlaybackViewModel`, `QueueManager`, and `MediaService`. Each writer calls `PlaybackStateHolder.update {}` independently with no coordination.

This causes race conditions when Cast is active:
- `PlaybackManager.playSingleTrack()` sets optimistic state (`isPlaying=true`, `title`, `artist`)
- ExoPlayer callbacks (`onIsPlayingChanged`, `onMediaItemTransition`) overwrite the same fields
- Cast callbacks overwrite them again
- Result: UI flickers — animated EQ bars blink, play/pause button toggles rapidly

## Decision

Introduce a `PlaybackStateController` — a single-threaded actor that is the **sole writer** to `PlaybackStateHolder`. All other components send typed `PlaybackEvent`s to it. The controller decides authoritative state based on current mode (local vs cast) and event history.

**States:** `Idle → Loading → Playing → Paused → Stopped`

**Events:** `LoadTrack`, `PlayRequested`, `PauseRequested`, `BufferReady`, `PlaybackEnded`, `PositionUpdate`, `CastConnected`, `CastDisconnected`, `CastStateUpdate`, `QueueChanged`, `QueueCleared`, `RepeatModeChanged`, `ShuffleModeChanged`, `VolumeChanged`, `SleepTimerSet`, `StarToggled`

**Key rules:**
1. Track metadata (title/artist/album/coverArt) sticks until next `LoadTrack` or `PlaybackEnded` — player callbacks cannot clear it
2. When Cast is connected, local `PlayRequested`/`PauseRequested` events are ignored
3. Position is single-sourced from the active player (local or Cast)
4. Cast ↔ Local transitions preserve position via seek commands

**Persistence:** State is persisted to Room DB for recovery across process death.

## Consequences

- **40+ write sites** → **1 write site** (controller)
- `PlaybackStateHolder.update()` becomes internal
- `MediaService`, `PlaybackManager`, `QueueManager` emit events instead of writing state
- Cast transitions preserve position with no UI flicker
- App restart resumes from last known position/state
