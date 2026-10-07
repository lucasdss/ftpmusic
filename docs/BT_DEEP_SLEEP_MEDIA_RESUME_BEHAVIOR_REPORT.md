# BT Deep-Sleep Media Resume — Behavior Report

Date: 2026-10-07
Related: ADR-0087, ADR-0072, ADR-0019
Compat: Android 16 (API 36) toolchain; Android 17 devices via Media3 + SDK_INT guards only

## Problem

Long deep sleep → process death → Bluetooth audio reconnects → head unit shows
**no media** until user opens FTP Music. Observed with Bluetooth resume **ON / any**.

## Causes

| Cause | Effect |
|---|---|
| No `onPlaybackResumption` | MediaButtonReceiver wakes empty playlist |
| Async Room restore after `onCreate` | Car binds before items seated → “no media” |
| Post-Doze FGS deny on A2DP | Service never starts; car never sees session |

## Flows

### A2DP connect (ADR-0072 + eager seat)

```
A2DP STATE_CONNECTED
  → BtConnectionReceiver (goAsync)
  → BtResumePolicy
  → startForegroundService(ACTION_BT_AUTOPLAY)
  → MediaService early startForeground
  → eager Room restore → seat MediaItems + play()
  → if FGS blocked → high-pri “Resume playback” notif
```

### Media button / System UI resumption (ADR-0087)

```
MEDIA_BUTTON or System UI resume
  → Media3 MediaButtonReceiver (FGS exemption for BT play)
  → MediaSessionService
  → onPlaybackResumption(isForPlayback)
  → Room → MediaItemsWithStartPosition (local metadata)
  → Media3 prepares + plays when isForPlayback=true
```

## Contracts

- Saved queue exists → resumption must not return empty list.
- Resumption uses local title/artist/album/artworkUri; no network on critical path.
- If resumption already filled the player, BT async restore skips overwrite.
- `repeatMode` / `shuffleEnabled` from `queue_state` applied on resume paths.
- Empty Room queue → service may start; no crash; no fake media.
- Casting → A2DP resume skipped (ADR-0072).
- Android Auto browse tree still out of scope.

## Settings note

Unrestricted battery + notification permission improve deep-sleep A2DP wake
reliability when the OS blocks background FGS starts.
