# Favorites Correctness Behavior Report

**Date:** 2026-10-02  
**Status:** Residual correctness after `0574cea` shipped.

## Edge map (caveman)

| Trigger | Expected | Fix |
| :--- | :--- | :--- |
| Room Flow emit mid-scroll | Keep pages >0 | `refreshLoadedWindow(limit=loaded)` under `windowMutex` |
| loadMore ∥ observe | No skipped page | Same mutex serializes append + refresh |
| Unstar on Favorites tab | Stay gone until Room | `FavoritePendingStore` Neutral filter |
| Albums full, tracks short | Still hasMore albums | Per-entity `hasMoreLiked*` |
| Skip during like | Next track own reactions | `mutationTrackId` + generation |
| Same-track like→dislike overlap | Newer gen wins | `AtomicInteger` generation; old `finally` CAS no-op |
| Artist page2+ | Thumbs for new ids | `loadReactions` merge-always (no replace-all) |
| Library fail mid other toggle | Other ids intact | Id-scoped rollback (no `_state = previous`) |
| Process death mid-pending | Room truth | In-memory pending only (market) |

## Residual → fixed (post-`0574cea`)

| Residual | Fix |
| :--- | :--- |
| Infinite scroll stalls when `nearEnd` stays true | `LaunchedEffect` re-keyed on loaded sizes + `isLoadingMore*` gate |
| Liked refresh `reconcile(liked, empty)` clears Neutral early | Both windows pass real liked **and** disliked id sets into `reconcile` |
| Library fail restores ids only (list/dislike lost) | Fail path restores `starredAlbums`/`starredArtists` row + `disliked*Ids` |
| Playback older fail skipped when newer gen live | Catch restores mutation baseline whenever still on same track (success still gen-gated) |
| Artist `loadReactions(merge=false)` race-wipes page2 | Merge-always into existing sets; drop replace-all |
| AlbumDetail album toggle full-state stomp | Id-scoped liked/disliked restore (mirrors Library) |

## Market UX

- Infinite scroll near list end (+ Load more footer for a11y/tests).
- Loaded window preserved when likes change elsewhere.
- Cold start = Room; no durable optimistic overlay.
