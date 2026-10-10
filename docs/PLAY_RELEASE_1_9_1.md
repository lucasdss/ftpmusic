# Play release 1.9.1

**versionCode:** 14 · **versionName:** 1.9.1

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Settings → Check for updates** — Play In-App Updates (flexible) with listing
  fallback; no banner or gear badge (ADR-0113).
- **Flexible install completion** — register install listener; on download
  finished show **Restart to install** and call `completeUpdate()`.
- **In-progress honesty** — `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` shows
  downloading state instead of a second “Update available” CTA.
- **Cancellable Play Task await** — avoid late-resume crashes after cancel.

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

- [ ] Upgrade from 1.9.0 (13) (or fresh install)
- [ ] Profile / About shows `1.9.1 (14)`
- [ ] Settings → ABOUT / DIAGNOSTICS → **Check for updates** visible
- [ ] Sideload/debug: Up to date or Error + Open Play Store (no fake Available)
- [ ] Play install with newer track: Available → flexible UI → Downloading → Restart to install
- [ ] Tap Restart to install applies update (app restarts onto new build)
- [ ] Flexible not allowed: opens Play Store listing
- [ ] Offline check: Error + Open Play Store
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.9.1 (14)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
- Full process-death flexible resume UI
- Home banner / gear badge update prompts
