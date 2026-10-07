# Bluetooth Resume — Behavior Report

Date: 2026-10-07
Related: ADR-0072 (supersedes ADR-0071), ADR-0087 (Media3 resumption + eager seat), ADR-0019 (FGS)

## Scope

Opt-in resume on **A2DP audio** connect. User picks mode:
- **Any audio device** — all A2DP sinks
- **Selected devices** — bonded MAC allowlist only

Phone system surfaces (lock / QS / notif / media buttons) use Media3
`onPlaybackResumption` after process death (ADR-0087).
**Out:** Android Auto browse tree; ACL-only (non-audio) connects.

## Flow

```
A2DP STATE_CONNECTED
  → BtConnectionReceiver (goAsync)
  → BtResumePolicy (enabled + mode + debounce + !casting)
  → startForegroundService(ACTION_BT_AUTOPLAY)
  → MediaService early startForeground + eager Room seat → play()
  → if FGS blocked → high-pri "Resume playback" notif

MEDIA_BUTTON / System UI resume
  → MediaButtonReceiver
  → onPlaybackResumption → Room MediaItemsWithStartPosition
```

## Settings

| Control | Persist | Default |
|---|---|---|
| Resume on Bluetooth | `KEY_BT_RESUME_ENABLED` (fallback `KEY_CAR_BT_*`) | false |
| Mode | `KEY_BT_RESUME_MODE` (`any`/`selected`) | `selected` |
| Selected MACs | `KEY_BT_DEVICE_MACS` (fallback legacy) | `[]` |
| Default music app | OS default-apps Settings | n/a |

Selected + empty allowlist → armed, no-op until user picks devices.

Deep-sleep reliability improves with unrestricted battery + notification permission
(see Settings Bluetooth section note).

## Edge cases

| Case | Behavior |
|---|---|
| Watch / non-A2DP ACL | Ignore (A2DP-only trigger) |
| Mode any + headphones | Resume |
| Mode selected + wrong MAC | Ignore |
| A2DP bounce | 5s debounce per MAC |
| Casting | Skip |
| Empty saved queue | Start service; no crash |
| FGS denied | Resume notification |
| Legacy car-BT prefs | Read fallback; new writes use KEY_BT_* |
| Process death + car bind | Eager seat / onPlaybackResumption — avoid empty session race |
| Resumption already filled player | BT restore skips overwrite |
