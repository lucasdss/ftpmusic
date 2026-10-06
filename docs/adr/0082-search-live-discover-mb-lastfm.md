# ADR 0082 — Search live Discover via MusicBrainz + Last.fm

Date: 2026-10-06
Status: Accepted
Related: ADR 0079 (amended), docs/SEARCH_BEHAVIOR_REPORT.md

## Context

Phase-3/4 local FTS + enrich left tags invisible in UI and blocked live catalog
search. Market apps show mood/tag chips and web Discover. Spotify/Discogs clients
are heavy; MusicBrainz + Last.fm already ship in-app.

## Decision

1. Idle Search: Mood chips (curated token map) + Tags chips from aggregated
   `cached_artists.search_tags`. Tap → immediate local search.
2. After local paint, if online and not local-only: cancelable Discover job
   calls MusicBrainz `searchArtists` / `searchRecordings` and Last.fm
   `artist.search` (or `tag.getTopArtists` when query came from a tag chip).
3. Match Discover hits to Room by MBID or exact name → In library badge.
4. Tap external artist → soft-upsert stub (`mb:{mbid}` or hash id) +
   `scheduleRebuild`. Never invent playable tracks.
5. Discogs / Spotify Web API remain out of scope.

## Consequences

- MB 1 req/s mutex still applies; Discover may lag slightly behind local.
- Without Last.fm key: tags tip shown; Discover still uses MusicBrainz.
- Soft stubs improve future FTS but do not enable playback.
