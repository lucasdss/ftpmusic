# Cover-reactive detail backdrops — behavior report

## Goal

Album, Artist, Daily Mix, and Playlist detail screens tint a screen-anchored vertical
gradient from the **displayed** cover / artist art / collection image, blending into
`Background` (`#12121E`) like Spotify / YouTube Music.

## Pipeline

1. **`ResolvedCover`** — shared identity from `CollectionCoverResolver` (or album/artist URL).
2. **`ArtSeedExtractor`** — Coil decode (~112px, software bitmap) → Palette swatch score → ARGB seed.
3. **`art_palette_cache` (Room v71)** — persists seed by `server_scope` + `art_key` + versions.
4. **`CoverScheme`** — darken seed to Y ≤ 0.04; roles: `bgTop` / `bgMid` / `bgBottom` / `onBg` / `onBgSecondary` / `accent`.
5. **`ArtBackdrop`** — `drawBehind` gradient stops 0% / 45% / 75%; 250ms crossfade (skipped when animator scale = 0).

## Per screen

| Screen | Seed source | Notes |
|--------|-------------|--------|
| Album | Preferred cover URL | Hero scrim kept; meta uses `onBgSecondary` |
| Artist | iTunes/disk artist art URL | TabRow transparent so tint shows |
| Daily Mix | Fixed / bundled / lettermark / montage average | Montage uses displayed tile URLs |
| Playlist | Resolved collection cover | Removed row black wash that fought tint |

Home cards: **no live extraction** (scroll-perf).

## Edge cases

| Case | Behavior |
|------|----------|
| Missing art | Neutral `Background` |
| Lettermark | Name-hash hue darkened |
| White / yellow cover | Seed darkened; hero scrim keeps white title readable |
| Grayscale / near-black | Neutral (chroma / Y reject) |
| Offline / decode fail | Short memory TTL; not written to Room |
| Process death | Room hit → first tinted frame when seed known |
| Cover edit / clear | New `sourceVersion` / artKey → re-extract |
| Reduced motion | Instant color swap |

## Player

`rememberCoverArtColors` now uses the same Coil extractor. `hasColors` ignores transparent
vibrant. Player seed stays separate from collection/artist detail seeds.

## Tests

- `CoverSchemeTest` — clamp, contrast, lettermark, `hasColors`
- `AppDatabaseMigrationsTest` — MIGRATION_70_71
- `./gradlew :app:assembleDebug` green

## Cross-model

Define (DeepSeek-style) + Challenge (Mistral-style) completed in plan phase. Phase-5 audit after ship.
