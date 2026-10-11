# BT Connect + Auto-Play — Market Behavior Report

Date: 2026-10-11  
Status: research + hardening shipped (ADR-0119)  
Related: [ADR-0119](adr/0119-bt-connect-autoplay-market-hardening.md),
[ADR-0088](adr/0088-bt-resume-correctness-and-android17-wiu.md),
[ADR-0101](adr/0101-bt-a17-silent-wake-and-offlan-cache-seat.md),
[ADR-0087](adr/0087-media3-playback-resumption-and-bt-eager-restore.md),
[ADR-0072](adr/0072-bluetooth-resume-any-or-selected.md),
[BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md](BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md)

## Verdict (Caveman)

FTP Music stack = Poweramp-style A2DP opt-in resume + Google Media3
resumption. Deep-sleep fail mostly OS policy (FGS deny, API≥37 WIU/BFSL),
not missing ExoPlayer API. Silent autoplay after sleep with zero gesture
**not** guaranteed by Android for third-party apps.

## Market patterns

| Pattern | Who | Deep-sleep role |
|---|---|---|
| MediaButton + `onPlaybackResumption` | Google Media3; Spotify-class | Primary cold wake; BT Play → FGS exemption |
| Opt-in resume on BT connect | Poweramp “Resume on Bluetooth” | A2DP broadcast → start player; off by default |
| HU AVRCP MEDIA_PLAY on connect | Many car HUs / ROMs | Works even if app resume-off; routes to last session |
| Stay last active media session | Spotify / YTM | System media keys prefer last player |
| Unrestricted battery | Poweramp guidance | Restores FGS-from-background exemption |
| Android Auto browse tree | Spotify / YTM | Separate surface; not classic A2DP autoplay |

## OS constraints (must respect)

1. **API ≥31 FGS background start** — denied → `ForegroundServiceStartNotAllowedException`
   unless exemption (notif tap, media button, battery unrestricted, etc.).
2. **API ≥37 WIU / AudioHardening** — A2DP-started FGS often BFSL without WIU;
   background `play()` may silence. App gate: seat + Resume notif / HU Play.
3. **API ≥15 BOOT_COMPLETED** — cannot start `mediaPlayback` FGS from boot receiver.
4. **BAL** — no Activity launch from A2DP MAC alone.
5. **A2DP broadcast** — best-effort accelerator; not a Play-guaranteed autoplay API.

## Must-have permissions

| Permission | Role |
|---|---|
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | MediaSession FGS |
| `POST_NOTIFICATIONS` (runtime 33+) | Resume notif when silent play / FGS blocked |
| `WAKE_LOCK` | ExoPlayer network wake |
| `BLUETOOTH` (≤30) + `BLUETOOTH_CONNECT` (31+) | SELECTED allowlist MAC |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Stream / offline gate |

**Do not add for this feature:** `USE_FULL_SCREEN_INTENT`, Companion Device /
`connectedDevice` FGS, `SYSTEM_ALERT_WINDOW`, boot→mediaPlayback FGS.

## Must-use APIs

- `MediaSessionService` + `foregroundServiceType=mediaPlayback`
- `MediaButtonReceiver` + `MediaSession.Callback.onPlaybackResumption`
- Manifest A2DP `CONNECTION_STATE_CHANGED` → `startForegroundService`
- Early `startForeground` within start deadline
- High-pri notif `PendingIntent.getForegroundService` (WIU)
- Room-persisted queue + local metadata on resumption path
- Warm-only: `AudioManager.AudioDeviceCallback` (process alive)

## FTP Music call graph

```
A2DP CONNECTED → BtConnectionReceiver → BtResumePolicy
  → BtAutoplayStarter.startForegroundService(ACTION_BT_AUTOPLAY)
  → MediaService seat (playWhenReady=false)
  → SDK <37: play() | SDK ≥37: Resume notif
FGS deny → Resume notif
MEDIA_BUTTON → MediaButtonReceiver → onPlaybackResumption → play (WIU)
Warm service → AudioDeviceCallback A2DP add → same policy path
```

## Hardening shipped (ADR-0119)

1. Settings **Unrestricted battery** deep-link CTA + status.
2. Notification warning when resume ON and notifs disabled; toast on deny.
3. `BtResumeEvents` last-20 outcomes in Share diagnostics header.
4. Warm `BtWarmAudioDeviceMonitor` while MediaService alive.

## Device checklist

1. Long deep sleep → A2DP → logcat `ftpmusic-bt` + diagnostics `btResumeEvents`.
2. API ≤36: autoplay after seat. API ≥37: Resume notif or HU Play.
3. Battery unrestricted improves FGS start after Doze.
4. Notifs off → warning in Settings; HU Play still viable if seat succeeded.
5. SELECTED without CONNECT → Resume notif (`selected_null_mac`), no silent skip.
