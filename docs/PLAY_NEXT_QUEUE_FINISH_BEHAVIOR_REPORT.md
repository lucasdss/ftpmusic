# Play Next / Queue Finish Behavior Report

Caveman. Cycle: diag 2026-10-13 Play Next skip + finished-vanishes.

## Market gold

| Behavior | Apple / Spotify / YTM | FTPMusic (this fix) |
|----------|----------------------|---------------------|
| Play Next | After current | Align anchor → PRIORITY front after playhead |
| Add to Queue | PRIORITY tail before context suffix | Same Dual index on Exo (no append-end) |
| Finished track | Leave Up Next; Recently Played | Up Next `queueIndex > current`; history exclude current+upcoming only |
| Align on AUTO | N/A (single timeline) | **No** — skips unplayed PRIORITY |

## Bugs confirmed (logs)

1. `playNext size=57` then AUTO `idx=3` into context id — not Play Next track.
2. User reorder `2IhXgBz from=9 to=8` — insert sat far before playhead.
3. Queue `count` stable on AUTO — Exo never deleted finished; history exclude hid them.

## Root cause

- `contextAnchor` stale after mid-playlist listen; `playNext` spliced PRIORITY after start.
- Local `addToQueue` appended Exo end → Dual≠Exo.
- History excluded **all** queue ids including finished prefix.

## Fix

1. `alignAnchorToPlayhead()` only before `playNext` / `addToQueue` / `addAllToQueue`.
2. Local Exo insert at Dual merge index.
3. Cast `beforeEntryId` = post-mutation neighbor.
4. Sheet Up Next past-index filter; history current+upcoming exclude.

## Edge Agent

| Edge | Status |
|------|--------|
| Play Next while on PRIORITY row | afterIndex PRIORITY path kept |
| Align every AUTO | **Rejected** — skips PRIORITY |
| Duplicate mediaId | Prefer entryId in `currentCanonicalIndex` |
| Empty addAll | Early return |

## Coverage Agent

Target: DualQueueManager + PlaybackManager mid-album playNext/addToQueue + FavoriteTest history prefix.
Gate: ≥80% touched paths via new regression tests.
