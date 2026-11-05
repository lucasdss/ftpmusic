# ADR 0151 — Collection default covers + retained image library

Date: 2026-11-05  
Status: Accepted  
Related: ADR 0115, 0125, 0150

## Context

Mix/playlist fixed covers deleted the previous local JPEG on switch/clear. Genres had no cover model. There were no bundled default illustrations.

## Decision

1. **Style packs.** Offline Nano Banana (or placeholder) assets under `assets/collection_defaults/{packId}/` with shared `catalog.json` + `packs.json`. Nine packs: anime, pixel48, futuristic, vintage, watercolor, bauhaus, clay, risograph, noir. Settings `KEY_COVER_STYLE_PACK` selects the active pack.
2. **Resolver.** Fixed → derived → **bundled pack asset** → lettermark. Genres use `skipDerived` (fixed → bundled → lettermark).
3. **Personal library.** Room `user_cover_images` + files under `collection_covers/library/{id}.jpg`. Switch/clear only updates entity pointers. Explicit delete removes a library entry.
4. **Genre covers.** Room `genre_covers` keyed by genre name.
5. **Schema.** DB 69→70 additive tables. Playlist/mix `fixed_cover_*` unchanged; LOCAL values prefer `library/…` paths.

## Consequences

- ~700 WebP assets increase APK size; placeholders ship first, Nano Banana regenerations replace in place.
- Coil loads `file:///android_asset/collection_defaults/...`.
- Legacy `mix_{id}.jpg` / `pl_{id}.jpg` still resolve; new imports go to the library.
