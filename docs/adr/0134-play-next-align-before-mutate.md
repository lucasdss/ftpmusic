# 0134 — Play Next: Align Anchor Before Mutate (Not on AUTO)

Status: Accepted
Date: 2026-10-13
Extends: ADR 0039, ADR 0067, ADR 0074
See: `docs/PLAY_NEXT_QUEUE_FINISH_BEHAVIOR_REPORT.md`

## Context

Dual merge `[CONTEXT 0..anchor] + [PRIORITY] + [CONTEXT after]` requires
`contextAnchor` at the live playhead for industry Play Next. Anchor was only
set on `setContext` / restore. Mid-playlist Play Next spliced PRIORITY after
the album start → Exo AUTO continued context. Aligning on every AUTO while
PRIORITY is non-empty reorders merge and can skip unplayed Up Next
(`DualQueueManagerTest.alignAnchorToMergedIndex when playing context suffix`).

Local `addToQueue` appended to Exo end while Dual inserted mid-list → Dual≠Exo.

## Decision

1. Call `alignAnchorToMergedIndex(currentCanonicalIndex)` **only** immediately
   before `playNext` / `addToQueue` / `addAllToQueue`. If merge order changes,
   `syncDualQueueToPlayer` then insert.
2. **Do not** align on every `onMediaItemTransition` AUTO.
3. Local Exo mutations use Dual merge insert index (not append-at-end).
4. Cast `Add` / `AddAll.beforeEntryId` = post-mutation neighbor of inserted
   block (null only for true end-append such as Continuous Play).
5. Up Next UI: `queueIndex > current` only. History exclude: current + upcoming
   only (finished prefix may appear in Recently Played).

## Consequences

- Mid-album Play Next matches Apple/Spotify.
- Unplayed PRIORITY not skipped by per-AUTO realign.
- Persist SoT remains Dual (ADR 0067).
