# Continuous Play — Behavior Report

Date: 2026-09-30
Related: ADR-0052, `docs/SURPRISE_ME_BEHAVIOR_REPORT.md`, ADR-0039 (dual queue)

## Scope

When the playing timeline reaches its **last** item and Continuous Play is ON, append up to 10 journal-selected tracks to **CONTEXT** (never Priority). Separate from Surprise Me.

## Settings

| Control | Storage | Runtime |
|---|---|---|
| Continuous Play toggle | `KEY_CONTINUOUS_PLAY_ENABLED` | `PlaybackManager.continuousPlayEnabled` |
| Journal history size | `KEY_QUEUE_JOURNAL_CAP` | `PlaybackManager.setJournalCap` |

Hydrated at boot via `PreferenceBootstrap.hydrateJournalAndContinuousPlay()`.

## Journal write

Upsert on sourced context start (`playAlbum`, `shuffleAlbum`, `pushContext`, `playSingleTrack` with source). Unique on `(source_type, source_id)`. Evict oldest when over cap.

**Not journaled:** add-to-queue, play-next, radio `playStream`, Continuous Play appends themselves.

Surprise Me **is** journaled (`random`/`surprise-me`) — eligible for later selection.

## Trigger (MediaService)

On `onMediaItemTransition` (local player, not Cast):

1. Reset `hasLoadedContinuation` when `mediaId != previousTrackId`.
2. Fire when `ContinuousPlayGate.shouldLoadContinuation(...)` true:
   - not casting
   - `currentIndex >= mediaItemCount - 1`
   - `!hasLoadedContinuation`
   - `continuousPlayEnabled`
3. Set flag → IO: `queueJournalDao.getAllRecent()` → `JournalTrackSelector.select` (≤10, exclude current queue IDs) → resolve `trackDao` → `playbackManager.appendToContext`.

Missing DB rows skipped. Empty journal → no append → natural end.

## Dual-queue rule

Continuation = **context tail only**. User Priority untouched.

## Coexistence

`QueueAutoLoader` may also `appendToContext` for windowed album restore (not gated by Continuous Play). Selector excludes IDs already in the timeline.

## Cast

Continuous Play and QueueAutoLoader skipped while casting.

## Tests

`ContinuousPlayTest`, `JournalTrackSelectorTest`, `QueueJournalTest`, `ContinuousPlayGate` unit tests.
