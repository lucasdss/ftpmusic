# ADR 0079 — Search enrichment local-first

Date: 2026-10-06
Status: Accepted (Phase-3)
Related: ADR 0077, ADR 0080, docs/NAVIDROME-API-CAPABILITIES.md

## Context

Market apps search tags, genres, and aka names. Phase-2 stuffed similar-artist
names into `search_aliases`. Phase-3 separates real aliases vs tags.

## Decision

1. Local genre text search + Genres chip.
2. Artist LIKE matches similar JSON + aliases + tags + biography.
3. Escape LIKE via `SearchQueryNormalizer.escapeLike`.
4. Background enrichment (never keystroke):
   - `getArtistInfo2` / `getAlbumInfo2` → biography / notes
   - MusicBrainz aliases → `search_aliases`
   - Last.fm top-tags → `search_tags` (v60)
   - MBID token in FTS body
5. Discogs / Spotify Web API catalog search: out of scope.
6. **Amendment (Phase-5 / ADR 0082):** secondary live Discover via MusicBrainz
   + Last.fm after local paint is allowed. Does not replace local results.
   Discogs / Spotify Web API remain out of scope.
7. **Amendment (ADR 0085):** enrich runs in deferred WorkManager
   (`MetadataEnrichRunner` / `MetadataEnrichScheduleWorker`), enqueued after
   metadata sync populate+FTS. Sync watermarks do not await enrich. Catalog
   upserts preserve enrich columns (diff-replace / preserve-enrich).

## Consequences

- Enrichment rate-limited (Phase-7: 40 bio / 30 aliases / 30 tags / 25 albums per pass).
- Offline skips enrichment.
- Requires Last.fm API key for tags; MB aliases need network.
- Discover skipped when offline / local-only (ADR 0082).
- Discogs / Spotify / AcoustID remain out of scope (scope lock 2A).
- Sync UI completes without waiting on external enrich APIs.
