# ADR 0079 — Search enrichment local-first

Date: 2026-10-06
Status: Accepted
Related: ADR 0077, ADR 0080, docs/NAVIDROME-API-CAPABILITIES.md

## Context

Market apps search tags, genres, and similar-artist names. FTP Music stores
Last.fm similar JSON and now wires Subsonic info2 APIs for bio/notes.

## Decision

1. Local genre text search + Genres chip (phase-1).
2. Artist search matches `similar_artists_json`.
3. Escape LIKE via `SearchQueryNormalizer.escapeLike`.
4. Background `getArtistInfo2` / `getAlbumInfo2` during sync → Room columns → FTS rebuild.
5. Never call enrichment from `search()` keystroke path.
6. Discogs / Spotify Web API catalog search: out of scope.

## Consequences

- Enrichment rate-limited (25 artists / 15 albums per sync).
- Offline sync skips enrichment.
