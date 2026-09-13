# ADR 0031: Canonical Cast Queue Mutations

## Status

Accepted.

## Problem

Phone queue and Cast receiver exposed different timelines. Phone used full queue;
receiver could expose trimmed/windowed queue. Positional mutations crossed that
boundary. Some operations also reloaded receiver then sent a second granular
command. Result: wrong item, reverted queue, blank Now Playing.

## Decision

- `DualQueueManager` owns canonical ordered queue.
- Every entry retains context or priority origin.
- `Play Next` inserts after canonical current entry.
- During Cast, ExoPlayer mirrors full canonical queue.
- CastPlayer represents receiver playback only.
- Replacement sends one `ClearAndPlay`.
- Granular mutation sends one ID-addressed command.
- Service resolves media ID to Cast queue item ID at command time.
- Move destination means `insertBeforeItemId`; null means queue end sentinel.
- Queue UI observes canonical revision, not queue count alone.
- Continuous queue extension never runs from Cast timeline callbacks.
- `CastQueueCommandExecutor` owns receiver mutation policy behind one execute
  interface.
- `QueueProjection` owns Player-to-UI metadata projection behind one project
  interface.

## Consequences

Positive:

- Receiver prefix trimming cannot shift command target.
- One gesture produces one receiver mutation.
- Daily Mix and equal-size queue replacement become visible immediately.
- Drag no longer rebuilds full receiver queue.

Limits (superseded by later ADRs where noted):

- Track media ID was queue identity until ADR 0040 (`queueEntryId`).
- Cast ack + optimistic rollback: ADR 0040.
- Origin persist: ADR 0039 (`is_priority`). `context_size` write-only after 0040.

## Follow-up

1. Unique queue-entry ID per occurrence — **done** (ADR 0040).
2. Persist entry origin instead of context count — **done** (ADR 0039).
3. Await Cast `PendingResult`; rollback matching mutation revision on failure — **done** (ADR 0040).
4. Connect command failure result to revision-safe optimistic rollback — **done** (ADR 0040).

## Verification

- `CastQueueCommandExecutor`: 100% line, 100% branch.
- `QueueProjection`: 100% line, 82.4% branch.
