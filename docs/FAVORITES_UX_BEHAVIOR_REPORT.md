# Favorites UX Behavior Report

**Date:** 2026-10-02  
**Status:** Thumbs = Navidrome star/unstar; 5★ = userRating; radio = local bookmark; local-first then sync.

## Semantics

| Control | Meaning | Local (first) | Server (second) |
| :--- | :--- | :--- | :--- |
| Thumb up | Like | `starred_at` | `star` |
| Thumb down | Dislike | `is_disliked` + clear star | `unstar` (no dislike API) |
| 5★ | Rating 0–5 | `user_rating` | `setRating` |
| Bookmark | Radio favorite | `radio_favorites` | **None** |

Invariant: Room write succeeds → then best-effort mirror. Offline / API fail → keep local. Local 0-row → **skip** API (no ghost star).

## Surfaces

| Surface | Thumbs | 5★ | Notes |
| :--- | :--- | :--- | :--- |
| Library Albums grid | below art, above name | **none** | navigate on tile; thumbs consume click |
| Library Artists | entity | — | list rows |
| Album detail | hero TopEnd | under hero art | track rows: thumbs only |
| Artist detail | artist | — | track rows: thumbs only |
| Mix / Playlist / Queue lists | mix/playlist: thumbs where present | **none** | |
| PlayerBar expanded | track | track rating | only track-list ★ surface |
| Favorites empty Liked | ThumbUp icon | — | was heart |
| Radio | Bookmark only | — | local-only |
| Daily Mix artist badge | ThumbUp | — | was Star |

## Market

| App | Primary | Negative | Rating |
| :--- | :--- | :--- | :--- |
| Spotify | +/✓ save | weak | rare |
| YT Music | thumbs | thumbs down | none |
| Apple Music | ★ favorite | buried | — |
| FTP Music | thumbs (= star) | thumbs (= local dislike) | 5★ (= userRating) |

## Interactive icon sizes (ADR 0059)

| Control | Glyph | Hit |
| :--- | :--- | :--- |
| FavoriteThumbButton / Player ReactionCircle | `reactionGlyphSize` 20 | `reactionHitSize` 40 |
| Dense-row thumbs / Favorites trailing | `reactionGlyphSize` 20 | `minimumInteractiveComponentSize` |
| Interactive 0–5★ (player / album under art) | `ratingStarInteractiveSize` 20 | min touch |
| Radio bookmark | `reactionGlyphSize` 20 | min touch |

Decorative headers / empty-state / display-only stars unchanged.

## Known limits

- Radio cannot sync to Navidrome (no radio star API).
- Favorites lists are **paged** (`FavoritesPaging.PAGE_SIZE = 50`) with **preserve-window** refresh + infinite scroll; Home shows page 1 only — see `docs/FAVORITES_PAGING_BEHAVIOR_REPORT.md` / `docs/FAVORITES_CORRECTNESS_BEHAVIOR_REPORT.md`.
- Track ★ rate only from expanded PlayerBar; album ★ from Album detail under art.
- See also `docs/RATING_SURFACE_BEHAVIOR_REPORT.md`.
