# ADR 0032: Media-Service Lifecycle Recovery (no dead controls after system service stop)

- **Status:** Accepted
- **Date:** 2026-09-07
- **Applies to:** `compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/MediaService.kt`, `.../playback/MediaSessionPlaybackProvider.kt`, `.../MainActivity.kt`

## Context

Android 14/15 demotes the media-session FGS when playback is inactive, then
idle-stops the service while the app process stays alive (observed 2026-09-07).
Because MediaService runs in-process and controls go through process-wide
singletons (`PlayerHolder`, the DI-singleton MediaLibrarySession,
`MediaSessionPlaybackProvider`), a service destroy left zombie state:

- `PlayerHolder.player = null` + released ExoPlayer, but the singleton session
  stayed registered against the released player → frozen PlaybackState, dead
  media-button routing.
- Transport controls (play/next/prev/seek/toggles) no-op silently
  (`PlayerHolder.player ?: return`).
- Content play already self-healed via `PlaybackManager.ensurePlayer`
  (ACTION_PLAYBACK), but the mini-player transport path and plain app resume
  did not re-arm the service → only a full process restart revived playback.

## Decision

1. **Never drop a transport control into a void.** Controls dispatch only while
   `PlayerHolder.player != null`. Otherwise the provider remembers the latest
   control as pending, fires `onPlaybackUnavailable`, and replays it once a
   Player is attached (`connect`/`onPlayerSwitched`/poller). `playerWired`
   StateFlow exposes pipeline health.

2. **Re-arm from the host.** `MainActivity` wires `onPlaybackUnavailable` →
   start MediaService with ACTION_PLAYBACK + `MediaServiceStartRequest.foregroundRequested`
   (same semantics as `PlaybackManager.ensurePlayer`), with a bounded escalation
   watchdog when the player is not wired within ~3 s.

3. **Resume re-initializes a dead pipeline.** On `onResume`, when no Player is
   wired, start the service with ACTION_INITIALIZE (plain `startService`,
   START_NOT_STICKY pre-warm — safe because there is no active playback to
   demote). Pure decision `shouldReinitializePlaybackServiceOnResume`.

4. **Non-zombie teardown.** Before releasing the service ExoPlayer, re-seat an
   idle ExoPlayer on the singleton MediaSession so straggler commands no-op
   against a live player; `onDestroy` calls `super.onDestroy()` exactly once.

5. **Never park main during teardown.** State/queue persistence snapshots are
   captured synchronously, then drained async on a process-lifetime
   `persistenceScope` (`SupervisorJob + IO`) — never `runBlocking` on main. The
   swipe-away path keeps a bounded (≤1.5 s) synchronous flush.

6. **Scope & failure hygiene.** IO scope gets a `SupervisorJob`; `onCreate`
   failure calls `stopSelf()` instead of leaving a half-wired zombie service.

## Consequences

- Return to the app or any transport tap after a system service stop restores
  playback without a force-restart (pending-control replay).
- Main thread no longer stalls behind Room during destroy; narrower resume race.
- Trade-off: last-chance persistence on system idle-stop is best-effort async
  (process may die before the write drains) — accepted; Room writes are small.

## Tests

Resume-decision matrix (`MainActivityFgsDecisionTest`), provider re-arm +
replay + poller survival (`MediaSessionPlaybackProviderTest`), coalescer
watchdog (`SeekCoalescerTest`).
