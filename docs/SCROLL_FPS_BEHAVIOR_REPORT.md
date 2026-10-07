# SCROLL_FPS — Behavior Report

Status: Act / Evaluate  
Date: 2026-10-07  
Related: ADR-0096, ADR-0036, ADR-0037, ADR-0027

## Symptom

Continuous low FPS on **every** scrollable surface (music off). Occasional brief hitch near list end. Uneven song-title font sizes from FittingText shrink.

## Root causes (ranked)

1. **Coil** — global `crossfade(true)` + many unsized `AsyncImage` in Lazy cells → decode/anim cost during fling.
2. **Main-thread disk probes** — `CoverArtFiles.looksLikeImage` (InputStream magic) inside composition `remember`.
3. **FittingText** on song titles — BoxWithConstraints + binary-search measure; per-row different font size (UX break).
4. **Global art version** on Search/Genre — any cache write recomposes whole Lazy tree.
5. **Near-end loadMore** — Main `StateFlow` append → brief hitch (secondary).

## Song list UX (required)

All song-listing pages use shared `SongListRow`:

- Title: fixed `textHeadingS`, max **2 lines**, then ellipsis (no shrink).
- Meta row below title: cache/download · like · dislike · time.
- Options (⋮) stay on the **right**.

Surfaces: Album, Playlist, Mix, Artist tracks, Favorites tracks, Search track hits, Downloads, Home track rows.

## Edge cases

- Long title → wrap line 2 → ellipsis.
- Missing duration / cache status `none` → omit from meta.
- Like/dislike optional (Favorites single-action rows).
- Overlapping loadMore → single-flight / loading flag.
- Corrupt cover file → Coil onError / eviction (not composition magic read).

## Verification

- `./gradlew :app:assembleDebug`
- Targeted unit/compose tests (SongListRow, cover resolver, loadMore)
- JaCoCo ≥80% on changed units
- Manual fling: uniform title size; smoother FPS; hitch reduced at list end
