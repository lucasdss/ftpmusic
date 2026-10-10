# Post-1.8.0 Delta Bugfix — Behavior Report

Date: 2026-10-10  
Anchor: `v1.8.0` → HEAD  
Policy: heal **C** (hybrid) + scope **P1/P2**

## Edge-Case Agent (caveman)

- Poison JSON/XML span: heal delete + clear pin. Offline seek must skip.
- Unknown magic (WMA-like random bytes ≥4KiB): keep file+pin; `isPlayableCached=false`.
- Undersize span: POISON heal.
- Auto-skip overlap: gen token + still-on-failedId abort; sticky banner flag stays until transition.
- Library/Favorites chrome under overlay: Column top pad; Lazy no double header pad.
- Header pre-measure: lastKnown height fills inset frame 0.
- FULL reconcile: keep `last_played_at` even when `play_count=0`.
- loadMore near-end: `distinctUntilChanged` — one fire per enter near-end.

## Performance Agent (caveman)

- Offline/BT `hasPlayableSpan` sync magic peek — no Main heal I/O.
- Mix/download still use suspend `isPlayableCached` (heal poison only).
- Alpha loadMore: less coroutine churn.

## Fixes shipped

1. `AudioCacheValidation.PayloadVerdict` + AIFF/WavPack + CT mp4/x-flac.
2. `CacheService.hasPlayableSpan` / hybrid heal / `initialize` classify.
3. `MediaService` offline+BT playable jump; auto-skip gen guard.
4. Library/Favorites Column `padding(top=headerPad)`.
5. Reconcile `AND last_played_at IS NULL`.
6. Header inset last-known; loadMore debounce.

## Confirm

| Bug | How |
|-----|-----|
| Chrome under header | Cold open Library/Favorites expanded — chips below logo |
| Poison offline | Plant Subsonic JSON span; airplane; error → jump skips poison |
| Unknown pin | Plant 5KB random span + isDownloaded; `isPlayableCached` false; pin remains |
| Reconcile | Room row `last_played_at` set, `play_count=0`, not in album/genre — FULL heal keeps |
| Auto-skip race | Rapid Source errors — no double seek past wrong id |

## Design Impact (caveman)

- Hit targets: chips/search fully below header; no overlay clip.
- Home/Search unchanged (content already in Lazy).
- Semantic spacing: Lazy top = `spacingS()` after Column pad.

## Coverage

Unit: `AudioCacheValidationTest`, `CacheServiceTest` hybrid, `DaosRoomTest` last_played, header inset helpers.
