# Daily Mix & Playlist Cover Behavior Report

Date: 2026-10-12  
Related: ADR 0115, ADR 0125

## Verdict

Collection art (Daily Mix + playlist) always resolves: **fixed → derived track/album → CoverArtFallbackService → lettermark**. Never blank MusicNote as sole chrome.

Device gallery covers go through **square pan/zoom crop** then **JPEG ≤1024px / ≤512 KiB** (ADR 0125).

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
| Playlist detail | Header + Library / Device / Clear; Device → crop dialog |
| Custom Daily Mixes editor | Preview + Library / Device / Clear; Device → crop; persist on save |

## Device cover flow (ADR 0125)

1. `PickVisualMedia(ImageOnly)` → `pendingCropUri`
2. `CoverCropDialog` — square viewport, pinch zoom (min=fill, max=4×), drag clamp, Cancel / Use
3. Use → `CollectionCoverStore.importCroppedBitmap` → always `.jpg`
4. Cancel → discard URI; prior fixed cover unchanged
5. Fail load/import → toast (`Could not load image for crop` / `Could not import image`)

Library album pick skips crop (Navidrome id).

## Export caps

| Cap | Value |
|---|---|
| Cropped max edge | **1024** px (no upscale) |
| Max output bytes | **512 KiB** (q85→75→65, then edge 768) |
| Format | JPEG (alpha → black) |
| Legacy raw URI read | ≤ **8 MiB** (`importFromUri`) |
| Atomic write | `*.tmp` + rename |

## Edge cases

- Sync updates server `coverArt` only — never clears fixed columns.
- Temp playlist id → server id remap copies fixed cover.
- Delete mix/playlist deletes local cover files.
- New Daily Mix gallery stage uses `mix_new_<ts>.jpg`; **save rekeys** → `mix_{id}.jpg`.
- Library **Remove locally** calls `onPlaylistDeleted` (cover wipe) — mirrors detail path.
- Mix detail: stale LOCAL (file gone) → **not** usable fixed → montage/lettermark (`isUsableFixed`).
- Library playlist rows fill `primaryArtist`/`primaryAlbum` in montage load (fallback art).
- Empty tracklist / null art → lettermark (still “proper” tile).
- Subsonic has no playlist cover upload; Navidrome native image API deferred (auth mismatch).
- EXIF orientation applied before crop math; HEIC may fail decode → toast.
- Process death mid-crop: `pendingCropUri` is Compose `remember` (not saveable) → dialog gone; user re-picks. No persistable URI grant needed; no partial LOCAL write.
- EXIF orientation applied in `decodeSampledOriented` before crop preview/export.
- Coil local model key includes `mtime+length` so overwrite refreshes tiles.

## Perf

Home stays single Coil decode. Fixed path skips multi-cover fan-out. Album picker page ≤60. Crop preview uses subsampled decode (≤2048) so 50MP gallery shots do not OOM.

## 1.10.0 fixes (post-1.9.1)

- P0 orphan LOCAL files on Library remove / new-mix id mismatch → fixed.
- P1 import cap, playlist primary meta, mix-detail usable-fixed gate → fixed.

## Post-1.10.0 (ADR 0125)

- Device square crop + JPEG export ladder.
- Playlist Device import toast parity with Custom Daily Mixes.
