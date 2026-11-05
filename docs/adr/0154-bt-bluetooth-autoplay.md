# ADR 0154 — Bluetooth Auto Play (Symfonium-class Path B)

Date: 2026-11-05
Status: Accepted
Supersedes (in part): ADR-0148 (API ≥37 happy-path seat-only / no app `play()`)
Related: ADR-0072 (A2DP resume), ADR-0087 (Media3 resumption), ADR-0101
(empty/offline), ADR-0133 (Resume-tap WIU), ADR-0119 (warm A2DP monitor)
Toolchain: compileSdk/targetSdk 36; runtime SDK_INT ≥ 37 still observed

## Context

ADR-0148 assumed market apps (Spotify / YTM / Apple / Symfonium) only seat a
paused MediaSession on Bluetooth connect and wait for head-unit Play. Device
validation and peer research contradict that for **Symfonium**: it ships an
explicit **Bluetooth auto play** setting that starts playback when a Bluetooth
audio sink connects, plus **Ignore remote stop** so cars that send STOP on
disconnect do not wipe the queue.

**Substreamer** (via `react-native-queue-player`) does **not** autoplay on A2DP
connect; its strength is staying a discoverable media provider
(`MediaLibraryService` + legacy `MediaBrowserService` + `MediaButtonReceiver`).
FTP Music already exports those surfaces.

User goal when Path B is enabled: **always** become the media provider on A2DP
connect and **always** call `play()` after a successful seat. Wait for Play only
when autoplay is impossible (empty/offline queue, FGS deny, casting, already
playing, or post-`play()` AudioHardening mute).

Android 17 AudioHardening can still mute background `play()` without WIU; that
is mitigated by seating the session + media FGS notif `1001` and
`PlaySuppressionWatch`, not by refusing to attempt play.

## Decision

1. **Path B autoplay on all API levels** when Bluetooth resume is enabled.
   `PlaybackResumptionMapper.shouldBackgroundAutoplay` returns `true` regardless
   of `sdkInt`. `applyBtPlayAfterSeat` calls `play()` after seat (telemetry
   `autoplay` / `tap_play`); happy-path `seat_only` pause is removed.
2. **Keep opt-in.** Feature remains off by default (Symfonium/Poweramp-class).
3. **Wait-for-Play only when autoplay is impossible:** empty queue / offline
   no-cache (`1003`), FGS start denied (`1002` Resume tap), casting skip,
   already playing (`skip_playing`), or `play_suppressed` after attempt (leave
   seated `1001`).
4. **Ignore remote stop (opt-in):** treat external `COMMAND_STOP` /
   `KEYCODE_MEDIA_STOP` as `pause()` so the queue survives car disconnect STOP.
   Default **off**; Settings recommends enabling for car users.
5. **Telemetry:** record `source=warm|cold` on Path B crumbs; retain
   `autoplay` / `skip_playing` / fallback tokens. `seat_only` remains only as a
   historical crumb name (no longer emitted on the happy path).
6. **Unchanged:** Path A MediaButton / `onPlaybackResumption`; SELECTED null-MAC
   and FGS-deny Resume `1002`; empty/offline info `1003`; casting skip; debounce.

## Consequences

- API ≥37 A2DP connect with BT resume ON matches Symfonium Bluetooth auto play
  intent (seat + play), not ADR-0148 seat-only.
- Settings copy: “Bluetooth auto play”; tip covers battery + notifications for
  deep-sleep wake and wheel Play when OS blocks autoplay.
- Future `targetSdk ≥ 37` may tighten WIU; `PlaySuppressionWatch` + seated
  session remain the recovery surface. Do not bump targetSdk in this change.
- ADR-0148 empty/offline info notices and null-mac / FGS-deny Resume paths stay.
