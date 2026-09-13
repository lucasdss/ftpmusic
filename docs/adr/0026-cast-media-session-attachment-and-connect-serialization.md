# ADR-0026: Cast MediaSession Attachment Invariant + Connect Serialization

**Date:** 2026-08-27
**Status:** Accepted
**Supersedes:** part of ADR-0008 (connect/disconnect lifecycle) and ADR-0016 (receiver-state mirror) — this ADR hardens the session-attachment rule they assumed.

## Context

Cast sessions on this app repeatedly crash, hang, or diverge from the UI. On
2026-08-27 a full repro was captured from device logcat (Pixel 8 Pro, app v3/1.2.0):

1. **Process crash** — `IllegalStateException` at
   `androidx.media3.session.PlayerWrapper.createPositionInfo` (media3 1.10.1):
   `checkState(currentIndex < windowCount)` fails when `RemoteCastPlayer` flushes
   an empty timeline during session teardown while the `MediaSession` is still
   attached to the `CastPlayer`. Dropbox shows the same crash repeatedly
   (4× in 14 s on 08-24). Upstream fix exists only in media3 1.11+.
2. **Reconnect hang** — after a manual disconnect, tapping the device again sets
   `connectingDeviceName` ("Connecting…") but no session is ever created:
   logcat shows **no `requestCreateSessionWithRouter2`** after the release. The
   Cast SDK `SessionManager` still owned a stale/desynced session (GMS
   re-created the old receiver session with `uniqueRequestId=0`, logged
   `Ignoring session creation result for unknown request`), so `route.select()`
   was a no-op. No timeout existed, so the picker stuck forever.
3. **Blue icon, phone playback** — after a network drop, `onSessionResumed`
   restored `isCasting=true` (blue icon) but never re-seated
   `PlayerHolder.player`/`mediaSession.player` to the `CastPlayer` (media3 keeps
   `DeviceInfo` remote across suspension — session non-null both sides — so
   `onDeviceInfoChanged(remote=true)` never fires on resume). Tapping Play then
   started the phone's ExoPlayer.

## Decision

### 1. MediaSession-attachment invariant (crash fix)

`mediaSession.player` must never observe `CastPlayer`'s shrinking/empty
timeline.

- **Default attach:** `MediaService.onCreate` attaches the **local ExoPlayer**,
  not the CastPlayer. A live session re-seats CastPlayer via
  `attachCastPlayerToSession()` (single attachment point) called from
  `onSessionStarted`, `onSessionResumed`, and `onDeviceInfoChanged(remote=true)`.
