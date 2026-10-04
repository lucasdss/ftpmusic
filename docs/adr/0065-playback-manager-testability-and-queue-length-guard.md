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

A follow-up review found: restore coerced tracks/urls but left
`isPriorityFlags`/`entryIds` at the old length (stamps dropped); enqueue used
raw `startIndex` after coerce; cast→local skipped seek when restore no-op'd;
dead `queueGeneration` counter never read.

## Decision

1. **Equal-length contract.** Public play/restore/append/enqueue entry points
   run `alignedTracksAndUrls`: empty urls → no-op; size mismatch → coerce to
   `min(tracks, urls)` + `ftpmusic-playback` / DiagnosticLog breadcrumb.
   Restore also prefix-aligns `isPriorityFlags` / `entryIds` when they match
   the pre-coerce track count (`alignParallelList`).
2. **Cast→local.** When `restoreQueue` rebuilds with `positionMs`, do not call
   a second `seekTo` (atomic start already applied). Seek only when the local
   queue was already full-sized, **or** when truncated restore returns false
   (empty/invalid urls) — then seek the truncated local queue.
3. **Stamp memory propagation.** `withAutoplay` copies `QueueEntryIdMemory`;
   `withQueueEntryId` copies `AutoplayFlagMemory`.
4. **Coverage.** Branch suite
   `PlaybackManagerBranchCoverageTest` + restore mismatch tests; gate remains
   ≥80% line and branch on `PlaybackManager`.
5. **Deduped snapshot mapping.** `trackAndUrlFromMediaItem` shared by Dual/Exo
   queue snapshots; cast emit helpers are non-inline for honest JaCoCo.
6. **`restoreQueue`: Boolean.** Returns whether rebuild happened so MediaService
   can fall back. Coerced start index is what `enqueuePlayQueue` receives.
7. **No generation theater.** Remove unread `queueGeneration` increments.

## Consequences

- Torn URL lists no longer enqueue past zip length; restore keeps prefix
  priority/entry metadata when urls truncated.
- Autoplay clear works in JVM tests and after entry-id restamp on device.
- Cast→local empty-url restore no longer leaves position unseeked.
- PlaybackManager measured **98.3% line / 82.7% branch** after P0/P1 follow-up.
- `runBlocking` on cast disconnect remains a known ANR risk (future ADR).
