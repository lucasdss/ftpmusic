# 0061 — Collection Play-From-Track (Spotify / Apple)

Status: Accepted
Date: 2026-10-03
Extends: ADR 0039 (dual-queue merge), ADR 0007 (queue SoT)

## Context

Ordered collections (album, playlist, artist tracks, Daily Mix, favorites
track lists) must start playback the same way market apps do: full CONTEXT
from the list, jump to the tapped row, keep Up Next (PRIORITY) after current.
Daily Mix and Favorites previously used `playSingleTrack` (1-item CONTEXT).
Mix Play/Shuffle bypassed `tryStartContext` overwrite Ask.

YT Music often wipes the whole session on play — **not** adopted.

## Decision

1. **Row tap** in an ordered collection → `playAlbum(fullLoadedList, startIndex)`
   with **no** `sourceType` → PRIORITY kept (ADR 0039 merge).
2. **Play / Shuffle** headers → `tryStartContext` / `tryShuffleContext` + source;
   ASK overwrite when PRIORITY nonempty; Clean clears PRIORITY.
3. **Non-collections** (search results, profile recent) → `playSingleTrack`.
4. **Queue sheet** row → `playFromIndex` only (no new CONTEXT).
5. Paginated lists (artist, favorites) → CONTEXT = currently loaded slice.

## Consequences

- Daily Mix / Favorites row tap match album/playlist/artist.
- Mix headers share overwrite UX with album.
- Continuous Play journal still attached to Play-with-source paths, not bare
  row taps (album parity).

## See also

`docs/COLLECTION_PLAY_FROM_TRACK_BEHAVIOR_REPORT.md`
