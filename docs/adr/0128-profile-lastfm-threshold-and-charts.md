# ADR 0128 — Profile Last.fm Threshold, Tops-by-Plays, Charts

Date: 2026-10-12
Status: Accepted
Related: ADR-0046, ADR-0047

## Context

Profile minutes used player position at scrobble (seek-inflated). Eligibility was
≥60% position with an AUTO shortcut under threshold. Tops ranked by honest
seconds (unlike Spotify/Apple play-count ranking). Album/genre denorm could be
wiped by `ensureTrackRow` REPLACE. UI had no activity graphs.

## Decision

1. **Last.fm eligibility:** track >30s AND accumulated heard playtime ≥ half
   duration OR ≥4 minutes (pause-aware; wall delta × playback speed while
   playing). No AUTO under-threshold scrobble. Stored `listened_seconds` =
   accumulated content-heard.
2. **Bounded periods (Week/Month/Year):** exclude `is_backfill` from plays,
   distinct counts, tops, and recent. ALL_TIME keeps hybrid (plays/tops include
   backfill; minutes always exclude).
3. **Tops:** `ORDER BY playCount DESC, honestSeconds DESC`; UI shows plays +
   minutes and share bars.
4. **Charts:** daily/monthly honest-seconds buckets; Compose column chart +
   genre mix strip (no chart library).
5. **Denorm:** null-preserving coalesce in `ensureTrackRow`; album_name enrich
   + v63 migration repair from `tracks.album`.

## Consequences

- Short tracks ≤30s never scrobble (Last.fm).
- Seek-forward no longer inflates minutes.
- Upgrade users: Week/Month/Year no longer show backfill-only plays with 0m.
- ADR-0047 tops sort amended to play-count primary.
