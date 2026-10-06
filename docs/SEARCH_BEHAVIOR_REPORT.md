# SEARCH_BEHAVIOR_REPORT

Caveman style. FTP Music search — Phase-7 FTS5 + BM25 (Room 2.7.2 SupportSQLite).

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
maybeEnableSearchLyrics (unset + lyrics count>0 → KEY=true + rebuild 500ms)
onQueryChanged → keep prior + isLoading
  → LocalSearch FTS5 MATCH ORDER BY bm25 → hydrate-by-id (preserve order)
  → split lyric-only vs title tracks
  → rank (exact → BM25 → lexical → play_count/recency)
  → pickTopHit (exact artist/album > first track)
  → search3 ∪ Discover
```

## FTS5 / BM25 (shipped)

Room **2.7.2** + **SupportSQLite** (no `BundledSQLiteDriver` this cycle).
Driver deferred: Support-only migrations + `SearchFtsDao.openHelper` break under
`setDriver` — see ADR 0084 amendment.
No `@Fts5` in Room API → `search_fts` via migration 60→61 +
`FTS5_CALLBACK` (onCreate/onOpen try/catch); `SearchFtsDao` store.
`bm25(search_fts, 1.0, 0.0, 10.0)` — body weighted. Over-fetch `limit * 3`.
`ftsRanks` = min BM25 per entityId; loadMore reuses ranks from state.
Rebuild clear+insert in one transaction.

## Corpus densify (2A)

Genre warm top-**100**; album drain cap **400**/delta sync.
Enrich caps: bio 40 / album 25 / alias 30 / tag 30 (MB + Last.fm only).
Singles (`album_id` null): year filter **keeps** them (FTS body may hold decade).
Track FTS body: path + folder tokens + genre + year/decade/mbid.
Top hit: cover art + title + artist · album|"Singles" (≥48dp). Soft-typo empty:
"No close matches — check spelling".

## Market UI (P0/P1 shipped)

| Gap | Fix |
|-----|-----|
| Soft-typo tip lied | Honest empty copy when `usedSoftTypo` |
| Discover artist dead tap | Stub upsert → navigate |
| Top hit text-only | Cover + title + subtitle, ≥48dp |
| Recents only when focused | Idle empty query shows Recents |
| Offline silent | Banner "Offline · downloaded only" |
| Discover offline silent | "Discover unavailable offline" |
| Loading thin | 6 skeleton rows while loading |
| Lyrics Singles | Same Singles subtitle rule |
| Tiny hit targets | Recent ✕ / Tune ≥48dp |
| Weak tip contrast | Secondary tip text `#888`/`#999` |

**OOS (P2):** mic/voice, entity typeahead, sticky filter chips on zero,
Did-you-mean chip, Lyrics filter chip.

## Market gap (local-first)

| Cap | Spotify/Apple/YT | FTP Music |
|-----|------------------|-----------|
| Relevance | Cloud ML | **BM25 + fusion** |
| Offline | Weak | Strong local FTS5 + banners |
| Singles | First-class | Densify + Singles label |
| Tags | Graph | Last.fm `search_tags` + mood chips |
| New APIs | — | Discogs/Spotify/AcoustID **OOS** |

## Coverage (Phase-7 review)

JaCoCo unit gate (Compose UI excluded — no screenshot tests this cycle):

| Package | Line | Branch |
|---------|------|--------|
| `data/search` | ~97% | ~85% |
| `SearchFts*` (db) | ~96% | — |

Branch coverage raised via merger early-return / exact>BM25, LocalSearch
playable/LIKE/soft-typo/match-throw, ViewModel Discover navigate + loadMore ranks,
FTS5 callback CREATE test.
