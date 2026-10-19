# Home Recently Added — Behavior Report

Caveman terse. ADR-0109 + amend (resume TTL + revision StateFlow).

## Clocks (do not conflate)

| Clock | Default | Drives |
|-------|---------|--------|
| Sync Interval | 12h | WM periodic library sync attempt |
| FULL heal | 7d since `lastFullSyncMs` | FULL vs DELTA **mode** |
| Home Recent TTL | 5m | When Home may hit `getAlbumList2(newest×10)` |
| Auto cooldown | 5m in-process | Soft-skip auto `syncNowAsync` |

`lastDeltaSyncMs` = watermark only — **not** a schedule input.

## Load path

1. `onHomeResume()` / init / connect → `loadRecentlyAddedHome`
2. Paint prefs ids from Room **only when** strip empty or ids changed (preserves ratings)
3. If TTL stale + online → newest×10 → write snapshot → state
4. If ordered ids changed → `syncNowAsync(DELTA)`
5. Empty/timeout → `touchHomeRecentFetched` (no revision bump)

## Sync warm → UI

`writeHomeRecentSnapshot` → bump `homeRecentRevision` → VM collector →
`paintHomeRecentFromSnapshot` **iff** ids ≠ current strip ids (cold start, revision with
new ids, or empty strip). Fresh-TTL resume with same ids **skips** Room paint so API
`userRating` stars stay on the strip (Room `CachedAlbumEntity` has no rating column).

Empty sync-warm newest → `touchHomeRecentFetched()` (TTL backoff; no revision bump).

## Diagnostics

Header includes `syncIntervalHours`, `homeRecentFetchedMs` /
`homeRecentTtlMin` / `homeRecentIdsCount`, `lastWmAttemptMs` /
`lastWmSkipReason` (plus existing full/delta watermarks).

## Log decode (2026-10-19 capture)

- `lastDelta` 16:13Z, `lastFull` 16:22Z, capture 18:05Z → ~1h43m since sync
- Default 12h interval → **not overdue**
- Symptom was Home TTL/resume + prefs-not-UI, not stalled DELTA schedule
