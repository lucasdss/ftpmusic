# Metadata Sync × Search Alignment — Behavior Report

Caveman terse. Truth after ADR-0085.

## Goal

Sync path densify local corpus for FTS search. No dupes (id PK). Songs OK
without albums. Enrich async — UI never waits MB/Last.fm.

## Phase order

```
albums → artists → genres → orphans → stars → album-tracks
  → artist album_count → daily-mix seed → populate tracks → FTS → enqueue enrich
```

## Writes

| Mode | Albums | Artists |
|------|--------|---------|
| DELTA | `upsertAlbumsPreserveEnrich` | `replaceArtistsDiffPreserveEnrich` |
| FULL | `replaceAlbumsDiffPreserveEnrich` | same |

Preserve: notes / bio / aliases / tags / MBID / similar JSON when incoming blank.

## Orphan densify (2B)

- Genre offset when server `songCount` > local cache count
- `getRandomSongs` rounds → `tracks` via `upsertTracksPreserveCache`
- null `album_id` allowed; no `cached_albums` required
- Budgets: DELTA 3 genre pages + 2 random rounds; FULL/force 20 + 6

## Counts

- Album `song_count` ← fetched `getAlbum` list size when written
- SyncStatus `trackCount` ← `COUNT(tracks)` (search corpus)
- Artist `album_count` recomputed post tracks (existing SQL)

## Enrich

- `MetadataEnrichRunner.enqueue` → WM `MetadataEnrichScheduleWorker`
- Caps unchanged (40/30/30/25) + 250ms delay
- Ends with `scheduleRebuild` (not blocking sync watermark)

## Edge

- Mid album-list fail → no write (ADR-0068)
- REPLACE wipe enrich → fixed by preserve upsert
- Cross-source same id → PK merge COALESCE (album wins album_id)
- Drain cap 400 DELTA kept — eventual converge (1A)

## Tests

Worker/DAO/Enrich cover preserve, orphan null album_id, genre offset,
random dedupe, enrich enqueue, song_count honesty.
