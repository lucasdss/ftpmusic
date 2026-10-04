# ADR 0065 — PlaybackManager Testability and Queue Length Guard

Date: 2026-10-04  
Status: Accepted  
Related: ADR-0064, ADR-0007, ADR-0053

## Context

`PlaybackManager` sat at ~65% JaCoCo branch coverage. Happy play paths were green;
cast-ack failure, clear-autoplay, restore flag mismatch, and enqueue errors were
untested. Separately, `tracks.zip(urls)` silently truncated while
`enqueuePlayQueue` indexed the full tracks list — short `urls` → OOB / wrong
stream under metadata.

JVM tests stub `Bundle`, so `is_autoplay` / `queueEntryId` identity memories
must travel across `MediaItem.buildUpon()` or Continuous Play stamps vanish
inside DualQueue.

## Decision

1. **Equal-length contract.** Public play/restore/append/enqueue entry points
   run `alignedTracksAndUrls`: empty urls → no-op; size mismatch → coerce to
   `min(tracks, urls)` + `ftpmusic-playback` / DiagnosticLog breadcrumb.
2. **Cast→local.** When `restoreQueue` rebuilds with `positionMs`, do not call
   a second `seekTo` (atomic start already applied). Seek only when the local
   queue was already full-sized.
3. **Stamp memory propagation.** `withAutoplay` copies `QueueEntryIdMemory`;
   `withQueueEntryId` copies `AutoplayFlagMemory`.
4. **Coverage.** Branch suite
   `PlaybackManagerBranchCoverageTest` + restore mismatch tests; gate remains
   ≥80% line and branch on `PlaybackManager`.
5. **Deduped snapshot mapping.** `trackAndUrlFromMediaItem` shared by Dual/Exo
   queue snapshots; cast emit helpers are non-inline for honest JaCoCo.

## Consequences

- Torn URL lists no longer enqueue past zip length.
- Autoplay clear works in JVM tests and after entry-id restamp on device.
- PlaybackManager measured **98% line / 82% branch** after this ADR.
- `runBlocking` on cast disconnect remains a known ANR risk (future ADR).
