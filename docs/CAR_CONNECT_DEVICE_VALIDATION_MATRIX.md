# Car Connect — Device Validation Matrix

Date: 2026-11-02  
Status: Skeleton (human QA fills runs; **not** a code merge gate for targetSdk 36)  
Related: [CAR_CONNECT_AUTOPLAY_SPOTIFY_PARITY_REVIEW.md](CAR_CONNECT_AUTOPLAY_SPOTIFY_PARITY_REVIEW.md),
[ADR-0148](adr/0148-bt-autoplay-market-parity.md)

## Gates

| Gate | Rule |
|------|------|
| This ship (docs / Settings / `play_suppressed`) | **Not** blocked by unfilled cells |
| Future `targetSdk ≥ 37` bump | Blocked until D37T media-notif Play cell understood |
| Missing API 37 hardware | Document “unavailable”; does not block current targetSdk 36 |

## Devices (fill model / build)

| ID | Device | API | APK targetSdk | Notes |
|----|--------|-----|---------------|-------|
| D34 | | 34 | 36 | Path B autoplay baseline |
| D36 | | 36 | 36 | Pre-A17 seat+play |
| D37 | | ≥37 | 36 | seat_only; HU WIU |
| D37T | | ≥37 | 37 (future) | V4 media-notif risk |

## Connect modes

| Mode | Setup |
|------|--------|
| A2DP classic | Car/speaker; AA unused; BT resume ON + SELECTED car MAC |
| AA wireless | Wireless Android Auto; record “Start music automatically” ON/OFF |
| AA USB (optional) | Browse regression; same play policy as wireless |

## Pass criteria (happy path)

| API | Mode | Expected crumbs | Audible w/o gesture | After HU/AA Play | Resume 1002 |
|-----|------|-----------------|---------------------|------------------|-------------|
| 34/36 | A2DP | `allow`→`Started`→`autoplay` | Yes if FGS ok | Yes | No |
| ≥37 | A2DP | `allow`→`Started`→`seat_only` | **No** | **Yes** | No |
| Any | FGS deny | `NotificationFallback` | No until tap | After tap/HU | Yes |
| Any | AA alone | Path B optional | **No** from browse | Yes if AA auto-resume | No |
| ≥37 targetSdk 37 | media-notif Play | maybe `play_suppressed` | Record | HU should work | — |

Negative (expected, not fail): last media app was Spotify → HU may open Spotify.

## Run template

```
### Run YYYY-MM-DD — <Dx> / <mode> / cold|warm
- APK: versionName / versionCode / targetSdk
- Phone: model / fingerprint
- Car/HU: make; AVRCP play-on-connect Y/N
- Battery unrestricted: Y/N
- AA Start music automatically: Y/N/n/a
- Crumbs:
- Audible before gesture: Y/N
- Audible after HU Play: Y/N
- Audible after media-notif Play: Y/N
- play_suppressed: Y/N
- Pass?: Y/N + notes
```

## Filled runs

_(none yet — attach Share diagnostics under `btResumeEvents=`)_
