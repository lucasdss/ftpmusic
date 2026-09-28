# ADR 0037 — Daily Mix Parent Dislike and Cross-Mix Fill Mode

Date: 2026-09-28
Status: Accepted
Related: ADR 0034 (custom daily mix architecture), ADR 0035 (composite filters)

## Context

Daily Mix pools already excluded `tracks.is_disliked = 0`. Album and artist
thumbs-down live on separate ledger tables (`albums.is_disliked`,
`artists.is_disliked`) but never influenced mix generation. Users who disliked
an artist/album still heard those tracks in mixes.

Separately, Phase 3 supplemental fill used the union of **all other mixes'
pools**. A small genre pool (e.g. MPB) could pull Rock/Pop tracks to reach the
30–120 target — genre bleed perceived as a library bug.

## Decision

1. **Parent dislike in pools.** Each generation run loads disliked album/artist
   ids once into `PoolCaches`. After id intersection and `getTracksByIds`,
   drop any track whose `album_id` or `artist_id` is disliked. Decade SQL also
   adds `a.is_disliked = 0`. No write-time cascade onto `tracks.is_disliked`
   (ledger semantics stay separate — ADR 0020).
2. **Per-mix `allow_cross_mix_fill` (default 0 / shrink).** Schema v51. When
   false, `generateForMix` passes an empty supplemental list → generator target
   shrinks to the primary pool. When true, keep legacy fill from other mix
   pools. Toggle lives next to Auto-Cache in Custom Daily Mixes editor; changing
   it forces manual regen like a filter edit.
3. **Docs.** Behavior report documents MPB leak diagnosis: build supplemental
   (fixed by default) vs server/tag genre quality (out of app scope).

## Alternatives

- **Global SecureStorage shrink/fill.** Rejected: purity needs differ per mix
  (MPB pure vs “surprise” mix); `auto_cache` already establishes per-mix pattern.
- **Cascade dislikeAlbum/Artist → tracks.is_disliked.** Rejected: blurs entity
  vs track reactions; Favorites screens treat them independently.
- **Always kill supplemental (no setting).** Rejected: some users want diversity
  fill; setting preserves opt-in.

## Addendum (responsive UI / v52)

- Pool SQL joins albums/artists so disliked parents never enter id sets
  (genre/artist/decade). `filterMixPlayableIds` pre-hydrate pass +
  `filterPlayableMixTrackIds` filter-on-read for Mix Detail/play so dislike
  applies without waiting for regen.
- Name-only tracks (`artist_id` null): excluded when display `artist` matches
  a disliked artist's name (`getDislikedArtistNames`).
- v52: indexes on `albums.is_disliked` / `artists.is_disliked`.
- Home `refreshMix` uses explicit `ioDispatcher` (parity with refresh-all).