- **Synchronous detach guard:** the app's `playerListener.onTimelineChanged`
  (registered on CastPlayer before MediaSession's own listener) checks
  `shouldDetachEmptyTimeline()` — attached-to-CastPlayer AND empty timeline AND
  no live session → `detachCastSessionFromMediaSession()` synchronously,
  preempting the `PlayerWrapper` crash dispatch. The same guard runs at the top
  of `onDeviceInfoChanged(remote=false)`.
- **All session-loss paths detach synchronously** (`onSessionEnding`,
  `onSessionEnded`, `onSessionResumeFailed`).

### 2. Connect serialization (hang fix)

MediaService owns manual route selection (`CastButtonState.onConnectRequested`
→ `connectToCastDevice()`):

- If the `SessionManager` still owns a session (stale, phantom, or a device
  switch target), end it FIRST (`endCurrentCastSession()` /
  `endSessionForSwitch()`), then `route.select()` after the teardown settles
  (800 ms) — the select is never a no-op.
- A connect timeout (`castConnectTimeoutMs = 10 s` + 1 s grace re-check) fails
  the attempt (clear `connectingDeviceName`/`isConnecting`, show an error,
  hard-reset the session) when no session became real — the picker can never
  stick.
- Cancel (picker dismiss) calls `cancelCastConnect()` — clears the timeout and
  connecting state.
- `connectToCastDevice` ignores duplicate requests while one is in flight
  (updating the displayed target to the latest tap), and never tears down a
  **healthy** same-device session (only stale/phantom sessions with no live
  media status get the teardown-reconnect path).
- Reconnect retry (`attemptCastReconnect`) skips when the Cast SDK already
  re-established a session for the same device (phantom resume), and tears down
  a desynced session on another device before selecting.

**Switch-pending guard (Edge-01, from the 2026-08-27 review):** a device switch
sets `castSwitchInProgress` before ending the old session. `onSessionEnded` of
the OLD session then skips `clearCastConnectAttempt()` (which would kill the
deferred `route.select(B)`), skips caching the old device for reconnect, and
skips the reconnect retry + force-local fallback — the deferred select owns the
transition. `onDeviceInfoChanged(remote=false)`'s 300 ms cleanup also
early-returns while a connect is in flight (Edge-02), so no full local teardown
runs in the switch gap.

### 3. Resume re-seat (blue-icon/phone-playback fix)

`onSessionResumed` restores Cast state AND calls `attachCastPlayerToSession()`.
`disconnectingManually` is reset on suspension and on `remote=true` so a stale
flag can never skip the `remote=false` cleanup. At service start, a
`currentCastSession` that is already connected seeds Cast state (Edge-04), so
process-death auto-reconnect can never leave a phone-bound player under an
active cast. The empty-timeline detach guard treats a session as "live" only
when `session.isConnected` AND `remoteMediaClient != null` (Edge-08/L10) — a
stale/phantom non-connected session, or a connected-but-idle one with no
receiver media, cannot suppress the crash guard.

## Review Round 2 (2026-08-27) — stuck-state closure

A second adversarial review (Edge-Case + Performance agents) found the connect
serialization could itself reproduce the Bug C divergence through the FAILURE
paths:

- **H1 (failed switch stuck state):** when a switch A→B failed (B never
  established), the timeout/failCast paths cleared only the connect flags — a
  stale `isCasting=true` with a sessionless CastPlayer remained (blue icon +
  phone playback). **Fix:** `failCastConnect` and the timeout now call
  `forceLocalPlaybackAfterCastFailure()` when no session is actually live
  (and `endCurrentCastSession()` first). When a DIFFERENT session survived the
  failed switch (GMS `endCurrentSession` no-op), its cast state is KEPT — the
  blue icon stays accurate.
- **H2 (cancel-during-switch nondeterminism):** `cancelCastConnect` now
  records whether the cancelled attempt was tearing down an old session and
  force-local-recovers when no session is live — both cancel/teardown orderings
  converge to deterministic local playback.
- **M4:** `onSessionResumeFailed` early-returns when a connect is in flight —
  it can no longer kill a manual connect's deferred select + timeout.
- **M5:** the connect timeout aborts only when no CONNECTED session exists on
  the TARGET device (`castConnectTargetDeviceId`); `onSessionStartFailed` has
  no isCasting guard — a start-failure during a switch cleans up immediately.
- **M7:** a `castConnectSeq` token invalidates a cancelled/superseded attempt's
  deferred 800 ms `route.select` (the deadline-capture equivalent). A second
  tap during a connect SUPERSEDES the target (name, timeout, deferred select)
  instead of displaying one device while connecting another.
- **M8:** the dead `isConnecting` state was removed — `connectingDeviceName`
  is the single connecting-state source.
- **M6:** the receiver-state poller emits only on actual snapshot change
  (paused cast no longer causes 5 identical StateFlow emissions/sec).
- **L9:** the cast picker hoists state reads and remembers the device list —
  no SDK lookup inside composition.
- **L10:** `hasLiveCastSession()` additionally requires `remoteMediaClient != null`.
- **L11:** the duplicate 300 ms queue-load in `onSessionStarted` was removed —
  `setupRemoteCastSession`'s 500 ms load is the canonical, single load.

JaCoCo: `jacocoTestReport.classDirectories` now points at
`bundleDebugClassesToRuntimeJar/classes.jar` (the classes the tests actually
executed) instead of `tmp/kotlin-classes/debug` — this eliminated the
"Execution data does not match" under-count for the Hilt-annotated outer
classes.

## Consequences

- **Positive:** the 08-27 crash path is closed at the app level (media3 1.10.1
  stays pinned; the 1.11 upstream fix is a future upgrade, not a dependency of
  this fix). Reconnects are serialized and time-bounded; Cancel always
  un-sticks the picker; resume can no longer leave a blue icon pointed at the
  phone; failed switches and cancels recover deterministically to local
  playback.
- **Negative:** `MediaService.onCreate` no longer attaches CastPlayer at boot —
  a resumed session's queue load now flows through `onSessionResumed` (which
  re-seats first), adding one synchronous hop before the receiver is controlled.
- **Testing:** pure decision functions (`shouldDetachEmptyTimeline`,
  `shouldIgnoreConnectRequest`, `shouldAbortConnectTimeout`) are unit-tested;
  handler behaviors covered by simulation tests (`CastReconnectHangTest`) and
  REAL-code spy-harness tests (`CastConnectFailureTest` — H1/H2/M4/M5/M7 +
  Edge-05 healthy/stale same-device, device-switch ordering, route selection).

## References

- Crash: `PlayerWrapper.createPositionInfo` checkState — media3 1.10.1,
  fixed upstream in 1.11.
- Phantom session: MR2 `Ignoring session creation result for unknown request`
  (uniqueRequestId=0), 2026-08-27 14:23:58.
- Guard predecessor: `detachCastSessionFromMediaSession()` in
  `onSessionEnding/onSessionEnded` (2026-08-22) — proven insufficient, kept as
  defense in depth behind the synchronous listener guard.
