# Phase D API Hygiene — Behavior Report

Caveman. ADR-0094.

## Ship

| Item | Behavior |
|------|----------|
| Lyrics songId | `getLyricsBySongId` first; parse `lyricsList` (ADR-0095); artist+title fallback |
| NP error sticky | Sticky across auto-skip; dismiss (ADR-0095) |
| CP similar | `getSimilarSongs2` primary online; journal fallback |
| Star ratings | Already on NP/album (`InteractiveStarRating`) |
| QueueScreen | Deleted — PlayerBar sheet SoT |
| Overwrite PUSH | Removed from Settings; `fromKey(push)`→ASK |
| NP error banner | `playbackError` strip; clear on READY/transition |
| Cast flatten copy | Clearer one-timeline notice |

## Coverage

`LyricsFetcherTest` songId prefer/fallback. Settings overwrite fromKey. Existing CP / PlayerSurfaces.