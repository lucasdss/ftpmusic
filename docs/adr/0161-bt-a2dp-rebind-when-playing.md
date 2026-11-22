# ADR-0161: BT Path B rebind when playing but not on A2DP

## Status

Accepted

## Context

ADR-0154 / F3 skipped `play()` whenever `isPlaying` (market no-op). Tesla warm
A2DP often arrives while the phone session still reports playing on a non-car
route → diagnostics `skip_playing`, silent cabin. Android Auto Path A still
works via MediaSession PLAY.

## Decision

Gate `skip_playing` on **both** `isPlaying` and `BtAudioRoute.isA2dpOutputActive`.
If playing but not on A2DP, call `play()` and record `autoplay_rebind`.

## Consequences

- Tesla reconnect while “playing” on phone rebinds audio without requiring AA.
- True on-car playing stays a no-op (no mid-track pause/replay).
- Diagnostics gain `a2dpOut` / `isPlaying` extras for field triage.
