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

1. **Decode:** `decodeForCrop` / `importFromUri` use `ImageDecoder` (API 28+, HEIC + EXIF) with target max edge, fallback `decodeStreamOriented` (bounds + `inSampleSize` + EXIF). **No 8 MiB hard reject** on crop path. `MAX_IMPORT_BYTES` retained only for legacy `readUriBytesCapped`.
2. **Encode:** Progressive edges `768 → 512 → 384 → 256` with quality ladder 85/75/65; reject only at floor.
3. **Crop UX:** Keep cover-fill `minScale`; Use works at default framing; copy “Drag to frame · pinch optional”.
4. **Long-press edit:** Shared `CollectionCoverEditSheet` + `rememberCollectionCoverEditController` on all six `CollectionCoverArt` surfaces (Playlist detail, Mix detail, Mix editor, Home mix/playlist cards, Library playlist thumb). Buttons kept on detail/editor for discoverability. `CollectionCoverArt` unchanged (modifier-only long-press).
5. **Library row:** Thumb long-press → cover sheet; text row long-press → Remove Locally.

## Consequences

- Behavior report + ADR 0125 clauses on 8 MiB reject / 768 stop superseded for Device path.
- Unit-test seam `decodeStreamOriented` keeps coverage achievable under Robolectric.
- HEIC: ImageDecoder primary; manual verify if CI lacks HEIC fixtures.
