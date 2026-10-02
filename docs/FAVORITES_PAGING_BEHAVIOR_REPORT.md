# Favorites Paging Behavior Report

**Date:** 2026-10-02  
**Status:** Pagination / load-more (not a hard total cap).

## Rules

| Surface | Behavior |
| :--- | :--- |
| Favorites tab | First page via Room Flow (`PAGE_SIZE=50`, offset 0). **Load more** appends further pages via one-shot suspend queries. |
| Home fav rows | **First page only** — preview; full list lives on Favorites. |
| Thumb id sets | Uncapped `*IdsFlow` — Library/detail thumbs never miss ids beyond page 1. |
| Radio | Uncapped (`getAllFlow`). |
| Sync / mix pools | Uncapped (`getAllStarredArtists`, etc.). |

Constant: `FavoritesPaging.PAGE_SIZE = 50` (page size, not total cap).

`hasMoreLiked` / `hasMoreDisliked` = last batch size ≥ `PAGE_SIZE` for any entity type in that mode.

## Races (companion)

Optimistic thumbs use `FavoritePendingStore` merge over Room Flow so stale emissions cannot clobber in-flight toggles. Playback scopes in-flight guard to `mutationTrackId`. AlbumDetail uses per-track `Mutex` (no cancel of in-flight writes).
