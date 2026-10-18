# BT Deep-Sleep Media Resume — Behavior Report

Date: 2026-10-09
Related: ADR-0101, ADR-0088, ADR-0087, ADR-0072, ADR-0019, ADR-0043, ADR-0148 (seat-only)
Compat: Android 16 (API 36) toolchain; Android 17 runtime via SDK_INT ≥ 37 guards

## Problem

Long deep sleep → process death → Bluetooth audio reconnects → head unit shows
**no media** until user opens FTP Music. Observed with Bluetooth resume **ON /
selected** (allowlist filled) on Android 17 — sometimes with **no phone Resume
notification**. Separately: resume starts off home LAN then dies mid-track when
the current item is not fully cached.

## Causes

| Cause | Effect |
|---|---|
| No `onPlaybackResumption` (fixed ADR-0087) | MediaButtonReceiver wakes empty playlist |
| Async Room restore before seat (hardened ADR-0088) | Car binds before items seated → “no media” |
| Post-Doze FGS deny on A2DP | Service never starts; phone Resume notif |
| Android 17 BFSL FGS without WIU | `play()` silently silenced (`AudioHardening`) |
| SELECTED + null MAC (CONNECT deny) (ADR-0101) | Policy hard-skip before starter → **total silence** |
| Empty Room on BT seat (ADR-0148) | Flag cleared; info notif `1003` (open app) |
| `POST_NOTIFICATIONS` denied | `notify()` no-op; seat still required for HU Play |
| Off-LAN + uncached current (ADR-0101) | Fail-fast IOException → stop / skip cascade |

## Flows

### A2DP connect (ADR-0072 + ADR-0088 sync seat + ADR-0101)

```
A2DP STATE_CONNECTED
  → BtConnectionReceiver (goAsync)
  → BtResumePolicy
       ANY: null MAC → "*"
       SELECTED + null MAC → Resume notif only (no silent skip)
       SELECTED + MAC → allowlist gate
  → startForegroundService(ACTION_BT_AUTOPLAY)
  → MediaService early startForeground
  → sync Room restore → seat MediaItems
  → if unreachable/offline: resolveOfflineStartIndex (prefer full cache)
       none cached → cancel Resume 1002 + info notif 1003; no doomed play()
  → API ≤36: play() + cancel Resume 1002
  → API ≥37: seat_only pause + cancel Resume 1002 (ADR-0148); HU / media-notif Play
  → if FGS blocked → high-pri Resume notif 1002
  → empty Room → cancel 1002 + info notif 1003 (open app)
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
- Empty Room queue → info notif `1003`; cancel stale Resume `1002`; no crash; no fake media.
- Casting → A2DP resume skipped (ADR-0072).
- SELECTED + unreadable MAC → Resume notif (ADR-0101); no autoplay without MAC match.
- Off-LAN BT seat prefers fully cached index; partial cache ≠ playable.
- Android Auto browse tree still out of scope.

## Device checklist

1. Long deep sleep → BT connect → logcat `ftpmusic-bt` + optional `AudioHardening`.
2. HU shows last track metadata without opening the phone.
3. API ≤36: autoplay starts. API ≥37: seat-only; Play on HU / media notif (no happy-path Resume).
4. SELECTED without CONNECT: phone shows Resume notif (not silence).
5. Off LAN with some downloads: starts on first fully cached track at/after current.
6. Off LAN with zero downloads: info `1003`; stale Resume cancelled; no play-then-die.

## Settings note

Unrestricted battery + notification permission improve deep-sleep A2DP wake
reliability when the OS blocks background FGS starts. Bluetooth CONNECT is
required to match SELECTED allowlist MACs.
