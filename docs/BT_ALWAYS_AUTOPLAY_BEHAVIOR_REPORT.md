# BT Always Autoplay + Media Provider — Behavior Report

Date: 2026-11-05  
Related: [ADR-0154](adr/0154-bt-bluetooth-autoplay.md), ADR-0148 (superseded in part),
ADR-0072, ADR-0087, ADR-0101, ADR-0133  
Loop: android-orchestrator plan → implement

## Problem

With Bluetooth resume ON (ANY), Pixel 8 Pro (API 37) diagnostics showed
`warm_a2dp → allow → Started → seat_only|skip_playing`. User wanted:

1. Always be the car media provider on BT connect.
2. Always auto-play when possible.
3. Wait for Play only when autoplay is impossible.

Separate report: car “media provider not present” + no phone notification —
distinct from `seat_only` (which keeps media FGS `1001`).

## Market correction

| Peer | Behavior |
|------|----------|
| Symfonium | **Bluetooth auto play** + **Ignore remote stop** |
| Substreamer | Provider-first (MLS/MBS); no A2DP autoplay |
| ADR-0148 claim | Seat paused on API ≥37 — **wrong for Symfonium** |

## Decision shipped (ADR-0154)

- `shouldBackgroundAutoplay` → always `true`.
- `applyBtPlayAfterSeat` → `play()` + `autoplay`/`tap_play` + `PlaySuppressionWatch`.
- Settings: “Bluetooth auto play”; stronger notif warning; **Ignore remote stop** toggle.
- Telemetry: `source=warm|cold` on Path B crumbs.

## Wait-for-Play still when

Empty/offline (`1003`), FGS deny (`1002`), casting, already playing
(`skip_playing`), or `play_suppressed` after attempt (seated `1001` remains).

## Device check

1. BT auto play ON / ANY → A2DP connect → expect `autoplay` (not `seat_only`).
2. Share diagnostics: crumbs include `source=warm` or `source=cold`.
3. Force-stop → cold connect: look for `source=cold` / `NotificationFallback`.
4. Enable Ignore remote stop → car STOP should pause, not clear queue.
