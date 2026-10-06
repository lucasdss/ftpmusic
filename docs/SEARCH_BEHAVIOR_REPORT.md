# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — post WS1–WS6 improvement pass.

## Surfaces

| Surface | Trigger | Corpus |
|---------|---------|--------|
| Global Search tab | Typeahead 300ms (≥2 chars) + IME Search | Room multi-field ∪ search3 |
| Library Artists/Albums filter | Debounce 300ms | `cached_*` LIKE (escaped) |
| Add-songs picker | Debounce | `tracks` |
| Custom Daily Mix artist picker | Debounce | `searchArtistsPaged` escaped |
| Voice / MediaSession | Immediate | search3 songs |

## Data flow (Global Search)

```
onQueryChanged → debounce → search()
  → escapeLike(query)
  → local-only? playable DAOs + playlists + genres
  → else phase1 Room (tracks album/genre/path, albums, artists+similar JSON, playlists, genres)
  → search3 (page 50) → id-union merge + rank → cacheServerResults
  → loadMore: artist/album/song offsets += 50
```

## Bug fixed (P0)

`results.artists.ifEmpty { cached }` **clobbered** local artists when search3 returned any page.
Now: `SearchResultMerger.unionById` + `rankByQuery` (exact → prefix → contains, diacritic fold).

## Fields searched locally

| Entity | Fields |
|--------|--------|
| Track | title, artist, album, genre, path |
| Album | name, artist, genre, year |
| Artist | name, similar_artists_json |
| Playlist | name, comment |
| Genre | name |

Singles: `album_id` null → UI label **Singles**; `tracks.album` column (v58) backfilled from `cached_albums`.

## Market gaps remaining

| Gap | Status |
|-----|--------|
| FTS5 / typo | Deferred ADR 0080 — multi-field LIKE + rank for now |
| Live Discogs/Spotify search | Out of scope (local-first) |
| Lyrics FTS | Deferred |
| getArtistInfo2 wire | Partial — similar JSON already searchable |

## Local-first rule

Query never blocks on MB/Last.fm/iTunes. Enrichment = background → Room only.

## Tests

- `SearchResultMergerTest`, `SearchQueryNormalizerTest`
- `SearchViewModelTest` union clobber regression
- Migration 57→58
