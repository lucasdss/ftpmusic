# Play release 1.2.0

**versionCode:** 4 · **versionName:** 1.2.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Offline / local-only playback** — multi-network loss revalidation; airplane
  mode gated like local-only playable search (ADR-0051).
- **Lyrics** — TTL restore; hardened on-demand fetch/cache path (ADR-0050).
- **UI fit & a11y** — FittingText for constrained slots; honor system
  fontScale; hide nav labels when needed (ADR-0049).
- **Profile StatCards** — weight-bounded FittingText so listening stats stay
  visible under Robolectric / narrow layouts.

## Build artifact

```bash
cd compose
./gradlew :app:bundleRelease
# Optional full APK check:
./gradlew :app:assembleRelease
```

Signed output (when `keystore.properties` is present):

`compose/app/build/outputs/bundle/release/app-release.aab`

Upload that AAB to Play Console → Internal testing → promote when OK.

## Pre-upload gates

```bash
make test
cd compose && ./gradlew :app:lintVitalRelease
cd compose && ./gradlew :app:bundleRelease
```

## Tester checklist

- [ ] Fresh install / upgrade from 1.1.0 (3)
- [ ] Save server URL + Test Connection
- [ ] Library sync completes (Settings → Library Metrics)
- [ ] Toggle airplane / Simulate Offline; local-only playable search still works
- [ ] Open lyrics for a track; second open uses cache
- [ ] Large system fontScale: Profile chips / nav still readable
- [ ] Profile shows `1.2.0 (4)` in Settings → About / Diagnostics
- [ ] Share diagnostics → paste into bug report

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.2.0 (4)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- `IMAGE_DIAGNOSTICS` default-on in production
