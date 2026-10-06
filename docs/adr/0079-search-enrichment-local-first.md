# ADR 0079 — Search enrichment local-first

Date: 2026-10-06
Status: Accepted
Related: ADR 0077, docs/NAVIDROME-API-CAPABILITIES.md

## Context

Market apps search tags, genres, and “similar artist” names. FTP Music already
stores Last.fm similar JSON and MusicBrainz ratings but did not query them.
Live Discogs/Spotify would violate local-first offline guarantees.

## Decision

1. Local genre **text** search via `GenreDao.searchGenres`; Search filter chip GENRES.
2. Artist search also matches `similar_artists_json` (Last.fm aliases already synced).
3. Escape LIKE metacharacters via `SearchQueryNormalizer.escapeLike` on all search DAO calls.
4. External APIs remain **background enrichment only** — never on keystroke.
5. Discogs / Spotify Web API catalog search: **out of scope**.

## Consequences

- Genre queries return Genres section + genre-matched songs (via track.genre).
- Similar-artist name query can surface the primary cached artist.
- `getArtistInfo2` / `getAlbumInfo2` still optional follow-ups for bio/notes FTS.
