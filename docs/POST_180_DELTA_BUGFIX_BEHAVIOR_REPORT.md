# Post-1.8.0 Delta Bugfix — Behavior Report

Date: 2026-10-10  
Anchor: `v1.8.0` → `v1.9.0`  
Policy: heal **C** (hybrid) + ship blockers ADR-0112

## Edge-Case Agent (caveman)

- Poison JSON/XML span: heal delete + clear pin. Offline seek must skip.
- Unknown magic (WMA-like random bytes ≥4KiB): keep file+pin; `isPlayableCached=false`.
- Mix UNKNOWN: `hasMixOwnedSpan=true` — no re-enqueue churn.
- Undersize span: POISON heal.
- Pinned poison on Source-error: `removeCached` force-heals (not refuse).
- Auto-skip overlap: gen token + still-on-failedId abort; sticky banner flag stays until transition.
- CIRCUIT_STOP: reset circuit after stop — next session can seek.
- Scrobble: ghost ≤250ms skip; short real listen scrobble.
- Home newest timeout/empty: touch `fetched_at`; keep ids.
- Library/Favorites chrome under overlay: Column top pad; Lazy no double header pad.
- Header pre-measure: lastKnown height fills inset frame 0.
- FULL reconcile: keep `last_played_at` even when `play_count=0`.
- loadMore near-end: `distinctUntilChanged` — one fire per enter near-end.

## Performance Agent (caveman)

- Offline/BT `hasPlayableSpan` sync magic peek — no Main heal I/O.
- Mix/download still use suspend paths (heal poison only).
- Alpha loadMore: less coroutine churn.
- Home empty/timeout: no hammer newest every open.

## Fixes shipped (1.9.0)

1. `AudioCacheValidation.PayloadVerdict` + AIFF/WavPack + CT mp4/x-flac.
2. `CacheService.hasPlayableSpan` / hybrid heal / `initialize` classify.
3. `MediaService` offline+BT playable jump; auto-skip gen guard; CIRCUIT_STOP reset.
4. `removeCached` force-heal pinned POISON; `hasMixOwnedSpan` for mix ownership.
5. Scrobble ghost gate (250ms) replaces 5s hard floor.
6. Home TTL touch on timeout/empty.
7. Library/Favorites Column `padding(top=headerPad)`.
8. Reconcile `AND last_played_at IS NULL`.
9. Header inset last-known; loadMore debounce.
10. Untrack `compose/macrobenchmark/build/**`.

## Confirm

| Bug | How |
|-----|-----|
| Chrome under header | Cold open Library/Favorites expanded — chips below logo |
| Poison offline | Plant Subsonic JSON span; airplane; error → jump skips poison |
| Pinned poison Source-error | Plant poison + isDownloaded; Source error → span+pin cleared |
| Unknown pin | Plant 5KB random span + isDownloaded; `isPlayableCached` false; pin remains; mix no re-enqueue |
| Short scrobble | Play ~3s track past 60% — scrobble fires |
| Circuit sticky | 3 Source errors stop; resume play — first error seeks not instant re-stop |
| Home TTL empty | Stale TTL + empty newest — `fetched_at` stamped; next open within TTL skips network |
| Reconcile | Room row `last_played_at` set, `play_count=0`, not in album/genre — FULL heal keeps |
| Auto-skip race | Rapid Source errors — no double seek past wrong id |

## Design Impact (caveman)

- Hit targets: chips/search fully below header; no overlay clip.
- Home/Search unchanged (content already in Lazy).
- Semantic spacing: Lazy top = `spacingS()` after Column pad.
- Playback/cache ship blockers: no Compose chrome regression.

## Coverage

Unit: `AudioCacheValidationTest`, `CacheServiceTest` hybrid/pin-poison/`hasMixOwnedSpan`,
`MixCacheCoordinatorTest` UNKNOWN ownership, `SourceErrorCircuitTest` ghost scrobble +
circuit clear, `LibraryViewModelTest` Home TTL backoff, `DaosRoomTest` last_played,
header inset helpers.
