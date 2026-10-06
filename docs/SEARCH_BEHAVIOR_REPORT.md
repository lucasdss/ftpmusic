# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — Phase-3 complete.

## Surfaces

| Surface | Trigger | Corpus |
|---------|---------|--------|
| Global Search tab | Typeahead 300ms (≥2 chars) + IME Search | FTS (or LIKE / soft typo) ∪ search3 |
| Decade chips | Idle Search | Query `60s`…`2020s` → year filter |
| Library Artists/Albums filter | Debounce 300ms | `cached_*` LIKE (escaped) |
| Add-songs / playlist picker | Debounce | `tracks` LIKE escaped |
| Voice / MediaSession | Immediate | LocalSearch first → search3 |

## Data flow (Global Search)

```
onQueryChanged → debounce → search()
  → SearchYearParser (year/decade tokens)
  → LocalSearchRepository
       FTS MATCH → hydrate → year filter
       else soft typo (edit≤1) if len≥4
       else LIKE (+ year-only album drain)
  → local-only? stop
  else search3 → id-union + rank → cacheServerResults → scheduleRebuild FTS
```

## Phase-3 shipped

- Debounced FTS rebuild after search cache + lyrics toggle; indexing empty state
- Corpus: genre warm top-60; album-track drain cap 200/delta sync
- Real MB aliases → `search_aliases`; Last.fm tags → `search_tags`; MBID in FTS
- Year/decade parse + decade chips; track year in FTS body via album join
- Soft edit-distance fallback when FTS miss

## Fields

Track: title, artist, album, genre, path, year/decade, MBID (+ lyrics if enabled)
Album: name, artist, genre, year, notes, decade tokens
Artist: name, similar JSON, biography, **aliases**, **tags**, MBID
Playlist: name, comment
Genre: name

## Local-first

Query never blocks on enrichment APIs. Rebuild FTS offline-capable from Room.
Discogs / Spotify catalog search: out of scope.
