# ADR 0034 — Custom Daily Mix Architecture

Date: 2026-09-10
Status: Accepted
Related: ADR 0017 (lazy generation), ADR 0035 (composite filters)

## Context

Daily Mixes were derived from a single global genre selection
(`daily_mix_settings`, up to 20 genres, default = top 20 by song count).
Every selected genre produced one mix, `daily_mix` was keyed by
`(date, genre)`, and generation logic was duplicated in three places
(Home lazy generation, sync screen, detail refresh). There was no way to
name a mix, combine sources, cap the list deliberately, or auto-cache a
mix's tracks.

The design (ftpmusic-design `App.tsx`, "Custom Daily Mixes") replaces the
picker with user-managed recipes: name + source (genres ≤5, decades ≤4, or
favorite artists), max 20, per-mix Auto-Cache, seed-once defaults from the
top-20 genres.

## Decision

1. **Recipes in `custom_mixes`.** Source payloads are newline-joined name
   lists (reusing the legacy encoding) plus a `source_kind` discriminator.
   `daily_mix` is rekeyed by `mix_id`; generated tracklists stay transient
   and per-day.
2. **Seed once, then user-owned.** `custom_mix_state.seeded_at` marks that
   the top-20 genres were materialized as default mixes. Sync never
   mutates existing mixes and never re-seeds. Migration maps the legacy
   selection to mixes so user picks survive.
3. **One generation service.** `DailyMixRepository` owns recipe CRUD,
   pool resolution, weight-map batching, regeneration policy and
   persistence. `DailyMixGenerator` stays a pure selection algorithm.
4. **Ownership-based auto-cache.** `custom_mix_cache_tracks` records which
   mix asked for which track. Eviction removes only tracks that no other
   auto-cache mix owns and that are not pinned downloads; enqueue uses
   DownloadManager priority 2 (constrained + LRU-evictable), never the
   priority-1 download lane.
5. **Disliked tracks never enter a mix.** Pools are filtered at the DAO
   level (`getMixTracksByGenre`, `getMixTracksByYearRange`,
   `getMixTracksByArtistIds/Names`).
6. **Persist the Wi-Fi-only preference.** `DownloadManager.allowMobileData`
   was in-memory only; it is now stored in `SecureStorage` and restored at
   process start so auto-cache respects it.

## Alternatives

- **Keep the settings table and add a names table.** Rejected: two sources
  of truth, and the global selection cannot express per-mix sources.
- **Keep genre as the daily_mix key and store mix name separately.**
  Rejected: names are not unique and sources are not genres.
- **Track cache ownership implicitly via the generated tracklist.**
  Rejected: regeneration churns tracklists daily, so delete/edit eviction
  could not distinguish exclusive ownership.
- **Re-seed defaults on every sync (v42 behavior).** Rejected by product
  decision: mixes become user-owned after first seed.
- **Priority 1 for auto-cache.** Rejected: priority 1 pins tracks as
  permanent downloads (`isDownload = true`), which would defeat quota
  eviction and the "downloads are sacred" distinction.

## Consequences

- Generated tracklists are dropped by the migration and rebuilt lazily on
  the next Home open (acceptable: they are day-scoped).
- `DailyMixGenreSelection` and the `daily_mix_settings` table are removed.
- Mix detail is keyed by id; the playback `sourceType` string remains
  `"genremix"` for queue-restore compatibility.
- Auto-cache ownership records **every desired track**, not only the ones
  the mix downloaded — otherwise a shared track could be evicted when the
  mix that happened to cache it is deleted while another auto-cache mix
  still wants it. Ownership rows self-heal (stale rows pruned when a mix is
  touched); quota pressure is still owned by the SimpleCache LRU.
- Seeding and add are atomic at the DAO layer (`seedOnce`,
  `insertIfUnderCap`), and all generation entry points are serialized by a
  repository `Mutex`.
- Home lazy generation triggers on a partial card set (`cards.size <
  mixes.size`), preserving the pre-v47 self-heal for recipes whose pool was
  empty at the last run.
- Home cards for zero-track mixes are hidden; Settings keeps them visible
  with a missing-source warning so the user can repair the recipe.
