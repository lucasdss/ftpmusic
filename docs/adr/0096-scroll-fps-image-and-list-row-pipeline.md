# ADR 0096 — Scroll FPS: Image Pipeline + SongListRow

Date: 2026-10-07  
Status: Accepted  
Related: ADR-0036 (scoped cover invalidation), ADR-0037 (library tab slices), ADR-0027 (playback flow split)

## Context

Scroll felt continuously low-FPS on every list surface with music off. FittingText
shrink-to-fit also made song titles render at different sizes per row, harming UX.
Near-end hitch from custom loadMore appends was secondary.

Room full-catalog dumps and Paging 3 remain deferred (ADR 0037).

## Decision

1. **Coil defaults** — `crossfade(false)` app-wide; list covers use sized
   `ImageRequest` via `CoverArtImage` / sized `ArtistAvatar`.
2. **Composition disk path** — composition uses cheap `existsNonEmpty` only;
   magic/decode validation stays off the composition critical path (Coil
   `onError` / existing eviction helpers).
3. **SongListRow** — shared song row: fixed-size title (`maxLines = 2`), meta
   row (cache · like · dislike · time) below title, ⋮ on the right. No
   FittingText on song list titles.
4. **Scoped invalidation** — Search/Genre observe per-key cover versions, not
   global `cacheVersionState`.
5. **loadMore** — single-flight / loading guards on Favorites, Downloads, Genre;
   stable Lazy `key` / `contentType` where missing.

## Consequences

- Uniform song title typography across lists; FittingText reserved for
  non-scroll chrome (settings/player headers).
- Fewer decode/crossfade/measure spikes during fling.
- Corrupt covers still recover via Coil error → fallback/evict.
- Library Albums full alpha dump unchanged this pass.
