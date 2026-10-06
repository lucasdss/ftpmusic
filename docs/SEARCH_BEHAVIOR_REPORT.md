# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — Phase-7 FTS5 + BM25 (Room 2.7.2 SupportSQLite). Scope D review.

## Surfaces

| Surface | Trigger | Corpus |
|---------|---------|--------|
| Global Search tab | Typeahead 300ms (≥2 chars) + IME Search | FTS5 ∪ search3 ∪ Discover ∪ lyrics |
| Decade / Mood / Tag chips | Idle → immediate search | Year / tag tokens |
| From lyrics section | Lyric-only FTS hits | Lyrics cache snippets |
| Discover | After local paint, online | MusicBrainz + Last.fm |
| Settings Search lyrics | Toggle / default-on if cache nonempty | FTS lyrics rows |

## Data flow

```
ensureIndexed (ftsCount==0 && Room corpus → rebuildAll)
maybeEnableSearchLyrics …
onQueryChanged → clear ftsRanks/searchError; keep prior paint + isLoading
  → LocalSearch FTS5 MATCH (skip if match query empty) → hydrate
  → rank exact → BM25 → lexical → popularity
  → search3 ∪ Discover
  → server fail → searchError + Retry
```

## FTS5 / BM25

Room **2.7.2** SupportSQLite (no BundledSQLiteDriver). ADR 0084.
`replaceAll` fail → invalidate cache (`null`), not force 0.
`ensureIndexed` cold/post-upgrade backstop.
Discover stub → `indexArtistStub` immediate FTS row + debounce rebuild.
Over-fetch `limit * 3`. loadMore offsets bump only after success.
Punct-only MATCH → `""` → LIKE path.

## Market UI (Scope D)

| Gap | Fix |
|-----|-----|
| Soft-typo / offline / Top hit art / Recents idle / skeletons / Singles / 48dp chips | Prior cycle |
| Silent server fail | `searchError` banner + Retry |
| Filter chips hide on zero | Show when `hasSearched` |
| Result rows &lt;48dp | `heightIn(48)` list + autocomplete |
| Top hit no Play | Track Play IconButton 48dp |
| Lyrics plain | BrandTeal query highlight in snippet |
| Discover text-only | Placeholder thumb |
| Section headers weak | SemiBold white |

**OOS:** mic/voice, cloud typeahead, Did-you-mean, Lyrics filter chip, PTR sync.

## Coverage gate

| Package | Line | Branch |
|---------|------|--------|
| `data/search` | ~96% | ~83% |
| `SearchFts*` (db) | ≥80% line | — |
