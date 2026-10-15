# ADR 0144 — Desired search enrichment (parity + share JSON)

Date: 2026-10-14
Status: Accepted (amended 2026-10-15)
Related: ADR 0129, ADR 0130, docs/DESIRED_WISHLIST_BEHAVIOR_REPORT.md

## Context

Favorites Desired “Add from catalog” showed lean title/source rows. Discover
had in-library + MB disambiguation but always null thumbs. Persisted
`desired_items` lacked image/description for offline share / backend processing.
Lidarr hosted metadata (`api.lidarr.audio`) considered and rejected (not a
public consumer API; fragile; provider ethics — not a Google Play ban).

Users need readable artist bios (not thumb-only) and clear origin when a
catalog hit is already in the library (e.g. apostrophe/alias name variants).

## Decision

1. Enrich provider DTOs: Deezer `picture_medium` / `cover_medium`; Last.fm
   largest `image` URL. No Lidarr API.
2. **Limited biography fanout** (amend): after artist hits paint, enrich the
   first `DISCOVER_FANOUT` (3) artists via `DiscoverArtistBioEnricher` —
   prefer `CachedArtistEntity.biography` when in library; else Last.fm
   `artist.getInfo` wiki summary (HTML stripped, capped). Unbounded per-hit
   MB wiki / Lidarr remain out of scope. MB `disambiguation` stays the
   short fallback label.
3. Shared `Discover*Hit` models + `DiscoverLibraryResolver` for Search Discover
   and Favorites catalog (inLibrary, local ids, imageUrl, disambiguation,
   biography). Library artist match: MBID → exact name → apostrophe-normalized
   name → `search_aliases` token (`ArtistNameNormalizer`).
4. Room v68: `image_url`, `description`, `extra_json` on `desired_items`;
   coalesce on merge; snapshot at add time (`description` = bio ?:
   disambiguation).
5. Share Intent body = versioned JSON `schema: "ftpmusic.desired.v2"` with full
   item fields + embedded `textSummary` (human lines).
6. UI: thumbs via shared `SearchCoverThumb`; always show origin
   (`In library · {source}` / `Not in library · {source}`); multi-line bio on
   rows; hide Add when in library (parity with Discover bookmark gate);
   artist row tap → detail (stub upsert + biography).

## Consequences

- Migration 67→68 additive; existing rows keep null enrichment until re-added.
- Share consumers that expected plain text must parse JSON or read `textSummary`.
- Deezer/Last.fm image URLs may rot; snapshot is best-effort for backend.
- Last.fm API key required for remote bios; library cache works offline after
  enrich / prior fanout.
