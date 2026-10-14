# ADR 0144 — Desired search enrichment (parity + share JSON)

Date: 2026-10-14
Status: Accepted
Related: ADR 0129, ADR 0130, docs/DESIRED_WISHLIST_BEHAVIOR_REPORT.md

## Context

Favorites Desired “Add from catalog” showed lean title/source rows. Discover
had in-library + MB disambiguation but always null thumbs. Persisted
`desired_items` lacked image/description for offline share / backend processing.
Lidarr hosted metadata (`api.lidarr.audio`) considered and rejected (not a
public consumer API; fragile; provider ethics — not a Google Play ban).

## Decision

1. Enrich provider DTOs: Deezer `picture_medium` / `cover_medium`; Last.fm
   largest `image` URL. No Lidarr API. No per-hit biography fanout.
2. Shared `Discover*Hit` models + `DiscoverLibraryResolver` for Search Discover
   and Favorites catalog (inLibrary, local ids, imageUrl, disambiguation).
3. Room v68: `image_url`, `description`, `extra_json` on `desired_items`;
   coalesce on merge; snapshot at add time.
4. Share Intent body = versioned JSON `schema: "ftpmusic.desired.v2"` with full
   item fields + embedded `textSummary` (human lines).
5. UI: thumbs via shared `SearchCoverThumb`; hide Add when in library (parity
   with Discover bookmark gate).

## Consequences

- Migration 67→68 additive; existing rows keep null enrichment until re-added.
- Share consumers that expected plain text must parse JSON or read `textSummary`.
- Deezer/Last.fm image URLs may rot; snapshot is best-effort for backend.
