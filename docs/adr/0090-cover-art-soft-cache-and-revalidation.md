# 0090 — Cover Art Soft Cache and Revalidation

Status: Accepted
Date: 2026-10-07

## Context

Navidrome `getCoverArt` returns a real JPEG/PNG placeholder (grey star /
blue record) when artwork is unavailable. The client treated any decodable
image as final (`isUsableImage` → early return forever) and preferred
`file://` over remote URLs, so placeholders stuck until manual wipe or LRU.

Metadata sync already detects `coverArt` id changes on albums but never
invalidated the cover disk cache. Settings exposed cover quota and
`clearCoverArtCache()` in the ViewModel, but no Clear Cover Art UI, and
clear did not touch Coil's `coil_cover_cache`.

## Decision

1. **Sidecar meta** per cover file (`*.jpg.meta`): `contentSha256`, `etag`,
   `lastModified`, `fetchedAtMs`, `softPlaceholder`.
2. **Classify soft vs real:** known placeholder SHA set + shared-content
   heuristic (same SHA across ≥3 distinct `navidrome|*` keys → soft).
3. **TTL:** soft = 24h, real = 7d. Expired → conditional GET
   (`If-None-Match` / `If-Modified-Since`). 304 touches `fetchedAt`; 200
   replaces + reclassifies. Offline serves disk, skips network.
4. **Sync invalidate:** on album/artist `coverArt` id change, and when an
   album enters `changedAlbumIds`, call `invalidateNavidromeArt`.
5. **Settings Clear Cover Art:** wipe `covers/` + Coil memory/disk; leave
   audio SimpleCache alone. Scoped per-key versions (ADR-0036) still apply;
   full clear bumps global `cacheVersionState`.

## Consequences

- Placeholders can upgrade when Navidrome later resolves real art.
- Network load stays bounded (TTL + conditional GET).
- Users can reset sticky art without clearing audio.
- See `docs/COVER_ART_CACHE_BEHAVIOR_REPORT.md`.
