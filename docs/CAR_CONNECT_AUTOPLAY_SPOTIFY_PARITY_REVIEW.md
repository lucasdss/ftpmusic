# Car Connect Autoplay — Spotify vs FTP Music Parity Review

Date: 2026-11-02  
Status: Accepted (docs + Settings/instrumentation follow-ups)  
Loop: `.opencode` android-orchestrator Phase 1–2 + challenge amendments  
Related: [ADR-0072](adr/0072-bluetooth-resume-any-or-selected.md),
[ADR-0087](adr/0087-media3-playback-resumption-and-bt-eager-restore.md),
[ADR-0146](adr/0146-android-auto-car-media-declaration.md),
[ADR-0148](adr/0148-bt-autoplay-market-parity.md),
[BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md](BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md),
[BT_RESUME_SYSTEM_MEDIA_BEHAVIOR_REPORT.md](BT_RESUME_SYSTEM_MEDIA_BEHAVIOR_REPORT.md),
[CAR_CONNECT_DEVICE_VALIDATION_MATRIX.md](CAR_CONNECT_DEVICE_VALIDATION_MATRIX.md)

## Verdict

Spotify-class car play is **not** app-initiated A2DP `play()`. Market behavior:

1. Keep a live / resumable MediaSession (last played app wins soft routing).
2. On reconnect, seat metadata/queue paused.
3. Head unit AVRCP PLAY, Android Auto “Start music automatically”, or System UI media Play starts audio.
4. OS routes that through `MediaButtonReceiver` → `onPlaybackResumption` with a while-in-use (WIU) exemption.

FTP Music dual stack:

- **Path A** — Media3 resumption (Spotify-class).
- **Path B** — Opt-in A2DP seat (Poweramp-class); API ≥37 **seat_only** (ADR-0148).

Zero-gesture deep-sleep autoplay from A2DP alone is **not** a third-party market guarantee after Android 17 AudioHardening.

```
A2DP connect → (optional Path B) seat paused
HU / AA / System UI PLAY → Path A / media button → audible play (WIU)
```

## Permissions (mapped vs declared)

| Permission | Market role | FTP Music |
|------------|-------------|-----------|
| `FOREGROUND_SERVICE` + `_MEDIA_PLAYBACK` | MediaSession FGS | Declared + used |
| `POST_NOTIFICATIONS` | Resume/info when FGS blocked | Declared + runtime on BT enable |
| `WAKE_LOCK` | Player wake | Declared |
| `BLUETOOTH` ≤30 + `BLUETOOTH_CONNECT` | SELECTED MAC allowlist | Declared + runtime |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Stream / offline | Declared |

**No permission gap** for Spotify-parity car play. Do not add Companion / SAW / full-screen intent / boot→media FGS / `BLUETOOTH_SCAN` for this feature.

`ROLE_MUSIC` does not exist on current SDKs — Settings deep-links to Default apps only (soft routing).

## Findings

| ID | Finding | Reasoning |
|----|---------|-----------|
| F1.1 | Play is HU/OS-initiated | AVRCP + AA auto-resume; Spotify cannot always block accessory PLAY |
| F1.2 | App job = last session + resumption | Soft routing prefers last MediaSession |
| F1.3 | Must not auto-start on Auto browse connect | Android for Cars policy |
| F1.4 | Poweramp-class opt-in A2DP seat | Path B; default OFF |
| F1.5 | Deep-sleep silent autoplay ≠ market promise | BFSL without WIU → AudioHardening mute |

## Gaps / follow-ups

| ID | Status |
|----|--------|
| G2 Default-music CTA honesty | Shipped — badge-less CTA, no fake “held” status |
| Car-play tip in Bluetooth settings | Shipped — short tip; doze note battery-only |
| G3 `play_suppressed` detector | Shipped — `PlaySuppressionWatch`; diagnostics only |
| G6 stale system-media report | Shipped — refreshed for seat_only |
| Device validation matrix | Skeleton shipped; human fill (not a code merge gate) |
| Market-report F1 (stale `btAutoplayRequested` after FGS deny) | Fixed — `clearBtAutoplayFlags()` on FGS start failure |
| Market-report F3 (A2DP pause while already playing on API ≥37) | Fixed — `skip_playing` when already audible |

## Open / deferred (not this ship)

- Media-notif Play WIU under future `targetSdk ≥ 37` — use matrix + `play_suppressed` crumbs; do not bump targetSdk until understood.
- AAOS Car App Library — out of scope.
- Guaranteed deep-sleep zero-gesture autoplay — non-goal.

## Settings education (post-fix copy intent)

- Tip: car play = HU / Android Auto Play to last media app; enable BT resume to **seat**.
- Battery note: unrestricted battery helps FGS after Doze.
- Default apps: open system settings; no music RoleManager role.
