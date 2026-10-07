# BT Deep-Sleep Media Resume — Behavior Report

Date: 2026-10-07
Related: ADR-0088, ADR-0087, ADR-0072, ADR-0019
Compat: Android 16 (API 36) toolchain; Android 17 runtime via SDK_INT ≥ 37 guards

## Problem

Long deep sleep → process death → Bluetooth audio reconnects → head unit shows
**no media** until user opens FTP Music. Observed with Bluetooth resume **ON / any**.

## Causes

| Cause | Effect |
|---|---|
| No `onPlaybackResumption` (fixed ADR-0087) | MediaButtonReceiver wakes empty playlist |
| Async Room restore before seat (hardened ADR-0088) | Car binds before items seated → “no media” |
| Post-Doze FGS deny on A2DP | Service never starts; phone Resume notif |
| Android 17 BFSL FGS without WIU | `play()` silently silenced (`AudioHardening`) |

## Flows

### A2DP connect (ADR-0072 + ADR-0088 sync seat)

```
A2DP STATE_CONNECTED
  → BtConnectionReceiver (goAsync)
  → BtResumePolicy (ANY allows null MAC via "*")
  → startForegroundService(ACTION_BT_AUTOPLAY)
  → MediaService early startForeground
  → sync/gated Room restore → seat MediaItems
  → API ≤36: play()
  → API ≥37: Resume notif (WIU) + MediaButton path; no silent-only play
  → if FGS blocked → high-pri “Resume playback” notif
```

### Media button / System UI resumption (ADR-0087/0088)

```
MEDIA_BUTTON or System UI resume
  → Media3 MediaButtonReceiver (FGS + WIU for BT play)
  → onPlaybackResumption(isForPlayback)
  → Room → MediaItemsWithStartPosition (migrated URLs, Main transport)
  → skip overwrite if player already seated
  → Media3 prepares + plays when isForPlayback=true
```

## Contracts

- Saved queue exists → resumption must not return empty list.
- Resumption uses local metadata; URL migration matches PlaybackManager; no network on critical path.
- BT cold start seats before / gated for `onGetSession` (brief wait, fail-open).
- If player already has items, restore/resumption skip overwrite.
- `repeatMode` / `shuffleEnabled` applied on Main.
- DI singleton coroutine scopes survive service destroy.
- Empty Room queue → service may start; no crash; no fake media.
- Casting → A2DP resume skipped (ADR-0072).
- Android Auto browse tree still out of scope.

## Device checklist

1. Long deep sleep → BT connect → logcat `ftpmusic-bt` + optional `AudioHardening`.
2. HU shows last track metadata without opening the phone.
3. API ≤36: autoplay starts. API ≥37: Play on HU or tap Resume notif if needed.

## Settings note

Unrestricted battery + notification permission improve deep-sleep A2DP wake
reliability when the OS blocks background FGS starts.
