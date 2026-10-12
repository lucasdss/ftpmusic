# ADR 0125 — Collection Cover Crop & Export Caps

Date: 2026-10-12  
Status: Accepted  
Related: ADR 0115 (collection fixed cover art)

## Context

ADR 0115 added Library / Device / Clear fixed covers. Device path copied gallery bytes with an 8 MiB hard reject and longest-edge downsample at 2048, then **passthrough** when already ≤2048. Display used `ContentScale.Crop` only — users could not pan/zoom framing. Market apps (Spotify, YouTube Music) show a **square crop** with pinch-zoom after pick. Oversized or poorly framed photos wasted storage and looked wrong on tiles.

## Decision

1. **Device pick → Compose square crop → JPEG export.** `PickVisualMedia` unchanged. After pick, `CoverCropDialog` (pan + pinch-zoom, square mask). Cancel discards URI; Use writes via `CollectionCoverStore.importCroppedBitmap`.
2. **Library album pick unchanged.** Navidrome `coverArt` ids skip crop (already framed album art).
3. **Export caps.** Cropped output always **JPEG** (alpha flattened on black). Longest edge ≤ **1024** (no upscale). Quality ladder 85→75→65, then downscale to 768 if still > **512 KiB**. Atomic tmp+rename preserved.
4. **Decode hygiene.** Crop preview / export use subsampled decode + `ExifInterface` orientation (including mirror orientations). Raw stream budget remains 8 MiB for non-crop legacy `importFromUri`; primary Device path is crop→JPEG.
5. **Error parity.** Mix + playlist toast on load/import failure (`Could not load image for crop` / `Could not import image`).
6. **Process-death UX.** `pendingCropUri` is ephemeral Compose state — kill mid-crop requires re-pick (Photo Picker grants are not persistable). Atomic tmp+rename still protects completed writes.
7. **Coil bust.** Local covers load as `file://path?t=mtime&s=length` so overwrite after re-crop refreshes.

## Alternatives

- **CanHub / uCrop Activity.** Rejected: extra dependency, theme mismatch with BrandBg, harder to keep brand hit-targets.
- **Passthrough + display-only crop metadata.** Rejected: large files remain on disk; Coil still pays decode cost.
- **Camera capture.** Deferred (Spotify has it; not required for size/framing fix).

## Consequences

- New UI: `CoverCropDialog` + `CoverCropMath` (unit-tested clamps).
- `CollectionCoverStore` gains `importCroppedBitmap` / JPEG ladder; Device editors hold `pendingCropUri`.
- Behavior report updated; ADR 0115 gallery caps superseded for Device path by this ADR.
