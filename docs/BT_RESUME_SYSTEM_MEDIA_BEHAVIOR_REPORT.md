# Bluetooth Resume — Behavior Report

Date: 2026-11-02 (refreshed; original 2026-10-07)  
Status: Living — aligned with ADR-0148 seat_only  
Related: ADR-0072 (supersedes ADR-0071), ADR-0087 (Media3 resumption + eager seat),
ADR-0148, ADR-0019 (FGS),
[CAR_CONNECT_AUTOPLAY_SPOTIFY_PARITY_REVIEW.md](CAR_CONNECT_AUTOPLAY_SPOTIFY_PARITY_REVIEW.md)

## Scope

Opt-in resume on **A2DP audio** connect. User picks mode:

- **Any audio device** — all A2DP sinks
- **Selected devices** — bonded MAC allowlist only

Phone system surfaces (lock / QS / notif / media buttons) use Media3
`onPlaybackResumption` after process death (ADR-0087).

Android Auto browse = **separate surface** (ADR-0091 / ADR-0146); not A2DP Path B.
**Out:** ACL-only (non-audio) connects; AAOS Car App Library.

## Flow

```
A2DP STATE_CONNECTED
  → BtConnectionReceiver / BtWarmAudioDeviceMonitor
  → BtResumePolicy (enabled + mode + debounce + !casting)
  → startForegroundService(ACTION_BT_AUTOPLAY)
  → MediaService early FGS + eager Room seat (playWhenReady=false)
  → applyBtPlayAfterSeat:
       already playing + background → skip (no pause) — market no-op
       sdk < 37 OR userInitiated → play() + cancel Resume 1002  (autoplay|tap_play)
       sdk ≥ 37 background       → pause() + cancel Resume 1002 (seat_only)
  → empty Room / offline-no-cache → info notif 1003 (open app)
  → SELECTED+null MAC / FGS deny → actionable Resume 1002
       (FGS deny clears btAutoplayRequested so later app opens do not autoplay)

MEDIA_BUTTON / System UI / HU AVRCP PLAY
  → MediaButtonReceiver
  → onPlaybackResumption → Room MediaItemsWithStartPosition → play (WIU)
```

## Settings

| Control | Persist | Default |
|---|---|---|
| Resume on Bluetooth | `KEY_BT_RESUME_ENABLED` (fallback `KEY_CAR_BT_*`) | false |
| Mode | `KEY_BT_RESUME_MODE` (`any`/`selected`) | `selected` |
| Selected MACs | `KEY_BT_DEVICE_MACS` (fallback legacy) | `[]` |
| Default apps deep-link | OS Default apps / app details | n/a (no ROLE_MUSIC) |

Selected + empty allowlist → armed, no-op until user picks devices.

Deep-sleep reliability improves with unrestricted battery + notification permission
(see Settings Bluetooth tip + battery CTA).

## Edge cases

| Case | Behavior |
|---|---|
| Watch / non-A2DP ACL | Ignore (A2DP-only trigger) |
| Mode any + headphones | Path B seat / play gate |
| Mode selected + wrong MAC | Ignore |
| A2DP bounce | 5s debounce per MAC |
| Casting | Skip |
| Already playing (API ≥37) | Skip Path B — do not pause |
| Empty saved queue | Info notif 1003 |
| Offline, no cached track | Info notif 1003 |
| FGS denied | Resume 1002; clear static BT autoplay flags |
| API ≥37 happy path | seat_only; no app Resume chip |
| Legacy car-BT prefs | Read fallback; new writes use KEY_BT_* |
| Process death + car bind | Eager seat / onPlaybackResumption |
| Resumption already filled player | BT restore skips overwrite |
| Muted play after play() | Diagnostics `play_suppressed` (no user notif) |
