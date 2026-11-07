# ADR 0157: Cover-reactive detail backdrops

## Status

Accepted — 2026-11-07

## Context

Album, Artist, Daily Mix, and Playlist detail screens used static dark fills or fixed
scrims. The mini / now-playing player already tinted from cover art via Palette, but
extraction used raw `URL.openConnection()`, cached failures forever, and treated
transparent vibrant fallbacks as success. Market apps (Spotify, YouTube Music, Apple
Music, Symfonium) darken a seed from artwork and fade into the near-black base while
keeping body text white / high-contrast secondary.

## Decision

1. **Spotify-style gradient only** — darkened seed → mid → `Background`; always-dark UI;
   no Material You / wallpaper dynamic color; no light-text flip.
2. **Shared `ResolvedCover`** — same identity Coil renders and palette extracts so the
   tint cannot drift from the visible cover (including lettermark / bundled / montage).
3. **Coil-backed `ArtSeedExtractor`** — ~112px software bitmap, swatch scoring
   (population × chroma × usable Y), short failure TTL, never pin offline failures in
   Room.
4. **`art_palette_cache` Room table** — scoped by `server_scope` + `art_key` +
   `source_version` + `algorithm_version` for process-death persistence.
5. **`CoverScheme` + `ArtBackdrop`** — screen-anchored drawBehind gradient; hero scrims
   retained for overlay titles; secondary text uses scheme roles (`#AAAAAA`-class).
6. **Montage** — detail Mix averages displayed tile seeds; Home LazyRow does not extract
   on the fling path.
7. **Player** — reuses the extractor pipeline; keeps a separate seed from collection art.

## Consequences

- DB version 71 migration adds `art_palette_cache`.
- Detail screens gain a short crossfade (respects animator duration scale).
- Scroll-perf ADRs still forbid live extraction on Home cards.
- Global `ColorScheme` and brand Play/Shuffle gradients stay unchanged.
- `source_version` must never store credential-bearing Subsonic URLs (art id / content stamp only).
- Cancellation must not poison the failure TTL cache; montage caches only when all tiles succeed.
