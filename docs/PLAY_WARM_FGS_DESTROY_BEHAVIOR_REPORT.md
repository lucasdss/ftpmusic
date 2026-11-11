# Play after warm MediaService — FGS destroy race (2026-11-11)

## Symptom

Car BT autoplay worked. After a delta sync brought a new album, tapping play on
that album (and later Daily Mix / other music) seated a Dual queue (`playAlbum`
transition + queue save) then `MediaService` tore down within ~40ms
(`service teardown reason=destroy`, same pid). Restarts logged
`queue_restore_failed=f0` and further play attempts looped destroy/restore.
FULL sync did not fix it — same pattern for album and mix.

## Root cause

Warm `ACTION_INITIALIZE` leaves a non-FGS service with a live
`PlayerHolder.player`. `PlaybackManager.ensurePlayer()` only called
`startForegroundService(ACTION_PLAYBACK)` when the player was **null**, so user
play never reasserted mediaPlayback FGS. That violates ADR-0019 §2–4 and races
Media3 / Android 14–15 demotion/stop during `setMediaItems`. BT always promotes,
which is why car resume looked fine.

`queue_restore_failed=f0` is R8-obfuscated `Throwable.simpleName` (often cancel
after `scope.cancel()` in destroy) — not a domain error code.

Daily-mix `removeCached` and `albumTracksAlbumsZero` were timing correlates, not
causal for the first destroy.

## Fix

1. `ensureForegroundForUserPlayback()` on user play paths only; coalesce when
   `foregroundLive` and recent; never coalesce when player is null.
2. Quiet `restoreQueue` keeps `ensurePlayer()` (no idle FGS).
3. Restore `CancellationException` → `queue_restore_cancelled=…` (full class
   name); do not clear BT flags.
4. Service epoch: stale destroy skips `saveQueueStateSync`; destroy only nulls
   `PlayerHolder.player` when it still points at this instance’s player.

## Tests

- `PlaybackManagerCoverageTest`: warm-player play* promote; restoreQueue no
  promote; coalesce when live.
- `QueueRestoreSafelyTest` / helpers: cancellation classification.
- `MediaServiceFgsReassertTest`: coalesce + epoch + markForegroundAsserted.
