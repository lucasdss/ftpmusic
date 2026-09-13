# ADR 0011: Cast Receiver as Source of Truth for Queue

## Status
Accepted

## Context
The current implementation incorrectly assumes Media3's `CastPlayer` automatically syncs queue changes to the Cast receiver. In reality, `CastPlayer.setMediaItems()` only updates the local proxy; the receiver's queue must be managed explicitly via `RemoteMediaClient.queueLoad()` and `queueInsertItems()`.

## Decision
1. **Queue mutations during Cast must use `castQueueListener`**: Every `playAlbum`, `playSingleTrack`, `shuffleAlbum`, `playNext`, and `addToQueue` call during an active Cast session must dispatch a `CastQueueAction` to the receiver.
2. **`CastQueueAction.Add` must respect the `index`**: The `insertBeforeItemId` parameter to `queueInsertItems` must use the receiver's MediaQueue item ID, calculated from the `index` via `getItemIds()`. Fall back to `INVALID_ITEM_ID` (append) if the index is at the end.
3. **Session resume must re-register all callbacks**: Both `RemoteMediaClient.Callback` and `MediaQueue.Callback` must be restored on `onSessionResumed`, regardless of `wasSuspended`.

## Consequences
- Local queue changes during Cast now propagate to the receiver correctly.
- Play Next inserts at the correct position instead of always appending.
- Session resume properly restores all receiver state tracking.
- Additional `castQueueListener` traffic may increase network usage during rapid queue operations.
