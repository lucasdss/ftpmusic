# ADR 0081 — Search hydrate-by-id and single FTS rebuild

Date: 2026-10-06
Status: Accepted
Related: ADR 0080, docs/SEARCH_BEHAVIOR_REPORT.md

## Context

Phase-3 search called `getAllAlbums()` / `getAll()` on every local hydrate and
ran FTS `rebuildAll` twice per metadata sync. UX cleared results on keystroke.

## Decision

1. Hydrate FTS hits via `getAlbumsByIds`, `getPlaylistsByIds`, `getGenresByNames`,
   `getAlbumYearRows` — never full-table scan on search path.
2. Year-only queries use `searchAlbumsByYearRange` / `searchAlbumsByExactYear`.
3. Metadata sync: enrich then **one** `rebuildAll`.
4. `SearchIndexRebuilder.ftsCount()` caches last COUNT until rebuild.
5. `scheduleRebuild` default 3s after search3 cache.
6. Search UI keeps prior results while typing; shows loading + Top result.

## Consequences

- Large libraries avoid O(N album) per keystroke search.
- Enrichment text lands in FTS one rebuild later (acceptable).
