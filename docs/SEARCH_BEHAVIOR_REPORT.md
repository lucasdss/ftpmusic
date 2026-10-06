# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — Phase-5 tags + Discover.

## Surfaces

| Surface | Trigger | Corpus |
|---------|---------|--------|
| Global Search tab | Typeahead 300ms (≥2 chars) + IME Search | FTS ∪ search3 ∪ Discover |
| Decade / Mood / Tag chips | Idle → immediate search | Year / tag tokens / Last.fm tags |
| Filter chips | Horizontal scroll ≥48dp | Type filter |
| Discover section | After local paint, online | MusicBrainz + Last.fm |
| Library / pickers | Debounce | LIKE escaped |

## Data flow

```
onQueryChanged → keep prior hits + isLoading
  → debounce → search()
  → LocalSearch (FTS hydrate-by-id)
  → paint topHit + sections + matchedTags
  → search3 union
  → launchDiscover (MB artists/recordings + Last.fm artist.search or tag.getTopArtists)
  → scheduleRebuild(3s) on cache / soft-stub
```

## Phase-5 shipped

- Mood chips (curated token map) + Tag chips from Room `search_tags`
- Matched tag strip on results; Last.fm empty tip
- Discover lane: MusicBrainz + Last.fm; in-library badge; soft-cache artist stub on tap
- Offline / local-only: Discover hidden; Room tags still work

## Local-first

Local paint always first. Live Discover secondary. Discogs/Spotify Web API still OOS.
