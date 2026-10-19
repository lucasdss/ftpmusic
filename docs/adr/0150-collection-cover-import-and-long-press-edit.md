# ADR 0150 — Collection Cover Import Resilience + Long-Press Edit

Date: 2026-10-19  
Status: Accepted  
Related: ADR 0115, ADR 0125

## Context

ADR 0125 added square crop + JPEG caps for Device covers. Two problems remained:

1. **8 MiB hard reject** on URI bytes blocked common phone photos (especially HEIC) before crop UI.
2. JPEG encode stopped at edge **768** then rejected if still >512 KiB.
3. Edit affordances were **buttons only** on Playlist detail + Mix editor; Mix detail / Home / Library had no cover edit. Users expect long-press (album-card pattern).

## Decision

1. **Decode:** `decodeForCrop` / `importFromUri` use `ImageDecoder` (API 28+, HEIC + EXIF) with target max edge; URI fallback is two-pass `BitmapFactory` (no full-file byte[]). InputStream test seam `decodeStreamOriented` caps at `MAX_IMPORT_BYTES` (8 MiB).
2. **Encode:** Progressive edges `768 → 512 → 384 → 256` with quality ladder 85/75/65; reject only at floor (no redundant final q65 pass).
3. **Crop UX:** Keep cover-fill `minScale`; Use works at default framing; copy “Drag to frame · pinch optional”. Sheet / crop URI / edit targets use `rememberSaveable` so rotation survives.
4. **Long-press edit:** Shared `CollectionCoverEditSheet` + `rememberCollectionCoverEditController` on six surfaces — **playlists + Daily Mixes only** (album entities not editable; Home album long-press stays `AlbumCardActionSheet`). Buttons kept on detail/editor for discoverability; click+long-press both open sheet on detail (intentional).
5. **Library row:** Thumb long-press → cover sheet; text row long-press → Remove Locally.
6. **Alpha flatten:** translucent pixels matted on opaque black before JPEG.

## Consequences

- Behavior report + ADR 0125 clauses on 8 MiB reject / 768 stop superseded for Device ImageDecoder path; fallback retains 8 MiB ceiling.
- Unit-test seam `decodeStreamOriented` keeps coverage achievable under Robolectric.
- HEIC: ImageDecoder primary; manual verify if CI lacks HEIC fixtures.

## Amendment (2026-10-19 — post-1.16.1 / 2.0.0)

`openSheet` is a no-op while `pendingCropUri != null` or the album picker is
open (`canAutoOpenCoverEditSheet`). Rotation must not stack the edit sheet on
top of a restored crop dialog.

