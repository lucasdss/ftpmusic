# Car BT System Media — Behavior Report

Date: 2026-10-04
Related: ADR-0071, ADR-0019 (FGS), ADR-0032 (lifecycle recovery)

## Scope

Opt-in resume on **user-selected bonded Bluetooth devices** (car profiles).
Phone system surfaces (lock / QS / notif / media buttons). Default music role claim.
**Out:** Android Auto MediaBrowser browse tree.

## Flow

```
A2DP/ACL connect
  → CarBtConnectionReceiver
  → CarBtAutoplayPolicy (enabled + MAC allowlist + debounce + !casting)
  → startForegroundService(ACTION_CAR_BT_AUTOPLAY)
  → MediaService restoreQueue → play()
  → if FGS blocked → high-pri "Resume in car" notif → same action
```

## Settings

| Control | Persist | Default |
|---|---|---|
| Resume on car Bluetooth | `KEY_CAR_BT_RESUME_ENABLED` | false |
| Selected device MACs | `KEY_CAR_BT_DEVICE_MACS` (JSON) | `[]` |
| Set as default music app | OS default-apps Settings deep-link | n/a |

Empty allowlist + toggle ON → armed, no-op until user picks device.

## System surfaces

| Surface | Path | Status |
|---|---|---|
| Lock screen | MediaStyle + session token | Existing; `setSessionActivity` opens app |
| QS / notif | PlaybackNotificationProvider + MediaActionReceiver | Existing |
| BT / headset keys | MediaButtonReceiver → MediaSession | Existing |
| QS tile | PlaybackTileService | Existing |
| Default music app | DefaultMusicRoleHelper | New Settings CTA |
| Android Auto browse | onGetLibraryRoot/children | **Out of scope** |

## Edge cases

| Case | Behavior |
|---|---|
| Headphones (not allowlisted) | Ignore |
| A2DP bounce / ACL spam | 5s debounce per MAC |
| Casting | Skip car resume |
| Empty saved queue | Start service; no crash; no fake play |
| FGS start denied (Doze/API 31+) | Resume notification |
| BT perm denied | Toggle cannot arm; rationale in Settings |
| Process death mid-track | Room restore + play when AUTOPLAY flag |
| Becoming noisy (unplug) | ExoPlayer pause (unchanged) |

## Naming

`carBtResume` / `ACTION_CAR_BT_AUTOPLAY` ≠ Continuous Play autoplay tail (ADR-0053).
