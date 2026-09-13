# 0038 — Cast Device-Switch Queue Authority

Status: Accepted
Date: 2026-09-13

## Context

Soundbar sessions trim the receiver MediaQueue. Media3 `CastTransferFilter`
can shrink the local ExoPlayer mirror to that subset. Device switch A→B
(`connectToCastDevice` ends A without `switchToLocalPlayback`) then called
`loadFullQueueToReceiver` from Exo — Mini received a wrong/smaller queue and
playback stopped. Manual disconnect also persisted CastPlayer’s window-relative
index via `savePositionOnly`, so recovery sought an older track.

## Decision

1. **DualQueue (+ Room) is SoT.** Exo and the receiver are projections.
2. **`ensureExoMatchesDual()`** rebuilds Exo from DualQueue whenever
   `exo.mediaItemCount != dualQueueSize` before any receiver `queueLoad`.
3. **DeviceId change** clears `sessionWasResumed` so A→B never skips reload.
4. **Skip receiver load** only for same-device resume when Exo already matches Dual.
5. **Disconnect position** uses `resolveCurrentIndexForPersistence` (full-queue
   index), never raw CastPlayer index.

## Consequences

- Soundbar → Mini keeps full phone queue count/contents.
- Stale Cast-window seek after disconnect is avoided.
- Same-device sleep resume still skips redundant `queueLoad` when sizes match.
- See `docs/CAST_DEVICE_SWITCH_QUEUE_BEHAVIOR_REPORT.md`.
