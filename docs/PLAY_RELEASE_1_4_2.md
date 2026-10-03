# Play release 1.4.2

**versionCode:** 8 · **versionName:** 1.4.2

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Collection play** — tapping a track in Album / Artist / Mix / Favorites /
  Playlist plays the full collection starting at that track (not a one-shot).
- **Favorites unlike** — second-tap unlike sticks; local-first toggle + Room
  reconcile no longer restores a stale like.
- **Tab reselect** — tapping the active bottom-nav tab pops that tab stack to
  root (same-tab reselect).
- **Settings back** — Settings uses `DetailBackButton`; dismiss affordances
  match the shared navigation taxonomy.
- **Cast / nav harden** — Cast session resume policy + Today tab stack restore
  edge cases; coverage gaps closed.

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

- [ ] Upgrade from 1.4.1 (7) (or fresh install)
- [ ] Profile / About shows `1.4.2 (8)`
- [ ] Album / Artist / Mix / Favorites: tap mid-list track → full collection plays from that index
- [ ] Favorites: like then unlike → stays unliked after scroll / revisit
- [ ] Bottom nav: open detail, tap same tab again → returns to tab root
- [ ] Settings: back uses DetailBackButton; dismiss matches other detail screens
- [ ] Cast: resume session after brief disconnect does not double-enqueue
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.4.2 (8)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
