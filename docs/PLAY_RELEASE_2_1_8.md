# Play release 2.1.8

**versionCode:** 35 · **versionName:** 2.1.8 · **Room:** 71

Post-2.1.7 correctness cut after DeepSeek/Mistral cross-check of Auto Home /
connect alert / artist chrome / voice extras.

## Why this release

Everything in [PLAY_RELEASE_2_1_7.md](PLAY_RELEASE_2_1_7.md), plus:

- Android Auto Home hub, connect availability alert, reaction slots, voice extras
  (from `1241da6`) with review fixes below
- Artist bio teaser + sheet + collapsing chrome (from `858f141`) with measure-loop fix
- Connect alert: fingerprint persisted only after successful notification
- Surprise Me: LocalOnly uses cached tracks; playable-only (not browsable empty)
- Favorite radio: `item` / expand fall back to Room favorites
- Voice: album-focus Assistant extras expand album before track path

See [POST_2_1_7_RELEASE_REVIEW_BEHAVIOR_REPORT.md](POST_2_1_7_RELEASE_REVIEW_BEHAVIOR_REPORT.md).

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play / local 2.1.7 | 34 | 71 | **35 / Room 71** |
| Fresh install | — | — | **35 / Room 71** |

No new Room migration. **No** `fallbackToDestructiveMigration`.

## Pre-upload gates

```bash
cd compose && ./gradlew :app:assembleDebug
cd compose && ./gradlew :app:testDebugUnitTest \
  --tests 'com.lucasdss.ftpmusic.app.playback.AutoConnectAlertNotifier*' \
  --tests 'com.lucasdss.ftpmusic.app.playback.AutoBrowseCatalog*' \
  --tests 'com.lucasdss.ftpmusic.app.playback.MediaSessionCallback*' \
  --tests 'com.lucasdss.ftpmusic.app.playback.MediaSessionReactionCommands*' \
  --tests 'com.lucasdss.ftpmusic.app.ui.artist.ArtistBioUi*' \
  --tests 'com.lucasdss.ftpmusic.app.util.TextHtml*'
cd compose && ./scripts/build-signed.sh
adb install -r compose/app/build/outputs/apk/release/app-release.apk
```

## Manual smoke

- Artist detail: long bio → albums grid usable; Read more sheet; chrome collapses/restores
- Auto: Home first; Surprise Me plays (does not open empty folder)
- Auto: notifications off → connect → enable notifications → reconnect shows availability once
- Auto: favorite-only radio from Home plays
- Voice: “Play album \<name\>” / album focus starts album context
- Profile / About shows `2.1.8 (35)`

HU checklist: [ANDROID_AUTO_HU_TESTER_CHECKLIST.md](ANDROID_AUTO_HU_TESTER_CHECKLIST.md)
