# PlaybackManager Coverage — Behavior Report

Date: 2026-10-04  
Related: ADR-0065, ADR-0064, ADR-0053

## Goal

Lift `PlaybackManager` JaCoCo **branch ≥80%** / **line ≥80%** (was ~65% branch).
Cover real play/queue/cast/restore edges — not synthetic default-arg noise.

## Hardening shipped

1. **tracks/urls length guard** (`alignedTracksAndUrls`) — mismatch → coerce to `min` + DiagnosticLog; empty urls → no-op. Applied to `playAlbum`, `restoreQueue`, `shuffleAlbum`, `pushContext`, `appendToContext`, `enqueuePlayQueue`.
2. **Cast→local seek** — after atomic `restoreQueue(..., positionMs)`, skip redundant `ep.seekTo` when queue rebuilt.
3. **Autoplay ↔ entryId memory** — `withAutoplay` / `withQueueEntryId` copy sibling JVM identity stamps so Continuous Play clear-autoplay works under unit Bundle stubs.

## Edge cases tested

| Case | Test |
|---|---|
| clearAutoplay no-op / local / cast / empty→clear | `PlaybackManagerBranchCoverageTest` |
| castAck success / fail rollback / empty clear / wrong rev | same |
| clearPriority cast sync + empty clear | same |
| pushContext journal / full-dedupe / si clamp | same |
| syncDualQueueToPlayer cast / empty local / null mediaId | same |
| enqueuePlayQueue throw continues | same |
| empty shuffle / playSingle journal XOR | same |
| empty urls / tracks>urls coerce | same |
| restore flag/id mismatch / zero entryId / empty urls | `RestoreQueueTest` |
| autoplay survives entryId rebuild | `AutoplayMediaExtrasTest` |

## Perf notes

- No new main-thread IO / Compose collectors.
- Length guard = O(1) size check + `take(n)`.
- MediaService `runBlocking` cast→local restore still documented out-of-scope (ANR risk separate).

## Coverage gate (achieved)

Playback-focused unit suite + `:app:jacocoTestReport`:

| Metric | Before | After |
|---|---|---|
| Line | ~87% | **98.0%** (672/686) |
| Branch | ~65% | **82.2%** (350/426) |

Also: autoplay/entryId memory propagation; shared `trackAndUrlFromMediaItem`; non-inline cast emit helpers.
