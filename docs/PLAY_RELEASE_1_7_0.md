# Play release 1.7.0

**versionCode:** 11 · **versionName:** 1.7.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Android Auto browse MVP** — root/favorites/playlists/albums/artists; stream
  URI playables; empty playlist/album not Play-all.
- **Downloads screen** — paged offline list; heal missing files; stale status
  honesty (no purple downloaded glyph when file gone).
- **Cast Continuous Play** — Dual SoT append; ENDED retry; Autoplay honesty.
- **BT deep-sleep resume** — A2DP reconnect after Doze; ANY-mode null-MAC
  (no BLUETOOTH_CONNECT) resumes via policy key.
- **Cover art** — soft-cache placeholders; Coil onError evicts corrupt `file://`.
- **Home / scroll** — section header standard; AppHeader collapse; SongListRow
  unify; Cast gated when header non-interactive.
- **Search** — recent history only on commit (not typeahead).
- **Typography prefs** — user scale/family in Settings (ADR-0099).
- **1.7.0 post-ship remediation** — BT null-MAC; Cover eviction; AppHeader Cast;
  Downloads stale; Auto empty album; PUSH tests ↔ ADR-0094; NowPlaying Coil
  idle flake; detekt style noise for ThrowsCount / FunctionOnlyReturningConstant.

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

**Native debug symbols:** release sets `ndk.debugSymbolLevel = "SYMBOL_TABLE"`
so AGP embeds extractable native symbols in the AAB (Play crash/ANR
symbolication). Current packaged `.so` is pre-stripped
`libandroidx.graphics.path.so` from AndroidX — AGP then has nothing to embed
(`mergeReleaseNativeDebugMetadata` NO-SOURCE). Console may still soft-warn
until an unstripped native lands; config is ready for that day.

## Pre-upload gates

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release   # :app:bundleRelease + verify-native-symbols (ADR-0018)
```

Every future `PLAY_RELEASE_*` pack must keep `make bundle-release` (or
`make verify-native-symbols` after a manual `bundleRelease`) in this list.

## Tester checklist

- [ ] Upgrade from 1.6.0 (10) (or fresh install)
- [ ] Profile / About shows `1.7.0 (11)`
- [ ] BT: ANY-mode resume after A2DP reconnect without Connect permission
- [ ] Auto: empty album not Play-all; downloaded track plays via stream URI
- [ ] Downloads: missing file shows “File missing…” without purple check
- [ ] Home: collapse header — Cast does not open when clipped
- [ ] Cover: corrupt cached art recovers / shows fallback
- [ ] Search: typeahead does not pollute recent; commit does
- [ ] Settings: typography scale persists across restart
- [ ] Cast: Continuous Play still appends on last track
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.7.0 (11)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
- FTS chunked rebuild / BundledSQLiteDriver
