# ADR 0132 — Android Auto Hardening

Date: 2026-10-13
Status: Accepted
Related: ADR-0131 (peer parity), ADR-0087 (resumption), ADR-0118 (reactions), ADR-0039 (dual queue)

## Context

Deep review of ADR-0131 found correctness holes: voice play drops when
`expandToAlbum` is null; Auto/voice `setMediaItems` never adopts DualQueue
(BT/resume desync); radio reactions race via notification path; dangling
playlist IDs truncate Auto pages; local-only still network-fetches radio.

## Decision

1. **Voice fallback** — `searchAndNavidromeExpand` builds a single-track
   stream MediaItem when album expand fails; coerce numeric song ids via
   `songIdOf`.
2. **Dual adopt** — `PlaybackManager.adoptPlayerTimelineAsContext` rebuilds
   Dual CONTEXT from an external timeline (Auto / voice) without re-seating
   the player; called from `playSearchItems` and MediaService when player
   timeline diverges from Dual after Auto seat. `maybeAdoptExternalTimeline`
   no-ops while `shouldAdoptExternalTimeline()` is false (≈1s after local
   `syncDualQueueToPlayer`) so PLAYLIST_CHANGED mid-sync cannot wipe
   PRIORITY/LATE. Explicit voice `adoptPlayerTimelineAsContext` bypasses the gate.
3. **Radio reactions** — Gate `handleReactionCustomAction`; clear custom
   layout immediately on radio transition; omit reaction buttons in
   `onConnect` when current item is radio.
4. **Browse paging** — Over-fetch playlist/mix pages until `pageSize`
   playable items or true EOF.
5. **Local-only radio** — Skip network when `LocalOnlyPolicy.isLocalOnly`;
   bookmarks only.
6. **Artist playable** — `playable = (albumCount ?: 0) > 0`.
7. **Search cache** — LRU (~50) + TTL (~60s); clear on `destroy()`.

## Consequences

- Car-started queues survive BT resume / process death via Dual persistence.
- Voice play-from-search no longer silently no-ops on singles / missing album cache.
- Radio like/dislike cannot mutate Room with fake track ids from the shade.
