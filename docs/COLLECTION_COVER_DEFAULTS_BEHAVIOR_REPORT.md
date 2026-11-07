# Collection cover defaults — behavior report

Date: 2026-11-05  
ADR: 0151

## Behavior

1. **Resolution order** (mix/playlist): fixed navidrome → fixed local library file → derived track/server art → bundled style-pack asset → lettermark.
2. **Genres:** fixed → bundled → lettermark (`skipDerived`).
3. **Style pack:** Settings → Appearance → Collection cover style (**5 packs**: anime, futuristic, vintage, watercolor, noir). Persisted as `KEY_COVER_STYLE_PACK` (archived pack ids fall back to anime).
4. **Device crop:** writes into `user_cover_images` / `collection_covers/library/{id}.jpg`. Switch/Clear on a collection only clears the pointer.
5. **Assets:** `assets/collection_defaults/{packId}/{genres|mixes|playlists}/*.webp` + `packs.json` + `catalog.json`. Generated locally with mflux (Flux.1 schnell); prompt matrix in `scripts/collection_defaults/prompts.json` (see `COLLECTION_DEFAULT_COVERS.md`).

## Validation

- `./gradlew :app:assembleDebug`
- Unit: `CollectionCoverResolverTest`, `DailyMixRepositoryTest` clear-cover, `AppDatabaseMigrationsTest` v70
