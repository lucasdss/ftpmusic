# Collection cover defaults — behavior report

Date: 2026-11-08  
ADR: 0151 (+ 2026-11-08 amendment)

## Behavior

1. **Resolution order** (genre / mix / playlist AUTO): fixed navidrome → fixed local → fixed bundled path → (opt-in) derived collage when `fixed_cover_kind=derived` → **active style-pack asset** → lettermark. Track collage and playlist server `coverArt` are **not** used on AUTO (`skipDerived=true`).
2. **Pack asset pick:** name catalog slug → mix name without trailing ` mix` → exact catalog genre from `dominantGenre` → `{mixes|playlists|genres}/default.webp`.
3. **Style pack:** Settings → Appearance → Collection cover style (**5 packs**: anime default, futuristic, vintage, watercolor, noir). `KEY_COVER_STYLE_PACK`; unknown → anime.
4. **Edit:** Long-press/tap cover → sheet. Style packs → drill into pack → pick image (BUNDLED pin). Saved / From albums / From Device / Clear / Track collage. No inline Library|Device|Clear on Mix/Playlist detail or Custom Daily Mix editor.
5. **Device crop:** writes `user_cover_images` / `collection_covers/library/{id}.jpg`. Clear only clears the pointer.
6. **Assets:** `assets/collection_defaults/{packId}/{genres|mixes|playlists}/*.webp` + `packs.json` + `catalog.json`.

## Upgrade note

Unpinned Daily Mix / Playlist covers that previously showed track or server art now show the active pack (Anime unless the user changed Settings). Explicit pins (including Track collage) are unchanged.

## Validation

- `./gradlew :app:assembleDebug`
- Unit: `CollectionCoverResolverTest`, `DominantGenrePickerTest`, `DefaultCollectionCoverCatalogTest`, `CollectionCoverPackOrderTest`
