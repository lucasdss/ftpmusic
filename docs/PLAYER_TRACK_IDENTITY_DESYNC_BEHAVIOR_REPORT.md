# Player Track Identity Desync — Behavior Report

Date: 2026-10-04  
App: 1.4.2 (8)  
Related: ADR-0064, ADR-0007, ADR-0032, ADR-0027

## Symptom

- UI: KATSEYE — ANIMAL
- Audio: IVE — TAKE IT
- Sustained (not flash)
- Diagnostics: `transition id=0WRs8o53QCksznnlYiqumj reason=3 idx=8 count=115` → `saveQueueState` → `splash` → `home`

## Log decode

| Field | Meaning |
|---|---|
| `reason=3` | `MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED` (queue rebuild) |
| `idx=8 count=115` | restore/play at slot 8 of 115 |
| `splash`→`home` | process/UI restart; service restores queue |
| download enqueue other ids | `enqueuePlayQueue` ahead-cache — expected |

## Data flow

```
Track + URL (Room queue_items)
  → PlaybackManager.buildMediaItems / buildLocalUrl
  → MediaItem(mediaId=Track.id, metadata=Track, uri=URL)
  → ExoPlayer
  → PlaybackState.fromPlayer (title/artist/mediaId from currentMediaItem)
  → PlayerBar / Now Playing
```

Audio + SimpleCache key = URI query `id` (`streamCacheKey`).  
UI title/artist = `mediaMetadata` on same MediaItem.

## Root causes fixed

1. **mediaId ≠ URI `id=`** — persist zip tear → metadata ANIMAL, stream/cache TAKE IT.  
   Fix: `alignStreamUrlToTrackId` rewrite + transition mismatch breadcrumb.
2. **Non-atomic playAll** — `setMediaItems(list)` @0 → seek → play. Restore race: UI settles on idx=8 while decoder on earlier period.  
   Fix: `setMediaItems(list, startIndex, positionMs)`; restore passes `positionMs` once.

## Edge cases

- Process death mid-save → last good Room snapshot
- User play during DB restore → keep `playWhenReady`
- URL without `id=` (radio/local) → no rewrite
- Dupe `track.id` + `indexOfFirst` → separate; same audio usually
- Cast path already atomic `setMediaItems` — secondary for this log (`casting=false`)

## Perf

One timeline set vs set+seek → fewer transitions. No new Compose collectors. Metadata stays on `stateWithoutPosition` (ADR-0027).

## Coverage gate

Unit tests: `StreamUrlIdentityTest`, `QueueManagerTest` playAll atomic, `PlaybackManagerCoverageTest` rewrite fixture, `RestoreQueueTest` start index/pos. JaCoCo ≥80% on touched classes.
