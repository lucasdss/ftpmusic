# 0040 — Queue Entry ID and Cast Ack

Status: Accepted
Date: 2026-09-13
Extends: ADR 0031 follow-ups #1 #3 #4; ADR 0039 `context_size` write-only

## Context

Identity was Subsonic `track.id` as `MediaItem.mediaId`. Duplicate occurrences
broke Cast remove/move (`mediaId.hashCode()` first match), Compose keys, and
restore. Cast commands were fire-and-forget. `context_size` is no longer origin
SoT (`is_priority` is).

## Decision

1. Monotonic Int `queueEntryId` in `MediaMetadata.extras` + `queue_items.entry_id`.
2. `mediaId` stays `track.id`. Cast `setItemId(entryId)`.
3. Persist `queue_state.next_entry_id` so process-death cannot reuse receiver itemIds.
4. `CastQueueAction` Add/Remove/Move/JumpTo address `entryId`, not `mediaId`.
5. Await Cast `PendingResult` on Add/Remove/Move/`queueLoad`. JumpTo fire-and-forget.
   `CastPlayer.setMediaItems` ClearAndPlay has no PendingResult: treat success.
6. Optimistic snapshot carries `revision`. Fail + matching revision = Dual rollback
   + player resync. Stale revision = no-op.
7. Restore origin from `is_priority` only. Keep writing derived `context_size`.
   Drop column later, not this migration.

## Consequences

- Same track twice is addressable.
- Cast fail undoes Dual only when user has not mutated again.
- Unique IDs (0031 #1) and ack/rollback (0031 #3 #4) closed.
- Local-first: Dual + Room remain item SoT. Same-rev Cast-fail rollback is
  Spotify-Cast snap-back (session-coupled), not playlist LWW. See review.

## See also

`docs/QUEUE_ENTRY_ID_CAST_ACK_BEHAVIOR_REPORT.md`

`docs/QUEUE_ENTRY_ID_LOCAL_FIRST_REVIEW.md`
