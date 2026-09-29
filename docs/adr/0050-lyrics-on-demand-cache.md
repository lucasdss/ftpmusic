# ADR 0050 — Lyrics On-Demand Cache

Date: 2026-09-29
Status: Accepted
Related: docs/LYRICS_BEHAVIOR_REPORT.md, ADR 0027 (playback flow / lyric index)

## Context

Now Playing needs timed or plain lyrics without blocking metadata sync. Classic Subsonic `getLyrics(artist,title)` is available; OpenSubsonic `getLyricsBySongId` is not wired. Early impl cached in Room but `touch(fetchedAt)` broke 24h TTL, swallow cancel raced track skips, and all-zero `start` looked "synced".

## Decision

1. **On-demand only** — fetch when Now Playing has artist+title. No WorkManager lyrics job.
2. **Room PK = trackId** — display cache per track; network still keyed artist+title until songId API lands.
3. **fetchedAt = fetch time** — no access-time overwrite. TTL 24h drives background refresh. No LRU eviction this pass.
4. **Testable fetcher** — `LyricsFetcher` owns parse/cache/put; NavHost orchestrates UI state on Main.
5. **No fake sync** — all-zero timestamps after LRC attempt → unstructured text.
6. **Negative cache** — empty API result still stored when trackId present to stop remiss spam.
7. **Defer songId** — `getLyricsBySongId` / repository rewrite out of this harden.

## Consequences

- Offline works on cache hit; miss stays empty until network.
- Same title/artist multi-disc may share wrong server lyrics (documented gap).
- Cache table can grow unbounded until prune is added.
- Docs: NAVIDROME capabilities mark `getLyrics`+UI done; songId remains gap.
