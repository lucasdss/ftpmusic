# Play release 1.5.0

**versionCode:** 9 · **versionName:** 1.5.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Playback harden** — mediaId bound to stream URI; atomic queue restore;
  Dual persist as source of truth; cast→local async restore with session-kill
  position save (no double-seek race).
- **Library sync** — WorkManager awaits sync Job; fail-closed when album list
  is incomplete mid-pagination; watermarks only on `phase=complete`.
- **Artist detail** — albums-first layout; unified spacing; stable list keys.
- **Player surfaces** — Now Playing / mini / queue sheet UX parity (reorder,
  lexicon, buffering, string resources).
- **Bluetooth resume** — A2DP autoplay for any or selected bonded devices;
  CONNECT permission gates enable on API 31+; system media claim helpers.
- **Review pass** — cast/sync/BT/player copy correctness from deep review.

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

- [ ] Upgrade from 1.4.2 (8) (or fresh install)
- [ ] Profile / About shows `1.5.0 (9)`
- [ ] Cast: disconnect / session end → local resumes at correct track + position
- [ ] Library sync: after a failed mid-page album list, watermarks do not jump;
      library cache kept
- [ ] Artist detail: albums section first; track list scrolls/pages stably
- [ ] Player: mini hidden when idle; queue sheet reorder + remove; "Next from"
      / Autoplay section labels
- [ ] Settings → Bluetooth: enable asks for CONNECT on Android 12+; **Any** vs
      **Selected** devices; deny permission leaves toggle off
- [ ] A2DP connect (headphones/car) with resume on → playback restores / plays
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.5.0 (9)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
