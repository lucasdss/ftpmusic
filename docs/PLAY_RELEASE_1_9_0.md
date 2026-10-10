# Play release 1.9.0

**versionCode:** 13 · **versionName:** 1.9.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Scroll fluidity passes 4–7** — overlay AppHeader, Lazy padding, cache window /
  prefetch, macrobenchmark harness (ADR-0107).
- **Tracks corpus reconcile on FULL heal** — keep `last_played_at` orphans
  (ADR-0108).
- **Home Recently Added TTL** — local snapshot + TTL capped by Sync Interval;
  DELTA when ordered ids change; timeout/empty backoff (ADR-0109 / ADR-0112).
- **Audio cache payload validation** — reject Subsonic error bodies; hybrid
  POISON heal vs UNKNOWN keep-pin (ADR-0110).
- **Cast CP batch AddAll + Source-error circuit** — stop queue wipe storms
  (ADR-0111).
- **1.9.0 ship blockers (ADR-0112)** — force-heal pinned poison on Source-error;
  scrobble ghost gate (not 5s floor); CIRCUIT_STOP clears circuit; mix UNKNOWN
  ownership without re-download churn; Home TTL stamp on empty/timeout;
  untrack macrobenchmark build outputs.
- **Header chrome** — Lazy lists padded under overlay AppHeader; Library/Favorites
  Column top pad.

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

- [ ] Upgrade from 1.8.0 (12) (or fresh install)
- [ ] Profile / About shows `1.9.0 (13)`
- [ ] Library/Favorites: chips/search fully below AppHeader (cold open expanded)
- [ ] Cast continuous play: queue append batches; Source errors stop after 3 in 15s without wiping whole queue
- [ ] Plant poison cache (Subsonic JSON) pinned download → play error clears span; next track seeks
- [ ] Short track (~3s) past 60% still scrobbles
- [ ] Home Recently Added: after empty/failed newest, reopen within TTL does not spam network
- [ ] Mix auto-cache with exotic/UNKNOWN span: no endless re-download
- [ ] Library/Home scroll: header enter-always; EQ idle while flinging
- [ ] FULL resync keeps orphan `last_played_at` rows
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.9.0 (13)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
- Cast dual-advance residual (documented ADR-0111)
- Alpha full-catalog heap / Coil pause-on-fling
- FTS chunked rebuild / BundledSQLiteDriver
