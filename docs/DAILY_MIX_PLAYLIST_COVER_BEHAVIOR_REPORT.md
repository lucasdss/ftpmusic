# Daily Mix & Playlist Cover Behavior Report

Date: 2026-10-10  
Related: ADR 0115

## Verdict

Collection art (Daily Mix + playlist) always resolves: **fixed → derived track/album → CoverArtFallbackService → lettermark**. Never blank MusicNote as sole chrome.

## Resolution order

1. **Fixed navidrome** — `fixed_cover_kind=navidrome`, value = Subsonic coverArt id (library album pick).
2. **Fixed local** — `fixed_cover_kind=local`, value = relative path under `filesDir/collection_covers/`. Missing/corrupt file → fall through.
3. **Derived** — distinct cover ids from tracks (track art, else album / `cached_albums`), plus playlist server `coverArt`.
4. **Preferred / fallback** — UI calls `rememberPreferredCoverArt` (Navidrome URL → iTunes/MB cache).
5. **Lettermark** — name hash gradient + initials.

## Surfaces

| Surface | Behavior |
|---|---|
| Home Daily Mix card | Single `CollectionCoverArt` (fling-cheap) |
| Home playlist card | Fixed / montage primary / server / lettermark |
| Library playlists tab | Same resolver, 52dp tile |
| Mix detail | Fixed or lettermark; else 1 / 2 / 4-tile montage |
| Playlist detail | Always header + Library / Device / Clear |
| Custom Daily Mixes editor | Preview + Library / Device / Clear; persist on save |

## Edge cases

- Sync updates server `coverArt` only — never clears fixed columns.
- Temp playlist id → server id remap copies fixed cover.
- Delete mix/playlist deletes local cover files.
- Gallery import = atomic temp+rename.
- Empty tracklist / null art → lettermark (still “proper” tile).
- Subsonic has no playlist cover upload; Navidrome native image API deferred (auth mismatch).

## Perf

Home stays single Coil decode. Fixed path skips multi-cover fan-out. Album picker page ≤60.
