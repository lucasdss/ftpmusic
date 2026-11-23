# Play release 2.1.9

**versionCode:** 37 · **versionName:** 2.1.9 · **Room:** 71

> Play Console already consumed versionCode **36** (prior upload / draft). This
> cut keeps versionName `2.1.9` and ships as **37**.

Post-2.1.8 correctness cut after discover→challenge of BT / sticky terminal /
CP idle / Daily Mix sticky / battery+Cast delta (`12e3d71..38b300a` + review fixes).

## Why this release

Everything in [PLAY_RELEASE_2_1_8.md](PLAY_RELEASE_2_1_8.md), plus:

- Tesla / warm A2DP force-rebind; sticky last-track + CP idle resume (ADR-0161/0164)
  with review fixes for inverted `playWhenReady` gate and cap-saturated seek index
- Daily Mix sticky Home cards + rebuild anti-repeat (ADR-0162/0163); activate no
  longer resurrects stale “New mix ready”
- MixDetail sticky Play/Shuffle/options while scrolling
- Idle radio / battery drain cuts; Cast button always visible + passive discovery
  (ADR-0165); constraint-blocked download poll 1s (not 60s empty wait)
- Age Signals: legal hold documented — no SDK in this cut

See [POST_2_1_8_RELEASE_REVIEW_BEHAVIOR_REPORT.md](POST_2_1_8_RELEASE_REVIEW_BEHAVIOR_REPORT.md).

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play / local 2.1.8 | 35 | 71 | **37 / Room 71** |
| Fresh install | — | — | **37 / Room 71** |

No new Room migration. **No** `fallbackToDestructiveMigration`.

## Pre-upload gates

```bash
cd compose && ./gradlew :app:assembleDebug
cd compose && ./gradlew :app:testDebugUnitTest \
  --tests 'com.lucasdss.ftpmusic.app.playback.BtAudioRouteTest' \
  --tests 'com.lucasdss.ftpmusic.app.playback.BtAutoplayStarter*' \
  --tests 'com.lucasdss.ftpmusic.app.playback.SourceErrorClassificationTest' \
  --tests 'com.lucasdss.ftpmusic.app.playback.MediaServiceFgsReassertTest' \
  --tests 'com.lucasdss.ftpmusic.app.playback.DailyMixGeneratorTest' \
  --tests 'com.lucasdss.ftpmusic.app.data.repository.DailyMixRepositoryTest' \
  --tests 'com.lucasdss.ftpmusic.app.ui.library.MixDetailStickyActionsTest' \
  --tests 'com.lucasdss.ftpmusic.app.ui.library.MixDetailComposeTest' \
  --tests 'com.lucasdss.ftpmusic.app.data.repository.ServerReachabilityMonitorTest' \
  --tests 'com.lucasdss.ftpmusic.app.ui.player.CastButtonStateTest' \
  --tests 'com.lucasdss.ftpmusic.app.data.db.PlaylistSyncWorkerBranchTest' \
  --tests 'com.lucasdss.ftpmusic.app.playback.AutoplayTailCapTest'
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
# AAB: compose/app/build/outputs/bundle/release/app-release.aab
# Optional sideload APK:
# cd compose && ./scripts/build-signed.sh
```

## Manual smoke

- BT / car: warm A2DP rebind while already “playing”; cold Path B when not on A2DP
- Last sticky / terminal track → CP append resumes without cabin silence
- Daily Mix: lazy pending → force activate rebuild → no stale “New mix ready”
- MixDetail: scroll — Play/Shuffle/options stay pinned
- Cast: button always visible; picker uses ACTIVE_SCAN while open
- Low battery: downloads poll (not stuck 60s) when constraints clear
- Profile / About shows `2.1.9 (37)`

HU checklist: [ANDROID_AUTO_HU_TESTER_CHECKLIST.md](ANDROID_AUTO_HU_TESTER_CHECKLIST.md)
