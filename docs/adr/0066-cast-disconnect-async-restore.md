# ADR 0066 — Cast Disconnect Async Restore

Date: 2026-10-04  
Status: Accepted  
Related: ADR-0032, ADR-0065, ADR-0038

## Context

Cast→local handoff used `runBlocking` on the main thread for:

1. Manual disconnect `savePositionOnly` (Room)
2. `switchToLocalPlayback` `persistenceManager.restore()` + queue rebuild

Slow DB → ANR / jank. Cold-start restore already used `scope.launch` +
`withContext(Main)`. ADR-0065 flagged this as future work.

## Decision

1. **Capture on main** — resolve cast index/position before clearing Cast state.
2. **Save on `persistenceScope`** — `castDisconnectSaveJob = launch { savePositionOnly }`.
3. **Seat swap sync on main** — ExoPlayer / MediaSession authority must flip immediately.
4. **Restore async** — `scope.launch { saveJob.join(); restore(); withContext(Main) { applySwitchToLocalRestore } }`.
5. **Idempotent guard** — `switchingToLocal` stays true until async restore finishes.
6. Keep bounded sync Room only for swipe-away teardown paths already documented in ADR-0032.

## Consequences

- Cast disconnect no longer parks main on Room.
- Brief window where Exo seat is live before full queue restore completes (same class as cold start).
- Position save must complete before restore (`join`) or seek uses stale pos.
