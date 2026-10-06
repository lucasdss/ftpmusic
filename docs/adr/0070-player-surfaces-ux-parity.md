# ADR 0070 — Player Surfaces UX Parity (NP · Mini · Queue)

Date: 2026-10-04
Status: Accepted
Related: ADR-0039 (dual queue), ADR-0053 (autoplay), ADR-0054 (tokens),
ADR-0057/0058 (typography), `docs/PLAYER_SURFACES_UX_BEHAVIOR_REPORT.md`

## Context

Ship player chrome is a single `PlayerBar` (mini + full Now Playing + in-player
queue sheet). Legacy `QueueScreen` held richer edit (reorder / swipe) but was
never routed. Sheet showed decorative drag handles, mislabeled shuffle, dead
sleep-timer wiring, always-visible idle mini, and hardcoded English with
"Continue Playing" labels that drifted from Spotify / Apple / YT Music.

## Decision

1. **Queue SoT UI** = `PlayerBar` `queue_sheet`. Port calvin `reorderable` +
   swipe-remove; wire `moveQueueItem` / `beginQueueReorder` / `commitQueueReorder`.
   Keep `QueueScreen` as aligned test harness (delete later).
2. **Mini** = progress display only (no seek). Hidden when `!PlaybackState.isVisible`.
3. **Shuffle in sheet header** = playback shuffle mode (`onShuffleToggle`), label "Shuffle".
4. **Sleep timer** = reachable from NP header (Timer icon → existing NavHost dialog).
5. **Lexicon** = Now Playing / Up Next / Queue Next / **Next in Queue · n** /
   Next from · … / Autoplay · n (Spotify-explicit dual; ADR-0074 extends).
6. **Copy** = `strings_player.xml` + `stringResource` (EN).
7. **Typography/spacing** = ADR-0057 roles + `spacing*` / `corner*` tokens on player surfaces.
8. **Buffering** = `PlaybackState.isBuffering` drives indeterminate play control (local + cast).
9. **Errors** = no NP error strip until a canonical error field exists on `PlaybackState`.

## Consequences

- NavHost gates mini on `isVisible`; passes move/reorder callbacks into `PlayerBar`.
- `UpcomingTrack.entryId` supports stable reorder keys.
- Tests assert sheet reorder wiring, mini visibility policy, and lexicon strings.
