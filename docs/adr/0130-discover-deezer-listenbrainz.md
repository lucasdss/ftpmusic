# ADR 0130 — Discover: Deezer Search + ListenBrainz enrichment

Date: 2026-10-12
Status: Accepted
Related: ADR 0079, ADR 0082, docs/DESIRED_WISHLIST_BEHAVIOR_REPORT.md

## Context

Live Discover used MusicBrainz + Last.fm only. Users need broader catalog
coverage for “not in library” Desired adds. Discogs/Spotify remain out of
scope (heavy auth / ADR lock). Deezer public search needs no key. ListenBrainz
is MetaBrainz-family but is not a general name-search API.

## Decision

1. Add `DeezerService`: `GET api.deezer.com/search/{artist|album|track}`.
   Token-bucket (~40 req / 5s). Treat HTTP 200 + `error.code == 4` as quota
   (backoff), not empty success.
2. Add `ListenBrainzService`: popularity / metadata lookup for hits that
   already have an MBID. Lazy TTL cache. Missing stats → no badge (not “0”).
3. Wire into `SearchViewModel.launchDiscover` after MB + Last.fm; merge by
   name key; Deezer fills gaps + album hits; LB may reorder when listen
   counts available.
4. Discogs / Spotify Web API still out of scope.
5. Offline / local-only: Discover unchanged (skipped).

## Consequences

- More network fan-out per query; cancel on keystroke still required.
- Deezer “in library” without MBID uses exact-name match (fuzzy risk documented).
- Desired Add-from-catalog reuses the same provider merge path.
