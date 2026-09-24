# ADR 0042 — Gapless Local Queue Edits

Date: 2026-09-24
Status: Accepted
Related: ADR 0031 (canonical Cast mutations), ADR 0039 (dual-queue semantics),
ADR 0012 (Media3 dual queue)

## Context

`DualQueueManager` is the canonical queue. After each edit,
`PlaybackManager.syncDualQueueToPlayer()` called `Player.setMediaItems` with the
full merged list. That reloads ExoPlayer sources and caused audible Now Playing
hiccups on remove / move / play-next / clear, and especially on drag-reorder
(one full replace per pixel step). Append paths already avoided this via
`addMediaItems`.

## Decision

1. **Local edits use granular Exo APIs** — `removeMediaItem`, `moveMediaItem`,
   `addMediaItem(index)` — matching the existing gapless append pattern.
2. **Cast path unchanged this pass** — entryId commands + optional exo-mirror
   `syncDualQueueToPlayer` when `PlayerHolder.isCasting`.
3. **Drag coalesce** — Dual/UI update during `onMove`; single player sync on
   `draggableHandle` `onDragStopped` via `beginQueueReorder` /
   `commitQueueReorder`.
4. **Keep** `syncDualQueueToPlayer` for divergence repair, empty queue,
   Cast ack rollback, and ClearAndPlay mirror.

## Consequences

- Queue edits no longer restart the playing item's MediaSource locally.
- Drag releases commit one Cast `Move` instead of N intermediate Moves.
- Tests assert local remove/move/playNext never call `setMediaItems`.
