# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — Phase-4 UX/perf complete.

## Surfaces

| Surface | Trigger | Corpus |
|---------|---------|--------|
| Global Search tab | Typeahead 300ms (≥2 chars) + IME Search | FTS (or LIKE / soft typo) ∪ search3 |
| Decade chips | Idle → immediate search | Year filter |
| Filter chips | Horizontal scroll ≥48dp | Type filter |
| Library / pickers | Debounce | LIKE escaped |
| Voice | Immediate | LocalSearch first → search3 |

## Data flow

```
onQueryChanged → update query only (keep prior hits) + isLoading
  → debounce → search()
  → LocalSearch (FTS hydrate-by-id / soft typo / LIKE)
  → paint topHit + sections
  → search3 union → scheduleRebuild(3s)
```

## Phase-4 shipped

- Keep results while typing; LinearProgress + Searching…; Top result row
- Single scrollable filter chips; rich empty tips; decade chip immediate search
- `getAlbumsByIds` / year rows / playlist+genre by id — no `getAllAlbums` on search hot path
- Single FTS rebuild after sync enrich; ftsCount cache; 3s post-search rebuild debounce

## Local-first

No keystroke enrichment. Discogs/Spotify catalog out of scope.
