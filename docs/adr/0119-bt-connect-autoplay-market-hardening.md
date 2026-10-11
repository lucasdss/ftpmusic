# ADR 0119 — BT Connect Autoplay Market Hardening

Date: 2026-10-11
Status: Accepted
Related: ADR-0072, ADR-0087, ADR-0088, ADR-0101, ADR-0117,
`docs/BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md`
Toolchain: compileSdk/targetSdk 36; Android 17 via SDK_INT ≥ 37 gates

## Context

Deep review vs market (Poweramp A2DP resume, Media3 resumption, Spotify-class
media-key routing) showed FTP Music already implements the correct dual path.
Remaining gaps were **user-facing levers** and **field diagnostics**, not a new
playback engine:

1. Settings only *mentioned* unrestricted battery — no CTA.
2. `POST_NOTIFICATIONS` request was fire-and-forget; denied → silent Resume path
   on API ≥37 when FGS also blocked.
3. Share diagnostics lacked a Poweramp-style “last BT outcomes” section.
4. Warm process relied solely on the A2DP broadcast; no `AudioDeviceCallback`.

Silent continuous autoplay from A2DP alone on API ≥37 remains **impossible to
promise** (ADR-0088 WIU / AudioHardening).

## Decision

1. **Battery CTA** — `BatteryOptimizationHelper` deep-links to
   `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (fallback app-details). No
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Play-policy prompt.
2. **Notification prerequisite UX** — on enable, request `POST_NOTIFICATIONS`
   when needed; toast on deny; persistent warning while resume ON and notifs off.
   Resume remains enableable (HU Play / MediaButton still valid).
3. **`BtResumeEvents`** — last 20 outcomes in `DiagnosticLog.snapshot` header
   (no MAC/PII).
4. **`BtWarmAudioDeviceMonitor`** — register `AudioDeviceCallback` while
   `MediaService` alive; dispatch through existing `BtConnectionReceiver` policy
   (debounce shared). Does not replace cold A2DP receiver.
5. Keep ADR-0088 gate: no silent-only background `play()` on API ≥37.

## Consequences

- Users can action battery + notif gaps without logcat.
- Diagnostics export shows recent BT policy/FGS outcomes.
- Warm A2DP adds may double-fire with broadcast; 5s debounce absorbs.
- Android Auto browse tree still out of scope (ADR-0071/0091).
