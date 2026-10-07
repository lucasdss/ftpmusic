# COVER ART CACHE — BEHAVIOR REPORT

Caveman map. Soft cache + Settings clear. ADR-0090.

## Flow

```
coverArtId → rememberPreferredCoverArt
  1. disk covers/ (file://) if usable
  2. Navidrome getCoverArt URL
  3. iTunes ∥ MusicBrainz fallback
Coil loads URL → memory + coil_cover_cache
NavHost / BitmapLoader → cacheNavidromeArt / publishCachedBytes → covers/
```

## Soft vs real

| Kind | Meaning | TTL | Early-return |
|------|---------|-----|--------------|
| soft | placeholder SHA or SHA shared by ≥3 navidrome ids | 24h | no if stale |
| real | unique decodable art | 7d | yes if fresh |

Sidecar: `{name}.meta` JSON — sha, etag, lastModified, fetchedAtMs, softPlaceholder.

## Revalidate

Stale or soft-expired → conditional GET. 304 → bump fetchedAt. 200 → atomic write + classify + observeVersion. Offline → keep disk.

## Sync

MetadataSyncWorker: coverArt id change → invalidate old id. Album in changedAlbumIds → invalidate current coverArt id. Artist coverArtUrl change → invalidate. Orphan clean keeps deleted ids out.

## Settings

Cover Art quota slider (unchanged). Clear cover art → covers/ wipe + Coil memory/disk clear. Audio cache untouched.

## Edge

- Empty active set → orphan clean skip (no wipe).
- Corrupt/non-image → reject write, delete unusable.
- clearCache → wipe meta + sha index + global version bump.
