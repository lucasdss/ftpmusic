# ADR 0112 — Post-1.8.0 ship blockers for 1.9.0

Date: 2026-10-10  
Status: Accepted  
Related: ADR-0110 (cache validation), ADR-0111 (Source circuit / Cast), ADR-0109 (Home TTL), ADR-0018 (Play pipeline)

## Context

Deep review of `v1.8.0` → HEAD found ship blockers before Play cut 1.9.0:

1. Source-error `removeCached` refused pinned downloads even when the span was
   Subsonic error JSON → poison stayed under `isDownloaded=true`.
2. Scrobble storm gate required ≥5s listen for all AUTO/60% scrobbles → short
   tracks never scrobbled.
3. `CIRCUIT_STOP` left consecutive error state uncleared → next error in the
   15s window immediately re-stopped without seek.
4. Mix auto-cache used `isPlayableCached` → UNKNOWN magic re-enqueued forever
   while writes reject the same payload.
5. Home Recently Added timeout/empty returned without stamping `fetched_at` →
   newest poll every Home open when stale.
6. ~352 `compose/macrobenchmark/build/**` artifacts were tracked in git.

## Decision

1. **Pinned poison heal** — `CacheService.removeCached` classifies pinned spans;
   `POISON` force-heals (clear pin + span); healthy pins still refused.
2. **Scrobble storm** — `shouldScrobbleAfterListen` rejects only near-zero
   listens (`≤ SCROBBLE_STORM_MAX_GHOST_MS` = 250ms); short real listens pass.
3. **Circuit clear** — MediaService `CIRCUIT_STOP` resets via `onSuccessfulPlay()`.
4. **Mix ownership** — `CacheService.hasMixOwnedSpan`: PLAYABLE/UNKNOWN = owned;
   POISON heals and returns false. Mix enqueue/prune uses this, not strict
   playable.
5. **Home TTL backoff** — timeout/empty call `MetadataSyncWorker.touchHomeRecentFetched()`
   (stamp fetch ms, keep ids).
6. **Hygiene** — untrack macrobenchmark build outputs; gitignore
   `compose/macrobenchmark/build/`.

## Consequences

- Poison downloads can recover on Source-error without user clear-downloads.
- Short tracks scrobble when AUTO/60% fires with a real position.
- Circuit stop no longer sticky across the next play attempt.
- Mix UNKNOWN codecs stop download churn; poison still re-enqueues.
- Flaky newest API no longer hammers every Home open within TTL.
- Release tag `v1.9.0` / versionCode 13 ships this remediation with ADRs 0107–0111.
