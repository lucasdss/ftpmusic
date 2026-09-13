# Cast & Playback Behavior Map

## States

| State | Local | Casting |
|-------|-------|---------|
| `PlayerHolder.isCasting` | false | true |
| `PlaybackState.isCasting` | false | true |
| `PlaybackState.isPlaying` | true/false | true/false (from Cast receiver) |
| `PlaybackState.volume` | 0.0-1.0 (phone) | 0.0-1.0 (Cast device) |
| `PlaybackState.castDeviceName` | null | "Mini Speaker" |
| `ExoPlayer.volume` | 1.0 | 0.0f (muted) |
| `PlayerHolder.player` | ExoPlayer (via CastPlayer) | RemoteCastPlayer (via CastPlayer) |
| `mediaSession.player` | CastPlayer | CastPlayer |
| MediaItem URIs | proxy (127.0.0.1:9000) | direct (music.example.com) via converter |

## Commands (UI → Player)

| Command | Local Path | Cast Path |
|---------|------------|-----------|
| Play/Pause | UI → CastPlayer.isPlaying → CastPlayer.play/pause → ExoPlayer | UI → CastPlayer → RemoteCastPlayer → Cast receiver |
| Skip Next | UI → CastPlayer.seekToNextMediaItem() | Same → RemoteCastPlayer |
| Skip Prev | UI → CastPlayer.seekToPreviousMediaItem() | Same → RemoteCastPlayer |
| Seek | UI → CastPlayer.seekTo(ms) | Same → RemoteCastPlayer |
| Volume | UI → CastPlayer.volume = x | Same → deviceVolume on Cast receiver |
| Add Track | UI → PlaybackManager → QueueManager.addMediaItem | Same → RemoteCastPlayer.addMediaItems → Cast receiver |
| Remove Track | UI → QueueManager.removeMediaItem | Same → RemoteCastPlayer.removeMediaItems → Cast receiver |
| Play Album | UI → PlaybackManager.playAlbum → setMediaItems | Same → RemoteCastPlayer.setMediaItems → queueLoad |

## Events (Player → UI)

| Event | Source (Local) | Source (Cast) |
|-------|---------------|---------------|
| isPlayingChanged | ExoPlayer → CastPlayer → listener | RemoteCastPlayer → CastPlayer → listener |
| onMediaItemTransition | ExoPlayer → CastPlayer → listener | RemoteCastPlayer → CastPlayer → listener |
| onPositionDiscontinuity | ExoPlayer → CastPlayer → listener | RemoteCastPlayer → CastPlayer → listener |
| onDeviceInfoChanged | PLAYBACK_TYPE_LOCAL | PLAYBACK_TYPE_REMOTE |
| onDeviceVolumeChanged | N/A | RemoteCastPlayer → listener (device volume) |

## Connect Flow

1. User taps Cast button → CastButtonState.showDialog = true
2. NavHost.refreshCastRoutes → discoveredDevices populated
3. User selects device → route.select() → Cast SDK starts session
4. CastPlayer detects → onDeviceInfoChanged(REMOTE)
5. UI state: isCasting=true, deviceName set
6. ExoPlayer volume = 0f (muted local playback)
7. CastConnected event → PlaybackState updated
8. MediaItemConverter.toMediaQueueItem() converts proxy→direct URLs
9. Windowed queue (~4 items) + autoplay sent to Cast receiver (RemoteCastPlayer limits)
10. Notification updated (notifyChanged)

## Disconnect Flow

1. User taps disconnect → CastButtonState.disconnect()
2. onDisconnectRequested → reset all state + prepare ExoPlayer + restore full queue
3. player.stop() → CastPlayer → RemoteCastPlayer → receiver stops
4. sessionManager.endCurrentSession(true) → terminates Cast session
5. onDeviceInfoChanged(LOCAL) → isCasting=false, CastDisconnected (may not fire)
6. ExoPlayer volume = 1f (restored) + prepare() + full queue restored
7. MediaItemConverter.toMediaItem() — handled internally by CastPlayer
8. Playback ready for local control

## Sync Points

| What | How | Frequency |
|------|-----|-----------|
| Position | positionPoller → CastPlayer.currentPosition → PositionUpdate → PlaybackState | Every 200ms |
| Persist position | persistState() → Room playback_state | Every 5s |
| Persist queue index | savePositionOnly() → SharedPreferences | Every 5s |
| Persist full queue | saveQueueState() → Room queue_items | On track change, pause, destroy |
| Persist playback state | persistState() → Room | On every state change |
| Notification | notifyChanged() | On isPlayingChanged, deviceInfoChanged, disconnect |
| Volume sync | CastPlayer.volume → device volume | On setVolume, onDeviceVolumeChanged |

## Test Scenarios to Verify

### Local Playback
- [x] L1: Play album → verify isPlaying=true, title/artist set
- [x] L2: Play/pause → verify isPlaying toggles
- [x] L3: Skip → verify track changes, scrobble fired
- [x] L4: Seek → verify position updated
- [x] L5: Volume → verify volume changed in state
- [x] L6: Auto-advance → verify next track loads
- [x] L7: Queue empty → verify continuous play adds tracks

### Cast Connection
- [x] C1: Open dialog → verify devices listed
- [x] C2: Select device → verify onDeviceInfoChanged(REMOTE)
- [x] C3: State sync → verify isCasting=true, deviceName set, volume from device
- [x] C4: URL conversion → verify MediaItemConverter.toMediaQueueItem called
- [x] C5: Existing session → verify addListener fires onDeviceInfoChanged

### During Cast
- [x] D1: Play/pause → verify CastPlayer routes to receiver
- [x] D2: Skip → verify track advances on receiver
- [x] D3: Seek → verify receiver position changes
- [x] D4: Volume → verify device volume changes
- [x] D5: Auto-advance → verify onMediaItemTransition fires from Cast
- [x] D6: Add track → verify queue synced to receiver
- [x] D7: Remove track → verify queue synced to receiver

### Disconnection
- [x] X1: User disconnect → verify onDeviceInfoChanged(LOCAL)
- [x] X2: Receiver stops → verify player.stop() called
- [x] X3: State reset → verify isCasting=false, volume=1f
- [x] X4: URL reversion → verify MediaItemConverter.toMediaItem called
- [x] X5: Play after disconnect → verify local playback works
- [x] X6: Position preserved → verify position from Cast is retained

### State Sync
- [x] S1: Position poller → verify written to PlaybackState
- [x] S2: Persist state → verify written to Room
- [x] S3: Restore state → verify loaded on startup
- [x] S4: Notification → verify built with correct state
- [x] S5: Lock screen → verify MediaSession token linked

### Edge Cases
- [x] E1: Connect during paused playback
- [x] E2: Rapid connect/disconnect does not corrupt state
- [x] E3: Load new track during Cast keeps isCasting
- [x] E4: Queue size changes during Cast
- [x] E5: Repeat/shuffle work independently of Cast
- [x] E6: Volume change from Cast device hardware buttons → syncs to UI
- [x] E7: Cast device disconnects while app in background
- [x] E8: App killed while casting → resume on restart
- [x] E9: Multiple rapid skip commands during Cast
- [x] E10: Seek to 0 (restart) during Cast
