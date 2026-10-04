# 0067 — Dual Queue Persist Source of Truth

Status: Accepted
Date: 2026-10-04
Supersedes: local-Player persist branch in ADR 0007 §2 (Cast-only Dual save)
Related: ADR 0031, 0039, 0040, 0042

## Context

`DualQueueManager` is logical queue SoT (CONTEXT + PRIORITY + entryIds + autoplay stamps).
Room persist mixed sources: Cast used Dual; local `MediaService.saveQueueState` and several
`PlaybackManager` edit paths rebuilt tracks from Player. Dual origin/autoplay flag lists
could length-mismatch Player → silent drop on restore. Market apps treat logical queue as
persist SoT, not decoder timeline.

## Decision

1. **All Room queue writes** (`saveQueueState`, `saveQueueStateSync`, `persistenceSave`
   callers for edits) snapshot via `PlaybackManager.buildQueueStateFromDual()`.
2. **Player** remains playback projection only (gapless granular APIs local; Cast commands
   via entryId / ClearAndPlay).
3. **`playStream`** enters Dual as single-item CONTEXT (keep PRIORITY), same family as
   `playSingleTrack`.
4. **`clearQueue` (trim after current)** persists remaining Dual (or clears Room if empty)
   and emits Cast `ClearAndPlay` when casting.

## Consequences

- Local + Cast restore preserve `is_priority` / `is_autoplay` / entryIds after edits.
- ADR 0007 "DB SoT" now means Dual → Room, not Player → Room.
- `buildQueueState()` / `buildQueueStateFromPlayer` remain for UI/debug projection only;
  not used for authoritative persist.

## See also

`docs/PLAYBACK_QUEUE_MARKET_DRIFT_BEHAVIOR_REPORT.md`
