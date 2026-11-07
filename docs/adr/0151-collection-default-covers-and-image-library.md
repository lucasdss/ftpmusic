# ADR 0151 — Collection default covers + retained image library

Date: 2026-11-05  
Status: Accepted  
Related: ADR 0115, 0125, 0150

## Context

Mix/playlist fixed covers deleted the previous local JPEG on switch/clear. Genres had no cover model. There were no bundled default illustrations.

## Decision

1. **Style packs.** Bundled WebP assets under `assets/collection_defaults/{packId}/` with shared `catalog.json` + `packs.json`. **Five packs ship in the APK:** anime (default), futuristic, vintage, watercolor, noir. Four more (`pixel48`, `bauhaus`, `clay`, `risograph`) live under `scripts/collection_defaults/archived_packs/` for later. Settings `KEY_COVER_STYLE_PACK` selects the active pack (unknown/archived ids fall back to anime). Generation: LiteLLM ChatGPT or local mflux via `scripts/generate-collection-default-covers.py` + `scripts/collection_defaults/prompts.json`.
2. **Resolver.** Fixed → derived → **bundled pack asset** → lettermark. Genres use `skipDerived` (fixed → bundled → lettermark).
3. **Personal library.** Room `user_cover_images` + files under `collection_covers/library/{id}.jpg`. Switch/clear only updates entity pointers. Explicit delete removes a library entry.
4. **Genre covers.** Room `genre_covers` keyed by genre name.
5. **Schema.** DB 69→70 additive tables. Playlist/mix `fixed_cover_*` unchanged; LOCAL values prefer `library/…` paths.

## Consequences

- Five shipped packs (~385 WebPs / ~16 MB assets) keep APK growth bounded; archived packs stay in-repo outside Android assets.
- Coil loads `file:///android_asset/collection_defaults/...`.
- Legacy `mix_{id}.jpg` / `pl_{id}.jpg` still resolve; new imports go to the library.
