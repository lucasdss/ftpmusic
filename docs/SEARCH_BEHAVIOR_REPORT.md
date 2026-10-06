# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — Phase-2 complete.

## Surfaces

| Surface | Trigger | Corpus |
|---------|---------|--------|
| Global Search tab | Typeahead 300ms (≥2 chars) + IME Search | FTS (or LIKE fallback) ∪ search3 |
| Library Artists/Albums filter | Debounce 300ms | `cached_*` LIKE (escaped) |
| Add-songs / playlist picker | Debounce | `tracks` LIKE escaped |
| Custom Daily Mix artist picker | Debounce | `searchArtistsPaged` escaped |
| Voice / MediaSession | Immediate | LocalSearch first → search3 |

## Data flow (Global Search)

```
onQueryChanged → debounce → search()
  → LocalSearchRepository (FTS MATCH or LIKE)
  → local-only? stop (playable filter)
  → else search3 page 50 → id-union merge + composite rank → cacheServerResults
  → loadMore: offsets += 50 + re-rank
```

## Phase-2 shipped

- Composite track rank (title/artist/album); phase-1 + load-more ranked
- Corpus warm: top-40 genres × 200 songs; always populate tracks; starred upsert
- FTS `search_fts` (Room FTS4/unicode61) + rebuild after sync/enrichment
- getArtistInfo2 / getAlbumInfo2 background → biography / notes / aliases
- Settings: Search lyrics (opt-in FTS)
- Voice: local search first

## Fields

Track: title, artist, album, genre, path (+ lyrics if enabled)
Album: name, artist, genre, year, notes
Artist: name, similar JSON, biography, aliases
Playlist: name, comment
Genre: name

## Local-first

Query never blocks on enrichment APIs. Rebuild FTS offline-capable from Room.
