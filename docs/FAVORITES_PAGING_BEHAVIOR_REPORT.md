# Favorites Paging Behavior Report

**Date:** 2026-10-02  
**Status:** Preserve loaded window + per-entity hasMore + infinite scroll.

## Rules

| Surface | Behavior |
| :--- | :--- |
| Favorites tab | Room page-0 Flow **invalidates** → refresh `limit=loadedCount` (not wipe to page 0). Append via load-more / infinite scroll. |
| hasMore | Per entity: `hasMoreLikedTracks/Albums/Artists` (+ disliked). Aggregate `hasMoreLiked` = any true. |
| Home fav rows | First page only — preview. |
| Thumb id sets | Uncapped `*IdsFlow`. |
| Radio | Uncapped. |
| Sync / mix pools | Uncapped. |

Constant: `FavoritesPaging.PAGE_SIZE = 50`.

Pending overlay: `FavoritePendingStore` on Favorites tab + Library/Artist/Album. Process death → Room truth.

See also: `docs/FAVORITES_CORRECTNESS_BEHAVIOR_REPORT.md`.
