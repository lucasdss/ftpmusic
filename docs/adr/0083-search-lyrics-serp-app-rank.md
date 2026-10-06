# ADR 0083 — Lyrics SERP + app-side ranking (FTS4 kept)

Date: 2026-10-06
Status: Accepted
Related: ADR 0080, docs/SEARCH_BEHAVIOR_REPORT.md

## Context

Lyrics FTS rows existed when Settings toggle on, but Search UI ignored
`lyricTrackIds`. Ranking was lexical tiers only; Top hit always preferred
first track. FTS5/BM25 blocked on Room API.

## Decision

1. Keep FTS4. No Room 3 / BundledSQLiteDriver this phase.
2. If `KEY_SEARCH_LYRICS` unset and `lyrics_cache` count > 0 → default-on
   + `scheduleRebuild(500)`. Explicit false stays off.
3. Lyric-only FTS ids → **From lyrics** section with snippet from cache;
   title-matched tracks stay in Songs.
4. `SearchResultMerger.rankByFields` adds popularity tie-break
   (`play_count` + coarse `last_played_at`).
5. `pickTopHit` prefers exact artist/album name match over first track.
6. Top result shows subtitle (artist · album).

## Consequences

- Users with cached lyrics get lyrics search without hunting Settings.
- Ranking still not BM25; Room 3 migration remains future work.
