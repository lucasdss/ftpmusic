# Cast / Playback Diagnostics — Behavior Report

Date: 2026-10-10  
Source: device diagnostics v1.8.0 (12) Pixel 8 Pro  
Related: [POISON_CACHE_CAST_DIAGNOSTICS_BEHAVIOR_REPORT.md](POISON_CACHE_CAST_DIAGNOSTICS_BEHAVIOR_REPORT.md) (poison + scrobble units — already fixed)  
ADR: [0111-cast-cp-batch-ack-and-source-circuit.md](adr/0111-cast-cp-batch-ack-and-source-circuit.md)

Status: remaining main gaps closed (AddAll batch ack, Source circuit, cast storm guards)

## Symptom (logs)

- Cast mid-play: empty `transition id=`, idx Cast≠local, ghost scrobbles `secs=100800+`
- Last cast item → flood `syncLocalToRemote action=p` (R8 `Add`) → `castAckFail revision=13` ×N
- Cast disconnect `2161` → local `Source error` → `autoSkip SKIP_NEXT` + `removeCached` through queue
- Fresh artist play local → all tracks Source error → `LAST_TRACK_STOP`

## 1.8.0 vs main (already fixed — do not redo)

| Token | Fix |
|-------|-----|
| `scrobble … secs=100800` | `ScrobbleDuration` ms→sec |
| Poison ~182B cache | `AudioCacheValidation` + heal |
| Cast end 2055 thrash | reconnect only 2155/2161 |
| Blank transition breadcrumb | skip blank mediaId diag |

## Remaining root causes → fixes

### Edge-Case Agent (caveman)

- CP cast: N× Add same rev → N ackFail → Dual rollback thrash. Fix: one `AddAll` + one ack.
- Source cascade: `playerErrorCount` reset each transition → infinite SKIP_NEXT+removeCached. Fix: consecutive circuit → `CIRCUIT_STOP` after 3 in 15s.
- Cast EMPTY: saveQueue rewrite idx from Cast window. Fix: skip save on blank/null item.
- Storm ghost scrobble: AUTO with `lastTrackedPositionMs < 5s`. Fix: skip scrobble.
- Dual advance (sender jump + receiver autoplay): keep; document risk only.

### Performance Agent (caveman)

- One RMC `queueInsertItems` per CP burst — not N PendingResults.
- Circuit stop ends prepare/seek storm — less Main work + less cache I/O.

### Coverage Agent (caveman)

- `CastQueueCommandExecutor` AddAll, `SourceErrorCircuit`, scrobble storm guard, `decideErrorSkipAction`+CIRCUIT ≥80% on those units.

## Final behavior

| Path | Behavior |
|------|----------|
| Cast CP / `appendToContext` | Single `AddAll` → one insert → one castAck |
| Diag cast action | Stable `Add` / `AddAll count=N` (not R8 simpleName) |
| 3 Source errors / 15s | `CIRCUIT_STOP` — stop, sticky banner, no further removeCached |
| First Source errors | Still await removeCached then SKIP_NEXT (ADR-0110) |
| Listen under 5s before AUTO | No scrobble |
| Null/blank mediaId transition | No saveQueueState persist |
| Advance dedupe hit | `castAdvance skip=already` breadcrumb |

## Confirm

| Bug | How |
|-----|-----|
| CP castAckFail flood | Cast + CP on; last track; one AddAll log; ≤1 castAck |
| Circuit | Force 3 Source errors fast; stop; cache of later tracks intact |
| Storm scrobble | Cast EMPTY thrash; no secs=100800-style ghosts from sub-5s listens |
