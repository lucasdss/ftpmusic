# Play release 1.10.1

**versionCode:** 16 · **versionName:** 1.10.1

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

### Features since 1.10.0

- **Track-number chrome** — disc/track column + sort sync on album/playlist/mix
  (ADR-0116).
- **BT seat + terminal downloads** — A17 seat without play flash; non-audio CT →
  sticky fail; user Download can retry (ADR-0117).
- **BT resume UX** — battery CTA, warm A2DP monitor, Resume notif hardening
  (ADR-0119).
- **System media reactions** — like/dislike on session / notification / QS
  (ADR-0118).
- **Issue status tray** — AppHeader icon + sheet replaces stacked banners
  (ADR-0120).

### P0 fixes (ADR-0121)

- **Disc populate** — preserve existing `tracks.disc_number` when cache disc NULL.
- **BT debounce** — warm null-MAC and ACL real-MAC share one debounce window.
- **Shade thumbs** — reload reactions on track transition when UI dead.
- **Resume notif** — debounce `selected_null_mac` path.

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

**Native debug symbols:** release sets `ndk.debugSymbolLevel = "SYMBOL_TABLE"`.
Run `make bundle-release` (includes `verify-native-symbols`).

## Pre-upload gates

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release   # :app:bundleRelease + verify-native-symbols (ADR-0018)
```

## Tester checklist

- [ ] Upgrade from 1.10.0 (15) (or fresh install)
- [ ] Profile / About shows `1.10.1 (16)`
- [ ] Album with multi-disc: after library sync, disc column still correct
- [ ] BT ANY mode: connect car once → single autoplay (no double start)
- [ ] BT SELECTED + Nearby devices denied: Resume notif once, not a storm
- [ ] Play liked track → skip to unliked with app backgrounded → shade thumbs match
- [ ] Issue tray: server unreachable / offline / playback error surfaces
- [ ] QS / lock: like + dislike toggle current track
- [ ] User Download retries after terminal CT fail
- [ ] Share diagnostics still works

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.10.1 (16)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
- Notification shed / idle FGS remediation
- `replaceMediaItem` thumb metadata alternative
