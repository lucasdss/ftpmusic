# Play release 1.8.0

**versionCode:** 12 · **versionName:** 1.8.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Wi‑Fi-only library sync + cellular media policy** — FULL/DELTA optional
  unmetered; On cellular: auto-cache / minimal / local-only (ADR-0105).
- **List chrome prefs** — show/hide reactions and duration on song rows.
- **UI density ladder + typography subpage** — Settings density + dedicated
  typography screen.
- **Scroll FPS pass 3** — pause EQ infinite anims while scrolling.
- **Daily Mix title clip** — stop title paint under card corner radius.
- **BT A17 silent wake + off-LAN cache seat** — SELECTED null-MAC Resume notif;
  prefer fully-cached index when offline/unreachable (ADR-0101).
- **Native debug-symbol AAB gate** — `make bundle-release` verifies symbols
  metadata (ADR-0018).
- **1.8.0 post-1.7.0 remediation (ADR-0106)** — BT block includes cellular
  LOCAL_ONLY; Syncing null-job no hang; DownloadManager live transport +
  `isOfflineEnabled` gate; legacy download_mobile_data cleared after migrate;
  offline seat wraps queue for cached tracks.

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

- [ ] Upgrade from 1.7.0 (11) (or fresh install)
- [ ] Profile / About shows `1.8.0 (12)`
- [ ] Settings: Library sync on Wi‑Fi only — auto sync skips on cell; Resync warns
- [ ] Downloads: On cellular = Local only — no stream/prefetch on cell; BT wake
      seeks downloaded track or shows offline Resume
- [ ] Legacy “download on mobile” off users land on Minimal (not Local only)
- [ ] List chrome: toggle reactions/duration; rows update
- [ ] Settings → Typography / density persist across restart
- [ ] Home Daily Mix titles not clipped by card corners
- [ ] Library/Home scroll: EQ bars idle when flinging
- [ ] BT: ANY/SELECTED resume paths still work (incl. null-MAC Resume notif)
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.8.0 (12)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
- FTS chunked rebuild / BundledSQLiteDriver
