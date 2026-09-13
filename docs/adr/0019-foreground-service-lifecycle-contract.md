# ADR-0019: Foreground Service Lifecycle Contract

## Status

Accepted. Revised 2026-08-31 for Google Play release.

## Context

Playback needs a `mediaPlayback` foreground service. Starting that service on
every app launch created an idle persistent notification and claimed foreground
execution before any user playback request.

Calling `startForegroundService()` also creates a five-second deadline. Heavy
player and Cast initialization can consume that window.

Android's six-hour Android 15 timeout applies to `dataSync` and
`mediaProcessing`, not `mediaPlayback`. Restarting playback service to manufacture
new timeout windows is unnecessary and policy-hostile.

## Decision

1. App launch sends a normal `ACTION_INITIALIZE` start. This pre-warms playback
   without foreground promotion or notification.
2. User playback sends `ACTION_PLAYBACK` through `startForegroundService()`.
3. Playback code marks foreground intent before service creation. `onCreate`
   posts the placeholder before heavy initialization when that marker exists.
4. An already initialized service reasserts foreground only for
   `ACTION_PLAYBACK`.
5. Initialization is `START_NOT_STICKY`; active playback remains `START_STICKY`.
6. Notification permission is requested from the Settings playback-notification
   control, not automatically at cold launch.
7. No artificial foreground-service timeout restart exists.

## Consequences

- No idle foreground service during ordinary browsing.
- Playback retains five-second deadline protection.
- Process-restored playback remains restartable.
- First playback remains safe because app launch pre-warms the player; a killed
  service receives the explicit playback marker before recreation.
- Play Console must declare `mediaPlayback` and provide a playback demonstration
  video.
