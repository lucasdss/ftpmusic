# Play release 1.4.0

**versionCode:** 6 · **versionName:** 1.4.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Favorites** — local-first sync + thumbs UX; page lists + pending-merge
  race rewrite; loaded-window preserve + residual scroll / reconcile /
  rollback hardening.
- **Library** — cache API artists so list thumbs stick.
- **UI** — market-size interactive reaction icons.
- **Diagnostics** — Cast/queue/skip domain breadcrumbs in Share export
  (ADR-0060); URL scrub + share snapshot off main (no click-path jank).

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

- [ ] Fresh install / upgrade from 1.3.0 (5)
- [ ] Save server URL + Test Connection
- [ ] Library sync completes (Settings → Library Metrics)
- [ ] Artist list thumbs stick after sync / relaunch
- [ ] Favorites Liked | Disliked; scroll + like/dislike races stable
- [ ] Reaction icons market-size on interactive UI
- [ ] Profile / About shows `1.4.0 (6)`
- [ ] Share diagnostics → Cast/queue/skip crumbs present; paste into bug report

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.4.0 (6)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- `IMAGE_DIAGNOSTICS` default-on in production
- Closed-testing 12-tester → production promote
