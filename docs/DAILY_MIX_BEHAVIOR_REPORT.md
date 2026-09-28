# Daily Mix Behavior Report

Caveman terse. Code truth as of ADR 0037.

## Pipeline

```
custom_mixes recipe
  → prepare(): seedIfNeeded, populate genres, PoolCaches
  → resolvePool per mix (OR in dim, AND across dims)
  → drop tracks on disliked album OR artist
  → DailyMixGenerator (30–120 target, artist/album diversity)
  → optional supplemental (only if allow_cross_mix_fill)
  → replaceDailyMix(date, mix_id, tracks)
  → MixCacheCoordinator.onGenerated if auto_cache
```

Entry points: Home lazy (`cards.size < mixes.size`), sync complete, Home/detail refresh, filter/fill edit.

## Dislike rules

| Signal | Excluded from pool? | Filter-on-read? |
|--------|---------------------|-----------------|
| `tracks.is_disliked` | Yes — pool SQL | Yes |
| `albums.is_disliked` | Yes — SQL JOIN | Yes |
| `artists.is_disliked` | Yes — SQL JOIN + name-only | Yes |

Name-only tracks (`artist_id` null): excluded when `artist` display name matches
a disliked artist. Null album/artist with no name match → stay eligible.

Persisted `daily_mix` rows are **not** rewritten on dislike. Mix Detail / play
call `filterPlayableMixTrackIds` so UI drops them immediately (no regen wait).

## Shrink vs fill (`allow_cross_mix_fill`)

- **Default false (shrink):** supplemental = empty. Target `coerceAtMost(primary.size)`. Genre-pure short mix OK.
- **True (fill):** supplemental = union other mixes' pools minus primary. Legacy diversity; can bleed genres (e.g. Rock into MPB).

UI: Custom Daily Mixes editor — "Fill from other mixes" switch (`mix_editor_cross_mix_fill`). Toggle → manual regen.

## Auto-cache

After **successful** regen persist only:

1. `mix.autoCache == true`
2. `MixCacheCoordinator.onGenerated` → ownership rows + DownloadManager priority 2

Gates: Wi-Fi/mobile pref, offline mode, battery <20% not charging, URL/creds, shared LRU quota.
Seeded mixes default `auto_cache = 0`. Skip regen (`shouldRegenerate` false) → no `onGenerated`.

## Regeneration policy

- Manual → always
- Age ≥ 48h → always
- Age ≥ 24h AND ≥10% listened → regen
- Else keep existing tracklist

## MPB international leak diagnosis

1. **Build (fixed by default):** Phase 3 cross-mix fill. Small MPB pool + large other mixes → foreign tracks. Default shrink stops this.
2. **Library/tags:** `tracks.genre = 'MPB'` exact match. Wrong album genre stamp or server `getSongsByGenre("MPB")` can still put non-Brazilian tracks in the **primary** pool. App cannot fix bad tags.
3. **Not** MBP↔MPB typo cross-match (exact string only).

## Key files

- `DailyMixRepository.kt` — CRUD, pools, gen, fill gate, parent dislike
- `DailyMixGenerator.kt` — selection algorithm
- `MixCacheCoordinator.kt` — ownership + enqueue
- `Daos.kt` — `getMixTrackIds*`
- ADR 0034 / 0035 / 0037
