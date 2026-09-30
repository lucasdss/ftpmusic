# Surprise Me — Behavior Report

Date: 2026-09-30
Related: ADR-0052, `docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md`

## Scope

One-shot random context start from Home. **Not** Continuous Play. **No** auto-refill.

## Entry

| Surface | Action |
|---|---|
| Home hero card | `LibraryViewModel.playSurpriseMe()` |
| Offline / local-only | `surpriseMeOffline()` → `getRandomCachedTracks(50)` |

## Playback path

1. Fetch ≤50 random tracks (API `getRandomSongs` or cached DAO).
2. `playbackManager.tryStartContext(..., sourceType="random", sourceId="surprise-me", sourceName="Surprise Me")`.
3. Overwrite Ask modal if pending (`showOverwriteModal`).
4. Context replaced (or push/clean per overwrite setting). Priority cleared only per overwrite rules in `playAlbum` / push path.
5. Journal upsert: `random` / `surprise-me` + track ID list (feeds Continuous Play later).

## Explicit non-behavior

- No NavHost / queueSize auto-refill.
- No `addAllToQueue` from Surprise Me.
- Does not gate Continuous Play; does not require Continuous Play ON.
- Empty fetch → no-op.
- Concurrent tap guarded by `surpriseMeLoading`.

## Tests

`LibraryViewModelTest` — online/offline start, no-cache offline no-op, already-playing `tryStartContext`.
