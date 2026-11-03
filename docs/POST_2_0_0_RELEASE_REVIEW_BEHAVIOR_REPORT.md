# POST 2.0.0 → 2.1.0 release review (caveman)

**Baseline:** `v2.0.0` (versionCode 26)  
**Target:** `2.1.0` (versionCode 27)  
**Range:** 17 commits after tag + post-release P1 fixes  
**Room:** 68 (unchanged)

## Committed themes since 2.0.0

| Area | Notes |
|------|-------|
| Sync | HiltWorkerFactory (F1), SyncHealthGuard foreground heal |
| Playback errors | SourceErrorClassification, auto-skip circuit/burst, AlbumReconciler |
| BT / car | Seat education, mute detect, F1/F3 parity, PlaySuppressionWatch |
| Radio | Admin create/update/delete via Subsonic (ADR-0151) |
| Cache / scrobble | Targeted cache writes; process-scoped scrobble; COALESCE metadata |
| Continuous Play | Journal score, low-water prefetch, ENDED retry, fallback ladder, tail cap 50 |
| Daily Mix | Soft artist cap/adjacency, freshness, history prune, empty-day outcome |
| Artist / album | Songs browse sort + album track order (ADR-0152) |

## Discover / Define (cross-model)

Discover + Define over `v2.0.0..HEAD`. No leftover P0. Claimed CP/DM/MET P1s
from earlier review logs are closed in later §11 iterations (see DM review).

## Confirmed bugs → fixed (ship in 2.1.0)

### P1-A Cast autoplay prune+append shared optimistic revision — High

**Symptom:** `RemoveAll` then `AddAll` share one snapshot; first ack commits;
second fail cannot roll back; empty RemoveAll acks success before AddAll.  
**Fix:** `CastQueueAction.RemoveThenAddAll`; MediaService chains remove→insert
with a single final ack. Insert fail after remove landed → session-direct
`queueLoad` heal from Dual, then **commit** (rollback would diverge worse).  
**Test:** `AutoplayTailCapTest`, `CastQueueCommandExecutorTest`,
`MediaServiceCastQueueTest` (remove fail / insert-after-remove heal /
heal-fail still commits).

### P1-B Offline auto-skip never paused the burst — High

**Symptom:** `applyUnreachableSkip` recorded `disruptiveEntry=false`, so
`PAUSE_BURST` never tripped on long offline bad-span cascades.  
**Fix:** `recordUnreachableCachedJump` counts disruptive; pause at 8/120s.
Unreachable UNKNOWN/NETWORK still uses intentional 3/15s `CIRCUIT_STOP`.  
**Test:** `SourceErrorClassificationTest` unreachable jump helpers.

### P1-C BT fallback `notify` after notifications disabled — Med

**Symptom:** logged “notif disabled” then still called `NotificationManager.notify`
→ `SecurityException` on API 33+ could kill the BT receiver path.  
**Fix:** early-return when disabled; wrap `notify` in `SecurityException` catch.  
**Test:** `BtAutoplayStarterRobolectricTest` asserts no active notification.

## Looks correct (no further code)

- F1 HiltWorkerFactory + SyncHealthGuard
- CP gate / retry / local tail cap / restore trim
- Daily Mix soft-cap, fresh-skip, history prune
- Scrobble local-first + targeted metadata
- Artist songs keyset + album order; Radio admin UI + server 50
- Cache targeted promotion race

## P2 deferred (not blockers)

- Empty-day suppress sticky until process death / next calendar day
- `PlaySuppressionWatch` mute / slow-buffer blind spots
- Scrobble launch-on-`MediaService.scope` before singleton `scrobble()`
- CP whole-queue exclusion; `tryStartContext` journal gap; DM N+1 freshness
- MediaService E2E continuous-play harness
- Cast `beforeEntryId` after remove (latent; producer passes null)

## Stale narrative notes

- `DAILY_MIX_CONTINUOUS_PLAY_REVIEW.md` §11.4 “remaining” rows are historical;
  later §11.5–11.11 close MET-9 / DM-3 / CP-*.
- `BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md` F1/F3 blockers are closed by
  `501d123` + this cut’s notif guard; see
  `CAR_CONNECT_AUTOPLAY_SPOTIFY_PARITY_REVIEW.md`.

## Gates (must green before upload)

```bash
cd compose && ./gradlew :app:assembleDebug
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```

**Gate results (2026-11-03):** all green — `:app:assembleDebug`, `test-report`,
`quality` (ktlint + detekt), `lintVitalRelease`, `bundle-release` + signed AAB
+ native-symbols (pre-stripped androidx path soft-warn OK).
