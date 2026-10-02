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
| Library Artists/Albums | entity | album stars display | cache IGNORE-insert on API list |
| Album detail hero | album | album rating (always) | track rows: thumbs + stars |
| Artist detail hero | artist | — | album tab thumbs; track thumbs+stars |
| PlayerBar | track | track rating | |
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

## Known limits

- Radio cannot sync to Navidrome (no radio star API).
- Favorites lists are **paged** (`FavoritesPaging.PAGE_SIZE = 50`) with **preserve-window** refresh + infinite scroll; Home shows page 1 only — see `docs/FAVORITES_PAGING_BEHAVIOR_REPORT.md` / `docs/FAVORITES_CORRECTNESS_BEHAVIOR_REPORT.md`.
- Search/Queue/Playlist: stars display-only; no thumbs this pass.
