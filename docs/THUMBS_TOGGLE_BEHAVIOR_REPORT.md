# Thumbs Toggle Behavior Report

Caveman terse. YT Music parity. ADR-0020.

## Market

| App | 2nd tap Like | Dislike |
|-----|--------------|---------|
| YT Music | Unlike (toggle) | Undislike (toggle) |
| Apple Music | Love off | Suggest Less |
| Spotify | Unlike often multi-step | Feedback |
| **FTP Music** | Unlike (`unlike*`) | Clear (`clearDislike*`) |

Target = YT Music dual-thumb toggle.

## State machine

```
neutral --Like--> liked
liked --Like--> neutral          # toggle-off
neutral --Dislike--> disliked
disliked --Dislike--> neutral    # toggle-off
liked --Dislike--> disliked      # mutual exclusion
disliked --Like--> liked         # mutual exclusion
```

Like == server star (Navidrome). Dislike = local-only.

## Surfaces

| Surface | Like again | Dislike again |
|---------|------------|---------------|
| Player | `unlikeTrack` | `clearDislikeTrack` |
| Album / Artist / Mix / Library | toggle-off | toggle-off |
| Favorites Liked | ThumbUp → `unstar*` / `unlike*` | N/A |
| Favorites Disliked | N/A | ThumbDown → `clearDislike*` |

UI must collect reaction ids via `state` (or dedicated collected flow). Artist/Mix previously read uncollected StateFlows → stuck teal / re-like.

## Sync / pending contract

1. Local unstar = **atomic** `starred_at=NULL` + `pending_unstar_at=now`.
2. `populateAllTrackGenres` / `populateGenresFromCachedGenreSongs` **preserve** `pending_unstar_at`.
3. Ledger prune keeps rows with `pending_unstar_at IS NOT NULL`.
4. `MetadataSyncWorker.syncStarredAndRatings` skips restar for `pending_unstar ∪ disliked`; pushes unstar back.
5. Marker clears when server confirms absent or unstar push succeeds.
6. Optimistic UI: `FavoritePendingStore` Neutral/Liked/Disliked overlays until Room matches.

## Edge

- Process death → Room truth (pending survives).
- Offline unlike OK; mirror deferred.
- Rapid like→unlike: per-id mutex + mutation gen (Playback).
- Failed older toggle must not undo newer tap (gen-aware rollback).

## Out of scope

- Server-sync dislikes.
- Spotify plus/check library UX.
- Dual-thumb on Favorites list rows (remove-current-thumb OK).
