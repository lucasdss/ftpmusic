# Play release 1.6.0

**versionCode:** 10 · **versionName:** 1.6.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Queue sheet** — save as playlist, history band, batch select; sleep/repeat
  strip; share as public playlist (local-first + sync gate); Cast Autoplay
  honesty (switch disabled while casting).
- **Search** — FTS5 BM25 on SupportSQLite; lyrics SERP; tag/mood chips; live
  Discover; hydrate-by-id; empty-FTS rebuild; local+server union.
- **Library sync** — orphan song densify; preserve-enrich album/artist upserts;
  async MetadataEnrichWorker after sync.
- **1.6.0 audit remediation** — cancel-safe search errors; preserve-enrich on
  search cache; year filter drops null-album orphans; queue revision refresh;
  Subsonic status gate on playlist flush; share NEW_TASK + single-flight;
  batch remove one snapshot; enrich REPLACE; densify path/mbid merge.

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

- [ ] Upgrade from 1.5.0 (9) (or fresh install)
- [ ] Profile / About shows `1.6.0 (10)`
- [ ] Search: typeahead cancel does not flash “Can't reach server”
- [ ] Search: decade/year + text does not surface album-less modern singles
- [ ] Search: after enrich, re-search does not wipe artist bio / album notes
- [ ] Queue: reorder then multi-select remove hits correct rows
- [ ] Queue: Share while online publishes public playlist + chooser; offline
      toasts without chooser; double-tap does not create two playlists
- [ ] Cast: Autoplay switch disabled; Clear Autoplay still available for Dual
- [ ] Library sync: orphan densify + enrich still run after FULL/DELTA
- [ ] Share diagnostics still works (regression smoke)

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.6.0 (10)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
- FTS chunked rebuild / BundledSQLiteDriver
