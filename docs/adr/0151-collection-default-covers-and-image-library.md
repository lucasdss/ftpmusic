# ADR 0151 — Collection default covers + retained image library

Date: 2026-11-05  
Status: Accepted  
Related: ADR 0115, 0125, 0150

## Context

Mix/playlist fixed covers deleted the previous local JPEG on switch/clear. Genres had no cover model. There were no bundled default illustrations.

## Decision

1. **Style packs.** Bundled WebP assets under `assets/collection_defaults/{packId}/` with shared `catalog.json` + `packs.json`. **Five packs ship in the APK:** anime (default), futuristic, vintage, watercolor, noir. Four more (`pixel48`, `bauhaus`, `clay`, `risograph`) live under `scripts/collection_defaults/archived_packs/` for later. `CoverStylePackPreference` owns `KEY_COVER_STYLE_PACK` as a `StateFlow` (unknown/archived ids → anime). Generation: LiteLLM ChatGPT or local mflux via `scripts/generate-collection-default-covers.py` + `scripts/collection_defaults/prompts.json`.
2. **Resolver.** Fixed (navidrome / local / **bundled**) → derived → active-pack default asset → lettermark. Genres **and Daily Mixes** use `skipDerived` by default (fixed → pack default → lettermark) so MIX AUTO = active style pack. Users opt into track collage via `fixed_cover_kind=derived`. `BUNDLED` fixed covers store an asset path (Defaults pin) and never copy into the personal library.
3. **Personal library.** Room `user_cover_images` + files under `collection_covers/library/{id}.jpg`. **Clear / switch / delete mix|playlist never delete `library/` files.** Only Saved → Remove (confirm + null pointers) may delete. `CollectionCoverStore.deleteRelative` refuses `library/` unless `allowLibraryDelete`.
4. **Edit sheet.** Defaults strip / Saved grid / From albums / From Device / Clear. Saved long-press removes with usage count.
5. **Genre covers.** Room `genre_covers` keyed by genre name; Home long-press edits like mixes/playlists.
6. **Schema.** DB 69→70 additive tables. Playlist/mix `fixed_cover_*` unchanged; LOCAL values prefer `library/…` paths.

## Consequences

- Five shipped packs (~385 WebPs / ~16 MB assets) keep APK growth bounded; archived packs stay in-repo outside Android assets.
- Coil loads `file:///android_asset/collection_defaults/...`.
- Legacy `mix_{id}.jpg` / `pl_{id}.jpg` still resolve; new imports go to the library.
- Entity delete cleans only legacy prefix files (`mix_*` / `pl_*`), never Saved uploads.
