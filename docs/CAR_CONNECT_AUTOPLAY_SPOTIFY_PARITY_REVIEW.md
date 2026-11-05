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

**Updated 2026-11-05 (ADR-0154):** Symfonium ships **Bluetooth auto play** (app
`play()` on BT connect) plus **Ignore remote stop**. Substreamer is
provider-first without A2DP autoplay. Spotify often appears to autoplay because
many HUs send AVRCP PLAY to the last session (Path A), not because third-party
apps must refuse A2DP `play()`.

FTP Music dual stack:

- **Path A** — Media3 resumption (MediaButton / System UI / HU Play → WIU).
- **Path B** — Opt-in A2DP **Bluetooth auto play** (Symfonium/Poweramp-class);
  seat + `play()` on all API levels when enabled (ADR-0154). ADR-0148 seat-only
  on API ≥37 is superseded for the happy path.

Deep-sleep autoplay still needs FGS start permission (battery unrestricted);
FGS deny → Resume `1002` when notifications work. AudioHardening mute after
`play()` → seated `1001` + `play_suppressed` crumb; HU Play remains Path A.

```
A2DP connect → (Path B ON) seat + play() [autoplay]
             → else Path A when HU/System UI sends Play
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
| F1.4 | Symfonium/Poweramp-class opt-in A2DP autoplay | Path B; default OFF; ADR-0154 |
| F1.5 | Deep-sleep silent autoplay not guaranteed | FGS deny / AudioHardening → seated wait / Resume 1002 |

## Gaps / follow-ups

| ID | Status |
|----|--------|
| G2 Default-music CTA honesty | Shipped — badge-less CTA, no fake “held” status |
| Car-play tip in Bluetooth settings | Shipped — short tip; doze note battery-only |
| G3 `play_suppressed` detector | Shipped — `PlaySuppressionWatch`; diagnostics only |
| G6 stale system-media report | Shipped — refreshed; ADR-0154 autoplay |
| Device validation matrix | Skeleton shipped; human fill (not a code merge gate) |
| Market-report F1 (stale `btAutoplayRequested` after FGS deny) | Fixed — `clearBtAutoplayFlags()` on FGS start failure |
| Market-report F3 (A2DP pause while already playing on API ≥37) | Fixed — `skip_playing` when already audible |
| ADR-0154 Bluetooth auto play (supersede seat_only) | Shipped — mapper + Settings + ignore remote stop |

## Open / deferred (not this ship)

- Media-notif Play WIU under future `targetSdk ≥ 37` — use matrix + `play_suppressed` crumbs; do not bump targetSdk until understood.
- AAOS Car App Library — out of scope.
- Guaranteed deep-sleep zero-gesture autoplay — non-goal.

## Settings education (post-fix copy intent)

- Tip: enable **Bluetooth auto play** to start last queue on A2DP connect; wheel Play if OS blocks start.
- Battery note: unrestricted battery helps FGS after Doze.
- Ignore remote stop: recommended for cars that send STOP on disconnect.
- Default apps: open system settings; no music RoleManager role.
