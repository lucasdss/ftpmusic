# Play release 2.1.3

**versionCode:** 30 · **versionName:** 2.1.3 · **Room:** 71

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

Supersedes the 2.1.2 (29) testing cut: pack-first collection covers, Custom Daily
Mixes Settings hub promotion, and post-cut Home cover-edit genre plumbing.

## Why this release

Everything in [PLAY_RELEASE_2_1_2.md](PLAY_RELEASE_2_1_2.md), plus:

- Anime / active style pack as AUTO cover for Daily Mix + Playlist (`skipDerived`)
- In-pack image browser; remove detail Library|Device|Clear (sheet remains)
- Exact catalog genre fallback via recipe genres
- Custom Daily Mixes on Settings **Home & mixes** (after Account); Home **Manage**
- Home cover-edit passes `dominantGenre` matching GenreMixCard (post-2.1.2 review)

See [POST_2_1_2_RELEASE_REVIEW_BEHAVIOR_REPORT.md](POST_2_1_2_RELEASE_REVIEW_BEHAVIOR_REPORT.md).

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 2.0.0 | 26 | 68 | **30 / Room 71** (68→69→70→71) |
| Local 2.1.2 testing cut | 29 | 71 | **30 / Room 71** |
| Fresh install | — | — | **30 / Room 71** |

No new Room migration. **No** `fallbackToDestructiveMigration`.

## Pre-upload gates

```bash
cd compose && ./gradlew :app:assembleDebug
cd compose && ./gradlew :app:testDebugUnitTest --tests 'com.lucasdss.ftpmusic.app.data.cover.*'
cd compose && ./gradlew :app:testDebugUnitTest --tests 'com.lucasdss.ftpmusic.app.ui.settings.SettingsIndexTest'
cd compose && ./scripts/build-signed.sh
adb install -r compose/app/build/outputs/apk/release/app-release.apk
```

## Tester checklist (Pixel)

- [ ] Profile / About shows `2.1.3 (30)`
- [ ] Unpinned Daily Mix / Playlist shows style-pack art (not track collage by default)
- [ ] Long-press cover → Style packs → drill into pack → pick image pins cover
- [ ] Settings → Home & mixes → Custom Daily Mixes (near top, after Account)
- [ ] Home Daily Mixes **Manage** opens Custom Daily Mixes
- [ ] Network leaf has Rebuild Daily Mixes; no Custom Daily Mixes row
- [ ] Home long-press Style packs strip matches card genre for recipe mixes

## Out of scope / known deferrals

- Pack browser `browsingPackId` rememberSaveable restore
- Wire `DominantGenrePicker.preferRecipeThenTracks` for track-genre majority on playlists
- Cast sticky CT REMOVE_TERMINAL parity
- Jacoco gate wired into CI
