# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — Phase-6 lyrics SERP + app-side rank (FTS4).

## Surfaces

| Surface | Trigger | Corpus |
|---------|---------|--------|
| Global Search tab | Typeahead 300ms (≥2 chars) + IME Search | FTS ∪ search3 ∪ Discover ∪ lyrics |
| Decade / Mood / Tag chips | Idle → immediate search | Year / tag tokens |
| From lyrics section | Lyric-only FTS hits | Lyrics cache snippets |
| Discover | After local paint, online | MusicBrainz + Last.fm |
| Settings Search lyrics | Toggle / default-on if cache nonempty | FTS lyrics rows |

## Data flow

```
maybeEnableSearchLyrics (unset + lyrics count>0 → KEY=true + rebuild 500ms)
onQueryChanged → keep prior + isLoading
  → LocalSearch FTS hydrate-by-id
  → split lyric-only vs title tracks
  → rank (lexical + play_count/recency tie-break)
  → pickTopHit (exact artist/album > first track)
  → search3 ∪ Discover
```

## FTS5 / BM25 (deferred)

FTS5 = newer SQLite FTS with `bm25()` relevance. Room 2.6 only `@Fts4`.
Device SQLite often lacks FTS5 → need Room 3 + BundledSQLiteDriver.
Phase-6 keeps FTS4; app-side ranking instead (ADR 0080 / 0083).

## Phase-6 shipped

- From lyrics section + TypeBadge + snippet
- Default-on when lyrics cache nonempty and preference unset
- Composite rank + smarter Top result subtitle
- Settings subtitle: rebuild ~500ms

## Local-first

No keystroke lyrics fetch. Discogs/Spotify OOS. Discover secondary.
