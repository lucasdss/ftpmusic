# Queue Edit Playback Hiccup — Behavior Report

Date: 2026-09-24
Status: Implemented
Related: ADR 0042, ADR 0031, ADR 0039

## Symptom

Edit queue (drag / remove / clear / play-next) → Now Playing audio gap.

## Root cause (not coroutine starve)

Local edits called `PlaybackManager.syncDualQueueToPlayer()` →
`player.setMediaItems(fullMerged, idx, pos)` → Exo rebuilds timeline → rebuffer.

`addToQueue` already used `addMediaItems` (gapless). Edit path did not.

Drag: `QueueScreen.onMove` fired `moveQueueItem` **every cross** → spam rebuilds.

## Fix (1D + 2A — local Exo)

| Op | Local player API |
|---|---|
| `removeFromQueue` | `removeMediaItem(index)` |
| `moveQueueItem` (immediate) | `moveMediaItem(from, to)` |
| `playNext` | `addMediaItem(insertIndex, item)` |
| `clearPriorityQueue` | `removeMediaItem` PRIORITY indices high→low |
| `clearQueue` | trim after current via `removeMediaItem` |
| Drag reorder | Dual/UI on `onMove`; one `moveMediaItem` on `onDragStopped` |

Cast: keep Dual SoT + entryId cmds; exo mirror may still `syncDualQueueToPlayer`.

`syncDualQueueToPlayer` reserved for: empty, device-switch / ensureExoMatchesDual, Cast ack rollback, ClearAndPlay mirror.

## Drag coalesce

1. `draggableHandle(onDragStarted)` → `beginQueueReorder(entryId, index)`
2. `onMove` → Dual move only (snapshot once)
3. `onDragStopped` → `commitQueueReorder()` → one Exo move + persist + Cast Move

## Edge cases

- Remove playing index → Exo advances; Dual matches
- Move playing item → Exo `moveMediaItem` keeps position
- Drag many rows → one player mutation at release
- Clear priority while CONTEXT playing → CONTEXT continues
- Rapid remove + IO persist → Dual SoT; no stuck timeline
