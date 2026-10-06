# Today Changes Deep Review — Behavior Report

Date: 2026-10-06  
Release: **1.6.0 (10)**  
Scope: queue sheet + search FTS5/BM25/lyrics + sync densify/enrich + audit remediation B

## Commits reviewed (2026-10-06 main)

Queue: save/history/batch, sleep/share, cast Autoplay honesty, audit R1–R3.  
Search: local+server union → FTS5 BM25 → lyrics SERP → empty-FTS rebuild.  
Sync: orphan densify, preserve-enrich, async MetadataEnrichWorker.

## Edge-Case Agent (Caveman)

| Finding | Verdict |
|---------|---------|
| Search `catch(Exception)` swallows cancel → phantom error | **FIXED** rethrow CancellationException |
| `cacheServerResults` REPLACE wipe notes/tags | **FIXED** preserve-enrich upserts |
| loadMore offsets bump before query guard | **FIXED** cancelable loadMoreJob; bump after query match |
| Year filter keep null-albumId singles | **FIXED** drop orphans under year |
| NavHost nextTracks remember miss reorder | **FIXED** key QueueRevisionTracker.revision |
| PlaylistSyncWorker markFlushed without Subsonic OK | **FIXED** checkResponseStatus gate |
| shareQueue appContext no NEW_TASK | **FIXED** FLAG_ACTIVITY_NEW_TASK |
| shareQueue double-tap N playlists | **FIXED** shareMutex single-flight |
| removeFromQueueBatch multi-snapshot | **FIXED** removeBatchOptimistic + ClearAndPlay cast |
| EnrichWorker KEEP skip post-sync | **FIXED** ExistingWorkPolicy.REPLACE |
| densify path/mbid drop on stub | **FIXED** upsertTracksPreserveCache merge |

## Performance Agent (Caveman)

| Finding | Verdict |
|---------|---------|
| Full FTS rebuild ArrayList RAM | Deferred (out of scope 1.6.0) |
| Search path await rebuild when ftsEmpty | Deferred |
| Genre densify offset=COUNT gaps | Deferred |

## Coverage Agent

Touched paths: SearchViewModel cancel/cache/loadMore; LocalSearchRepository year;
PlaylistSyncWorker status; OptimisticQueueDelegate batch; DaosRoom path/mbid;
FavoriteTest share flag + batch delegate.

Gate: `make test` green before Play upload.

## Design Impact Agent

No Compose layout/typography/token changes this pass — behavior/correctness only.
Queue sheet / Search UI surfaces unchanged visually.

## Spec docs

- ADR-0086 — playlist flush status gate + year-orphan drop + enrich REPLACE
- PLAY_RELEASE_1_6_0.md — internal testing pack
