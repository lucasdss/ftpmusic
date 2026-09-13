# ADR 0035 — Composite Mix Filters and Searchable Artists

Date: 2026-09-10
Status: Accepted
Related: ADR 0034 (custom daily mix architecture)

## Context

ADR 0034 shipped one source kind per Custom Daily Mix (genres OR decades OR
favorite artists), capped at 5 genres / 4 decades, with artists limited to
the dynamic favorites list. The design mock enforced mutually exclusive
source tabs (`ftpmusic-design/src/app/App.tsx:3479-3483`).

Product feedback rejected those constraints:
- Users expect combined filters to narrow (Rock **and** 90s), not widen.
- The 5/4 caps were arbitrary; a large library has many genres.
- Artists should be searchable, not favorites-only.

## Decision

1. **Composite filters, AND across dimensions.** `MixFilters(genres,
   decades, artistIds, includeFavoriteArtists)`. Non-empty dimensions are
   intersected; picks inside a dimension are unioned; an empty dimension
   is a wildcard. Legacy single-kind mixes remain valid (one dimension).
2. **No selection caps.** The genre picker pages 20 at a time with
   "Load more" (selected pinned first); artist search pages 50 at a time.
   Decades are only 7 labels.
3. **Searchable artists.** `artists_json` stores explicit artist ids
   (newline-joined); `searchArtistsPaged` queries the local `cached_artists`
   library; names resolve via `getArtistsByIds`. The dynamic
   "All favorite artists" behavior is kept as a per-mix toggle and is
   backfilled for legacy `favoriteArtists` mixes.
4. **Tabs stay, but cumulative.** Switching tabs no longer clears the
   other dimensions; tab labels show counts. This is a deliberate
   deviation from the design mock, documented here and in the behavior
   report.
5. **Schema v48.** `ALTER TABLE custom_mixes ADD COLUMN artists_json`,
   `ADD COLUMN include_favorite_artists` + backfill.
6. **SQLite bind guard.** Uncapped selections can produce id lists beyond
   the 999-variable limit on older Android; every `IN (:ids)` in the mix
   path is chunked at 900.
7. **Sync rate limit 200 ms.** `getSongsByGenre` spacing reduced from
   500 ms to 200 ms (`GENRE_FETCH_DELAY_MS`); sync stays linear in the
   number of distinct selected genres (accepted cost).

## Alternatives

- **User-selectable All/Any match mode.** Rejected: extra column, UI and
  tests for a mode the product did not ask for; AND across dimensions is
  the intuitive reading.
- **Keep mutually exclusive tabs (design as-is).** Rejected by product.
- **Snapshot legacy favorite artists into explicit ids at migration.**
  Rejected: loses dynamic behavior; the toggle preserves it.
- **Skip or rotate genre song fetches to bound sync.** Rejected: product
  chose always-fresh fetches; the delay is the only lever.
- **Cap genre selection at a higher number (10/30).** Rejected: still
  arbitrary; pagination solves the UI concern.

## Addendum (review fixes)

- Explicit-artist-only recipes must not inherit the dynamic favorites pool.
  `derivedKind` emits `artists` for them, and the legacy
  `sourceKind == favoriteArtists` fallback in `toDomain`/`resolvePool` is
  guarded by "no explicit ids" (migration already backfills real legacy
  rows). Favorites are fetched only when a recipe actually uses them.
- Empty-pool generation deletes the stale today/yesterday `daily_mix` rows
  instead of leaving a tracklist that no longer matches the filters.
- Picker "Load more" accounting counts unselected genres; missing selected
  genres render as removable chips; artist search clears on query change.

## Consequences

- `resolvePool` intersects id sets instead of switching on one kind;
  per-dimension caches avoid duplicate queries across mixes.
- `source_kind` becomes a derived display value (`mixed` for composite
  recipes); `allMixGenreNames`/`missingSourceGenres` read `genres_json`
  regardless of kind.
- Uncapped selections increase sync duration proportionally to distinct
  selected genres (200 ms each) — accepted.
- Old recipes keep working: v47 rows map to single-dimension filters;
  favoriteArtists rows get the dynamic toggle.
- Behavior report and `CONTEXT.md` glossary updated; design mock deviation
  recorded here.
