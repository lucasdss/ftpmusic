# ADR 0078 — Search singles and incomplete metadata

Date: 2026-10-06
Status: Accepted
Related: ADR 0077, docs/SEARCH_BEHAVIOR_REPORT.md

## Context

Tracks without albums (`album_id` null) and tracks missing album display names
were hard to find. `tracks` lacked an `album` column; search cache from
`search3` dropped album titles; UI showed artist-only subtitles.

## Decision

1. Add `tracks.album` (nullable) + index (DB v58); backfill from `cached_albums`.
2. Persist `Track.album` / `path` in `cacheServerResults`.
3. Populate album name in `populateAllTrackGenres` from `cached_albums.name`.
4. Search UI: null `album_id` → subtitle **"Singles"**.
5. Multi-field song search includes album + genre + path.

## Consequences

- Singles searchable once present in `tracks` (genre sync / search cache / play).
- Full catalog singles still depend on sync ingest (genre songs / album tracks).
