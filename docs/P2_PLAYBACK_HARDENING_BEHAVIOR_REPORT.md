# P2 Playback Hardening — Behavior Report

Date: 2026-10-04  
Related: ADR-0053, ADR-0065, ADR-0066

## Goal

Close three leftover risks from PlaybackManager coverage review:

1. Autoplay stamps die on process death
2. Identity-hash LRU stamp maps misread as death durability
3. Main-thread `runBlocking` Room on cast disconnect / switchToLocal

## Hardening shipped

1. **Room `queue_items.is_autoplay`** (v55→v56) — save/restore via `SavedQueueState.isAutoplayFlags`; `restoreQueue` applies `withAutoplay`; any restored autoplay → `hasLoadedContinuation = true` (no CP double-append).
2. **Stamp maps** — remain JVM/in-process Bundle shims (identityHash + LRU 2048). Docs clarify Room owns death durability for autoplay + entryId.
3. **Cast disconnect async (ADR-0066)** — capture idx/pos on main → `persistenceScope` `savePositionOnly` → seat swap sync → `scope.launch` joins save then Room `restore` on IO → `applySwitchToLocalRestore` on Main. No `runBlocking` on cast handoff path.

## Edge cases tested

| Case | Test |
|---|---|
| save/restore autoplay flags | `QueuePersistenceManagerTest` |
| mismatched autoplay list ignored | same |
| restoreQueue stamps autoplay | `RestoreQueueTest` |
| autoplay coerce with url skew | same |
| migration 55→56 | `AppDatabaseMigrationsTest` |
| applySwitchToLocal sizes-match seek | `MediaServiceCastQueueTest` |

## Perf

- One bool column per queue row; rewrite-all save unchanged cost class.
- Cast DB off main; seat swap still sync (required for Exo/session).

## Coverage (re-measured)

| Class | Line | Branch |
|---|---|---|
| `PlaybackManager` | **98.3%** (684/696) | **83.0%** (357/430) |
| `QueuePersistenceManager` | **100%** (78/78) | **100%** (48/48) |

`MediaService` remains a large class below whole-file 80% historically; new paths covered via `MediaServiceCastQueueTest` + cold-start/cast policy tests.
