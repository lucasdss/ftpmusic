# ADR 0111 — Cast CP Batch Ack + Source-Error Circuit

Date: 2026-10-10  
Status: Accepted  
Related: ADR-0040 (cast ack), ADR-0093 (cast CP append), ADR-0110 (poison cache),  
[CAST_PLAYBACK_DIAGNOSTICS_BEHAVIOR_REPORT.md](../CAST_PLAYBACK_DIAGNOSTICS_BEHAVIOR_REPORT.md)

## Context

Diagnostics v1.8.0 showed:

1. Continuous Play on Cast emitted N× `CastQueueAction.Add` sharing one optimistic
   revision → N `castAckFail` → Dual rollback thrash.
2. Local `Source error` auto-skip reset per-track error count on every transition,
   so a systemic stream failure wiped the whole queue via `removeCached`.
3. CastPlayer EMPTY / dual-advance storms produced ghost scrobbles and noisy
   `saveQueueState` writes.

Poison write reject and scrobble ms→sec already landed (ADR-0110 / prior report).

## Decision

1. **AddAll** — Batch cast appends (`emitCastAddsOrCommit`) as one
   `CastQueueAction.AddAll` → one `RemoteMediaClient.queueInsertItems` → one ack.
2. **Stable diag tokens** — Log `Add` / `AddAll count=N` / …; never rely on
   R8-minified `::class.simpleName` alone.
3. **Source circuit** — Track consecutive Source errors across transitions.
   After 3 within 15s → `CIRCUIT_STOP` (stop player, keep sticky error, stop
   further `removeCached`). Reset on successful play ≥2s or user seek.
4. **Storm guards** — Skip scrobble when `lastTrackedPositionMs < 5000`.
   Skip `saveQueueState` on null/blank mediaId. Log `castAdvance skip=already`
   when sender advance dedupes.
5. **Keep receiver autoplay** — Dual-control (sender jump + `setAutoplay(true)`)
   remains; redesign out of scope for this pass.

## Consequences

- Cast CP appends are ack-atomic like batch remove / ClearAndPlay.
- Systemic stream failure stops after three tracks instead of burning the queue.
- Diagnostics readable in release builds despite R8.
- Residual Cast EMPTY timeline noise may still appear; no longer poisons scrobble
  or persist idx from blank items.
