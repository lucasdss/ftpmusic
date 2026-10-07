# ADR 0087 — Media3 Playback Resumption + BT Eager Restore

Date: 2026-10-07
Status: Accepted
Related: ADR-0072 (Bluetooth A2DP resume), ADR-0019 (FGS), ADR-0032 (lifecycle recovery)
Supplements: ADR-0072 (does not supersede — adds platform resumption path)

## Context

After long deep sleep / process death, Bluetooth A2DP reconnects but car/headset UI
shows “no media” until the user opens the phone app. Root causes:

1. Manifest declares Media3 `MediaButtonReceiver` but `MediaSessionCallback` did not
   implement `onPlaybackResumption` (required by Media3 docs).
2. BT autoplay started `MediaService` then restored the Room queue **asynchronously**,
   so AVRCP/MediaBrowser saw an empty session (symptom: empty on connect, before Play).
3. Post-Doze `ForegroundServiceStartNotAllowedException` on A2DP wake leaves only a
   phone notification the car never sees.

Target: Android 16 (API 36) toolchain; behave correctly on Android 17 devices without
Android-17-only APIs.

## Decision

1. **Implement `onPlaybackResumption`** — rebuild `MediaItemsWithStartPosition` from
   Room via `QueuePersistenceManager` with local metadata only (no network on the
   critical path). Never return an empty list when a saved queue exists.
2. **Eager BT / cold-start seat** — when `btAutoplayRequested` (or empty-player cold
   start), restore queue onto the player as early as safe after ExoPlayer build +
   early `startForeground`. Skip overwrite if resumption already populated items.
3. **Apply transport extras** — restore `repeatMode` / `shuffleEnabled` from
   `queue_state` on resumption and BT restore.
4. **Doze harden** — `BroadcastReceiver.goAsync()` around A2DP dispatch; keep FGS-deny
   notification fallback; structured `ftpmusic-bt` logs.
5. **Settings copy** — note unrestricted battery + notification permission improve
   deep-sleep reliability (no forced battery-exemption dialog).

## Consequences

- Media button / System UI resumption works after process death without opening the app.
- Car AVRCP sees metadata sooner on A2DP wake (eager seat).
- FGS denial after Doze still cannot silently start playback; phone notif remains the
  fallback; resumption covers Play-button path with OS exemption.
- ADR-0072 A2DP opt-in policy unchanged (enabled / any|selected / debounce / casting).
