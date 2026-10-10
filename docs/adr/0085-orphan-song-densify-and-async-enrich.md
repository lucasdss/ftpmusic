# ADR 0085 — Orphan song densify and async enrich

Date: 2026-10-06
Status: Accepted
Related: ADR 0045, ADR 0068, ADR 0078, ADR 0079, docs/LIBRARY_SYNC_BEHAVIOR_REPORT.md,
docs/METADATA_SYNC_SEARCH_ALIGNMENT_BEHAVIOR_REPORT.md

## Context

Local search (FTS) depends on densified `tracks` + enrich fields on
`cached_artists` / `cached_albums`. Gaps:

1. FULL/DELTA `REPLACE` upserts nullled notes/biography/aliases/tags until
   re-enrich.
2. Songs without albums only entered Room via starred / genre sample / search
   cache — never a dedicated densify path.
3. Enrich ran inline in `MetadataSyncWorker`, extending SyncingScreen / WM
   completion on MB/Last.fm latency.

Product chose eventual densify (1A) + dedicated orphan densify (2B).

## Decision

1. **Preserve-enrich upserts** — `upsertAlbumsPreserveEnrich` /
   `replaceAlbumsDiffPreserveEnrich` (and artist twins) merge catalog fields
   while keeping enrich columns when incoming is blank. FULL heal = delete
   missing ids + upsert-preserve (not clear+replace).
2. **`syncOrphanSongs`** after genres:
   - Genre offset continuation when server `songCount` > local
     `cached_genre_songs` count (`getSongsByGenre` + offset).
   - `getRandomSongs` rounds upsert into `tracks` with nullable `album_id`.
   - Never require `cached_albums` for searchability.
3. **Async enrich** — `MetadataEnrichRunner` + `MetadataEnrichScheduleWorker`
   (WorkManager one-shot). Sync enqueues after populate+FTS; watermarks do not
   await enrich. Enrich ends with `scheduleRebuild`.
4. **Honest counts** — after `getAlbum`, set `song_count` from fetched list
   size. SyncStatus `trackCount` = `COUNT(tracks)` (search corpus).

## Consequences

- DELTA still caps album-track drain (400) and orphan page/round budgets —
  corpus converges over intervals.
- Enrich KEEP policy: concurrent enqueue does not stack duplicate work.
- Identity remains Subsonic `id` only (no semantic dedupe).
- ADR 0079 amend: enrich is deferred WM, not inline sync.
- Amend (ADR-0108): densify remains additive on DELTA; FULL reconcile drops
  ghost corpus ids not present in album/genre caches (unless local weight).
