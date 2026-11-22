# BT Tesla A2DP rebind — behavior report

## Symptom

Tesla Bluetooth connect sometimes did not autoplay; Android Auto worked.
Share dump (2026-11-22 18:20:34): `warm_a2dp → allow → Started → skip_playing`.

## Root cause

Path B treated any `isPlaying` as a market no-op. Tesla can attach A2DP while
playback is still routed to the phone.

## Fix

[`BtAudioRoute`](compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/BtAudioRoute.kt) +
[`MediaService.applyBtPlayAfterSeat`](compose/app/src/main/java/com/lucasdss/ftpmusic/app/playback/MediaService.kt):
skip only when playing **and** A2DP route active; else `autoplay_rebind`.

ADR: [0161](adr/0161-bt-a2dp-rebind-when-playing.md). Spec: `specs/tesla-bt-a2dp-rebind-spec.md`.
