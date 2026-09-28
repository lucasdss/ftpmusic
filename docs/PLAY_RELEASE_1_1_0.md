# Play release 1.1.0

**versionCode:** 3 · **versionName:** 1.1.0

Ship the same signed AAB to **internal/closed testing first**, then promote to
production when green. Do not upload from CI unless an existing script is
already wired — build locally and upload in Play Console.

## Changelog

- **Settings hydration** — preferences restore correctly after process death
  (Last.fm key, sync interval, home section toggles, Wi‑Fi download gate).
- **Library delta sync** — adaptive DELTA + periodic FULL (ADR-0045); metrics
  show last full / last delta.
- **Profile listen metrics honesty** — `listen_events` with calendar week,
  honest minutes excluding synthetic backfill, STATE_ENDED scrobble (ADR-0046 /
  ADR-0047).
- **In-app Diagnostics** — ring buffer + Share / Clear in Settings (ADR-0048).

## Build artifact

```bash
cd compose
./gradlew :app:bundleRelease
# Optional full APK check:
./gradlew :app:assembleRelease
```

Signed output (when `keystore.properties` is present):

`compose/app/build/outputs/bundle/release/app-release.aab`

Upload that AAB to Play Console → Testing track → Production when OK.

## Tester checklist

- [ ] Fresh install / upgrade from 1.0.x
- [ ] Save server URL + Test Connection
- [ ] Library sync completes (watch Settings → Library Metrics)
- [ ] Play a track to completion; Profile shows updated plays / minutes
- [ ] Toggle Simulate Offline; playback from cache still works
- [ ] Settings → About / Diagnostics → **Share diagnostics** → paste into a bug
- [ ] **Clear log** empties the buffer (share again → header only / empty body)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.1.0 (3)`
4. Tap **Share diagnostics** and send the text (email / Drive / chat)
5. Attach that text to the Play feedback or GitHub issue — no passwords included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- `IMAGE_DIAGNOSTICS` default-on in production
