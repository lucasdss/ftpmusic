# PlaybackManager Coverage — Behavior Report

Date: 2026-10-04  
Related: ADR-0065, ADR-0064, ADR-0053

## Goal

Lift `PlaybackManager` JaCoCo **branch ≥80%** / **line ≥80%** (was ~65% branch).
Cover real play/queue/cast/restore edges — not synthetic default-arg noise.

## Hardening shipped

1. **tracks/urls length guard** (`alignedTracksAndUrls`) — mismatch → coerce to `min` + DiagnosticLog; empty urls → no-op. Applied to `playAlbum`, `restoreQueue`, `shuffleAlbum`, `pushContext`, `appendToContext`, `enqueuePlayQueue`.
2. **Cast→local seek** — after atomic `restoreQueue(..., positionMs)`, skip redundant `ep.seekTo` when queue rebuilt. If restore returns false (empty/invalid urls), seek truncated local instead.
3. **Autoplay ↔ entryId memory** — `withAutoplay` / `withQueueEntryId` copy sibling JVM identity stamps so Continuous Play clear-autoplay works under unit Bundle stubs.
4. **Restore parallel arrays** — when tracks/urls coerce shorter, prefix-align `isPriorityFlags` / `entryIds` via `alignParallelList`; pass coerced `si` into `enqueuePlayQueue`.
5. **`restoreQueue` returns Boolean** — true when rebuilt; false on no-op (MediaService cast→local fallback).
6. **Dead `queueGeneration` removed** — was incremented, never read.

## Edge cases tested

| Case | Test |
|---|---|
| clearAutoplay no-op / local / cast / empty→clear | `PlaybackManagerBranchCoverageTest` |
| castAck success / fail rollback / empty clear / wrong rev | same |
| clearPriority cast sync + empty clear | same |
| pushContext journal / full-dedupe / si clamp | same |
| syncDualQueueToPlayer cast / empty local / null mediaId | same |
| enqueuePlayQueue throw continues / clamp past coerce | same |
| empty shuffle / playSingle journal XOR | same |
| empty urls / tracks>urls coerce | same |
| restore flag/id mismatch / zero entryId / empty urls | `RestoreQueueTest` |
| restore coerce keeps prefix flags+ids / return bool | same |
| truncated restore no-op → seek | `MediaServiceCastQueueTest` |
| autoplay survives entryId rebuild | `AutoplayMediaExtrasTest` |

## Perf notes

- No new main-thread IO / Compose collectors.
- Length guard = O(1) size check + `take(n)`.
- Branch suite uses `UnconfinedTestDispatcher` (no `delay` flakiness).
- MediaService `runBlocking` cast→local restore still documented out-of-scope (ANR risk separate).

## Coverage gate (re-measured)

Playback-focused unit suite + `:app:jacocoTestReport`:

| Metric | Gate | After P0/P1 |
|---|---|---|
| Line | ≥80% | **98.3%** (678/690) |
| Branch | ≥80% | **82.7%** (349/422) |

Also: autoplay/entryId memory propagation; shared `trackAndUrlFromMediaItem`; non-inline cast emit helpers.
