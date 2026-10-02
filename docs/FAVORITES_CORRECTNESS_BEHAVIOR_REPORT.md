# Favorites Correctness Behavior Report

**Date:** 2026-10-02  
**Status:** Post-`748b526` audit fixes shipped.

## Edge map (caveman)

| Trigger | Expected | Fix |
| :--- | :--- | :--- |
| Room Flow emit mid-scroll | Keep pages >0 | `refreshLoadedWindow(limit=loaded)` under `windowMutex` |
| loadMore ∥ observe | No skipped page | Same mutex serializes append + refresh |
| Unstar on Favorites tab | Stay gone until Room | `FavoritePendingStore` Neutral filter |
| Albums full, tracks short | Still hasMore albums | Per-entity `hasMoreLiked*` |
| Skip during like | Next track own reactions | `mutationTrackId` + generation |
| Same-track like→dislike overlap | Newer gen wins | `AtomicInteger` generation; old `finally` CAS no-op |
| Artist page2+ | Thumbs for new ids | `loadReactions(..., merge=true)` |
| Library fail mid other toggle | Other ids intact | Id-scoped rollback (no `_state = previous`) |
| Process death mid-pending | Room truth | In-memory pending only (market) |

## Market UX

- Infinite scroll near list end (+ Load more footer for a11y/tests).
- Loaded window preserved when likes change elsewhere.
- Cold start = Room; no durable optimistic overlay.
