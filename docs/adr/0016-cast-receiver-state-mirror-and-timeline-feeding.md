# ADR 0016 — Cast Receiver-State Mirror & Timeline Feeding

Date: 2026-08-16
Status: Accepted
Related: ADR-0011 (cast-receiver-source-of-truth), ADR-0012 (media3 dual-queue),
`docs/CAST_STATE_DEATH_BEHAVIOR_REPORT.md`

## Context

The app's UI/notification/system-session state all derive from
`PlayerHolder.player.currentMediaItem` (a media3 `CastPlayer` during cast). The receiver
queue was loaded with the raw Cast SDK (`rmc.queueLoad`), which bypasses media3's
`RemoteCastPlayer.setMediaItems()` — the only path that populates
`CastTimelineTracker`'s contentId→MediaItem map. Result: during cast, `CastPlayer` can
stay IDLE with an empty/invalid timeline while the receiver plays independently,
blanking every state consumer and triggering Android 14+ media-session timeout demotion
(`setFgsInactiveLocked`). Multiple secondary races (rmc-null skip, sticky
`sessionWasResumed`, unregister-without-reregister, `listenerOnExoPlayer` duplicate
listeners) deepen the failure.

## Decision

1. **Feed media3 the queue** — load the receiver queue through
   `CastPlayer.setMediaItems(items, startIndex, position)` instead of raw
   `rmc.queueLoad()`. This keeps the existing `SubsonicMediaItemConverter` behavior
   (customData `trackId`, `preloadTime=45s`, `itemId=mediaId.hashCode()`, autoplay) and
   makes media3's timeline tracker authoritative for the current item.
2. **Mirror the receiver** — add a `castStateSource` hook to
   `MediaSessionPlaybackProvider`, polled on its existing 200ms cadence while
   `PlayerHolder.isCasting`. MediaService builds the state from
   `rmc.mediaStatus.currentItemId` → `resolveLocalIndexFromReceiverId` → local
   `MediaItem` (full metadata/extras) via the pure `PlaybackState.fromCastState()`
   builder (shared field logic with `fromPlayer`). The mirror wins over the player
   patch while casting, so UI/notification live even when media3's timeline lags.
3. **Resilient registration** — remote-setup body extracted to
   `setupRemoteCastSession(rmc)` with bounded retry when the session's
   `remoteMediaClient` is momentarily null; validate the phantom guard before bumping
   `castDisconnectGen`.
4. **Sticky-resume reset** — `sessionWasResumed` clears on `onSessionStarted`/
   `onSessionEnded` so a new session always attempts queue (re)load.
5. **Single listener** — `setListenerPlayer()` replaces the `listenerOnExoPlayer`
   flag; listeners are removed from the inactive player to prevent duplicate events.
6. **No mid-cast player swap** — `PLAY_PAUSE` never switches `mediaSession.player` to
   ExoPlayer while casting; an idle cast player reloads the queue through `CastPlayer`.

## Consequences

- The phone queue remains authoritative (unchanged); the receiver is the source of
  truth for position/current item (unchanged, ADR-0011).
- The system MediaSession publishes real PLAYING state during cast → Android media
  timeout no longer fires → no `setFgsInactiveLocked` demotion.
- New single source for cast state synthesis: `PlaybackState.fromCastState` (pure,
  unit-tested); all consumers keep reading `playbackState: StateFlow<PlaybackState>`.
- `loadFullQueueToReceiver`'s contract changes from "queueLoad via rmc" to
  "setMediaItems via CastPlayer" — its tests were rewritten accordingly.
- Risk: `CastPlayer.setMediaItems` timing vs. media3 state machine — mitigated by the
  mirror (UI can never blank) and by device verification.

## Amendments (2026-08-19 — deep-review findings)

Post-implementation review found and fixed six gaps:

1. **Retry-loop guard (FIND-01)** — `setupRemoteCastSessionWithRetry` captured
   `castDisconnectGen` and every deferred attempt now re-checks
   `shouldAttemptCastSetup(gen)` (`gen == castDisconnectGen && PlayerHolder.isCasting`).
   Prevents stale setups registering on a NEW session (duplicate receiver queueLoads
   = audible restart), re-wiring the mirror after cleanup, or running post-onDestroy.
2. **Safe load routing (FIND-05)** — `CastPlayerImpl` (media3 1.10.1, bytecode-verified)
   forwards `setMediaItems` to the LOCAL ExoPlayer until `isCastSessionAvailable()`
   flips. `loadFullQueueToReceiver` and `ClearAndPlay` therefore use
   `castPlayer.setMediaItems` ONLY when `remoteCastPlayer.isCastSessionAvailable()`
   is true; otherwise they fall back to the session-direct raw `rmc.queueLoad`
   (immune to the CastPlayer state machine). Fixes the device-switch window where a
   ClearAndPlay selection could silently land on the local player instead of the
   receiver.
3. **PLAY_PAUSE recovery (FIND-02)** — the cast-idle branch now re-attempts
   `setupRemoteCastSessionWithRetry()` when `rmc` is null (session in the ~10s
   reconnect retry window) instead of silently no-oping.
4. **Destroy hygiene (FIND-03)** — `performDestroyCleanup()` (extracted from
   `onDestroy`, unit-testable) clears the mirror closure from the @Singleton provider
   (`clearCastMirror()`), resets `PlayerHolder` cast state, and cancels pending
   handler callbacks. A stale closure previously leaked the destroyed service and
   could poll a released player after recreation.
5. **Resume wiring (FIND-04)** — `wireCastStateMirror()` is now also called from
   `onSessionResumed`; a resume after cleanup/process-death previously left the
   mirror null while `isCasting=true` (blank UI recurrence).
6. **Minor** — `PLAYER_STATE_LOADING` counts as playing in the mirror (no "paused"
   flicker on track advance); `lastMirrorTrackId` resets on mirror clear (accurate
   reconnect transition logs); `rmc.getMediaQueue()` null-guarded in setup.

Tests added: routing-guard (available→setMediaItems / local→queueLoad, incl.
ClearAndPlay), `shouldAttemptCastSetup`, PLAY_PAUSE rmc-null recovery,
`wireCastStateMirror`/`clearCastMirror`, LOADING-as-playing. Full suite: 1673 green
(baseline 1634). Coverage on modified logic files ≥80% line+branch
(MediaSessionPlaybackProvider 100%/81.8%; PlaybackState.Companion 100%/87.9%).
Note: `performDestroyCleanup` end-to-end is not unit-testable (MockK description
renderer StackOverflow under spy-original execution with nested mock calls) — its
logic is covered via the extracted helpers.
