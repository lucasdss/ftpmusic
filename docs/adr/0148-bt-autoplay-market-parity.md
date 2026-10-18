# ADR 0148 — BT Autoplay Market Parity: Seat-Only on Android 17

Date: 2026-10-18
Status: Accepted
Supersedes (in part): ADR-0088 (API ≥37 high-pri Resume notif), ADR-0101 (empty/offline Resume notif), ADR-0117 (pause + Resume notif on API ≥37), ADR-0119 (Resume-prompt warning copy), ADR-0133 (happy-path WIU notif)
Related: ADR-0072 (A2DP resume), ADR-0087 (Media3 resumption), ADR-0100 (null-mac)
Toolchain: compileSdk/targetSdk 36; Android 17 via SDK_INT ≥ 37 guards

## Context

On Android 17 (API ≥37), after a Bluetooth A2DP connect, the app seated the last
queue and then posted an app-specific "Resume playback" notification. Market apps
(Spotify / YouTube Music / Apple Music / Synfonium) do not do this: they seat the
MediaSession, stay paused, and let the OS / head unit initiate playback.

"Auto-play in deep sleep with zero gesture" is **not reliable** on Android 17: an
A2DP-started background foreground service (BFSL) lacks while-in-use (WIU)
capabilities, so `play()` is silently muted (`AudioHardening` in logcat). Market
apps do not depend on app-side A2DP `play()` for the connect gesture — the head
unit sends AVRCP PLAY (a media-button event), the OS routes it to the last active
media session via `MediaButtonReceiver` → `onPlaybackResumption`, granting an FGS
+ WIU exemption. That path already exists in FTP Music (ADR-0087).

## Decision

1. **Seat-only on API ≥37.** `applyBtPlayAfterSeat` no longer posts the app Resume
   notification on the happy path; it seats + pauses and cancels any stale Resume
   (ID 1002) left by a prior `selected_null_mac` connect.
2. **Empty queue / offline-no-cache → informational notice.** New
   `BtAutoplayStarter.postInformationalNotification` (ID 1003, DEFAULT-priority
   channel `ftpmusic_bt_info`) with a content intent that opens the app; no
   "Resume" action. Avoids the ADR-0101 "total silence" regression.
3. **`selected_null_mac` and FGS-denied keep the actionable Resume (ID 1002).**
   Both lack a seated session, so a WIU tap is the only phone-side resume;
   informational there would regress ADR-0101/0133.
4. **Telemetry:** `wiu` → `seat_only` across `Log` / `DiagnosticLog` /
   `BtResumeEvents`.
5. **Primary resume path** = head-unit / System UI MediaButton →
   `onPlaybackResumption`; **secondary** = media-notification Play (OK under
   `targetSdk 36`; unproven under `targetSdk ≥ 37`). Deferred to device validation.

## Consequences

- API ≥37 A2DP connect now matches market behavior (seat + paused media notification).
- Dual-notification invariant: the media FGS notification (ID 1001) remains; only
  the app Resume (ID 1002) disappears on the happy path.
- `selected_null_mac` / FGS-denied users retain a tappable Resume (WIU).
- No change to Path A (`MediaButtonReceiver` / `onPlaybackResumption`), API ≤36
  autoplay, `BtResumePolicy`, debounce, or casting.
- `PlaybackResumptionMapper.shouldBackgroundAutoplay` / `shouldPlayAfterBtSeat`
  gates unchanged; `userInitiated` still drives the FGS-deny + null-mac tap path.
- Settings copy updated for the seat-only reality.
