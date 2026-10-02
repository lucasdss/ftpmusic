# Play release 1.4.1

**versionCode:** 7 · **versionName:** 1.4.1

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Rating surfaces** — strip 5★ from track lists (Album / Artist / Mix /
  Playlist / Queue) so titles are not truncated; track stars remain in
  expanded full player only.
- **Library Albums** — thumbs under art, above album name; no grid stars.
- **Album detail** — album 5★ sits just below hero art (`rateAlbum`).

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

- [ ] Upgrade from 1.4.0 (6) (or fresh install)
- [ ] Profile / About shows `1.4.1 (7)`
- [ ] Album / Artist / Mix track rows: long titles not squeezed by stars
- [ ] Track rows: thumbs still work; no 5★ beside title
- [ ] Full player: track 5★ rate still works
- [ ] Library Albums: thumbs below art, above name; no stars in grid
- [ ] Album detail: 5★ under cover art; rate album works
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.4.1 (7)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
