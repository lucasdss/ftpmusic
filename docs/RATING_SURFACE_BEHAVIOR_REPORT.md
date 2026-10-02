# Rating Surface Behavior Report

**Date:** 2026-10-02  
**Status:** Track 5★ = full player only. Album 5★ = Album detail under art. Library album grid = thumbs only.

## Surfaces

| Surface | Thumbs | 5★ |
| :--- | :--- | :--- |
| Library Albums grid | below art, above name | **none** |
| Album detail | hero TopEnd | under hero art (`rateAlbum`) |
| Album / Artist / Mix track rows | yes | **none** |
| Playlist / Queue track rows | — | **none** |
| PlayerBar expanded | track | track (`onRate`) |
| Home album cards | — | display-only (unchanged this pass) |

## Layout (Library album tile)

1. Cover art (+ EQ / download badge)  
2. Thumbs up / down  
3. Album name  
4. Artist  

## Why

Track-row 5★ stole horizontal space → title truncated. Rating stays in full player. Album rating stays on Album detail under cover, not in Library grid.

## Coverage

- Unit suite + `AlbumDetailComposeTest` (album ★ under art; no track `Rate N`) + `InteractiveStarRatingTest` green.
- JaCoCo on Compose `*ScreenKt` / `InteractiveStarRatingKt` often reports 0% (Compose IR / inlining). Non-Compose album rating path: `AlbumDetailViewModel` line **≥80%**.
