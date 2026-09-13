# Cast Integration Analysis — ftpmusic

**Date**: 2025-06-17 | **Schema**: Cast V2 via `cast` package + `bonsoir` discovery

## Architecture Overview

```
User taps track → _TrackTile checks playbackModeProvider
  ├─ local → MusicPlayerHandler.playFromUrl() → just_audio → ExoPlayer
  └─ cast  → CastSessionManager.loadMedia() → raw Cast V2 messages → Nest Mini

State flow:
  CastSessionManager ──sets──→ playbackModeProvider (idle/local/cast)
                              castSessionProvider (device, track, state)
                              castIsPlayingProvider
                              castPositionProvider
  MiniPlayer ←──watches── playbackModeProvider + castSessionProvider → adapts UI
  NowPlayingScreen ←──watches── same + castIsPlayingProvider → adapts controls
```

## What Works

| Feature | Implementation |
|---------|---------------|
| Device discovery | bonsoir mDNS, 8s scan interval |
| Connection protocol | Correct LAUNCH→wait→LOAD flow with 12s timeout |
| mediaSessionId tracking | Extracted from MEDIA_STATUS, used in all commands |
| Volume control | SET_VOLUME on media namespace (Nest devices need this) |
| Pause/play/stop | sendMediaCommand with correct mediaSessionId |
| Hostname→IP | _resolveUrl for Cast device DNS compatibility |
| Disconnect | STOP sent before endSession with 400ms delay |
| Queue on Cast | loadQueue builds _castQueue, auto-advances on IDLE |
| Unified UI | Single NowPlayingScreen adapts to Cast/local |
| Device name display | Blue icon + device name in app bar |
| Tests | 15 Cast state/provider tests passing |

## Critical Issues Found

### 1. Memory leak in audio_handler (HIGH)
- `Timer? _positionTimer` never cancelled — fires 250ms forever
- `processingStateStream` and `playerStateStream` subscriptions never cancelled
- `MusicPlayerHandler` has no `dispose()` method

### 2. No metadata on LOAD (HIGH)
- `_loadMedia` sends `contentId`/`contentType`/`streamType` but no `metadata` field
- Cast device screen shows nothing about the track
- `loadQueue` *does* include metadata — inconsistency

### 3. Non-standard QUEUE_UPDATE command (HIGH)
- Auto-advance uses `'type': 'QUEUE_UPDATE'` with `'jump'` field
- Not a documented Cast Media message field
- May silently fail on some receivers

### 4. No seek support on Cast (MEDIUM)
- `sendMediaCommand` handles PAUSE/PLAY/STOP/PREVIOUS/NEXT
- No SEEK command despite having position providers

### 5. Raw auth URLs sent to Cast devices (MEDIUM)
- `buildStreamUrl` embeds Subsonic auth tokens in URL
- Cast devices on network receive full credentials
- PlaybackProxy (port 9000) exists but isn't used for Cast URLs

### 6. _lastPlayedAt map unbounded (LOW)
- Grows with every track ever played, never cleaned

### 7. Cast state not persisted (MEDIUM)
- Session restore only handles local queue
- Cast queue/position lost on app restart

### 8. No connection health monitoring (MEDIUM)
- WebSocket drops go undetected
- `_manager.isConnected` stays true forever after disconnect

## Comparison: ftpmusic vs Spotify Cast

| Feature | ftpmusic | Spotify |
|---------|----------|---------|
| Discovery | ✅ Manual scan | ✅ Bonjour + cloud |
| Pause/play/volume | ✅ | ✅ |
| Seek while casting | ❌ | ✅ |
| Metadata on device | ❌ (on LOAD) | ✅ |
| Queue sync | ⚠️ (local-only) | ✅ Real-time |
| Transfer-to-phone | ⚠️ (no position restore) | ✅ Seamless |
| Connection recovery | ❌ | ✅ Auto-reconnect |
| Credential security | ❌ (raw URLs) | ✅ Device tokens |
| OS notification (Cast) | ⚠️ (shows local state) | ✅ Cast-specific |
| Lock screen controls | ✅ (audio_service) | ✅ |

## Proposed Improvements (prioritized)

1. **Fix memory leaks** — add dispose() to MusicPlayerHandler, cancel Timer + subscriptions
2. **Add metadata to LOAD** — `metadataType: 3` with title/artist
3. **Fix auto-advance** — use `QUEUE_UPDATE` with `currentItemId` instead of `jump`
4. **Add Cast seek** — `{type: 'SEEK', currentTime: seconds, mediaSessionId}`
5. **Use PlaybackProxy for Cast URLs** — bind to 0.0.0.0, pass proxy URLs to Cast
6. **Track Cast duration** — add `castDurationProvider` from MEDIA_STATUS
7. **Connection heartbeat** — periodic ping or WebSocket close handler

## Deferred (larger refactors)

- **PlaybackController abstraction** — unify Cast/local behind a single interface
- **Cast session persistence** — save session across app restarts
- **Multi-room support** — Cast speaker groups
- **iOS Control Center** — AudioServiceConfig iOS settings
- **Cast-specific OS notification** — "Casting to..." text in Android notification
