# ADR 0094 — API HIGH + Hygiene (Critical Path Phase D)

Date: 2026-10-07
Status: Accepted
Related: ADR-0050 (lyrics), ADR-0053 (CP), NAVIDROME-API-CAPABILITIES

## Context

Balanced critical path Phase D: lyrics song-id correctness, similar-songs for CP,
dead harness prune, overwrite triad prune, NP error honesty.

## Decision

1. **Lyrics** — `getLyricsBySongId` first; artist+title fallback.
2. **CP similar** — `getSimilarSongs2` primary when online; journal fallback.
3. **Star ratings** — already shipped (`InteractiveStarRating` + `setRating`); no change.
4. **Delete `QueueScreen`** — prod SoT is PlayerBar `queue_sheet`.
5. **Overwrite PUSH** — removed from Settings UI; `fromKey("push")` → ASK.
6. **NP error** — `PlayerHolder.lastPlaybackError` → `PlaybackState.playbackError` banner.

## Consequences

- Multi-disc lyrics mismatch reduced.
- Cast/local CP can seed from server similarity.
- Less Settings clutter; PUSH code path retained but unreachable from UI.
