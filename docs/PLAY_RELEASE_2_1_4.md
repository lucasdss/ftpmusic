# Play release 2.1.4

**versionCode:** 31 · **versionName:** 2.1.4 · **Room:** 71

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

Hotfix on 2.1.3: Library Playlists create/import FAB was clipped by ADR-0114
riding chrome overhang at rest.

## Why this release

Everything in [PLAY_RELEASE_2_1_3.md](PLAY_RELEASE_2_1_3.md), plus:

- Hoist Playlists (+ Radio admin) FABs outside `appHeaderRidingContent` so they
  stay viewport-static and visible when the AppHeader is expanded
- Playlists list bottom clearance + empty-state “Add a playlist with +” hint
- Geometry regression tests for overlay FAB visibility / offset stability

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play / local 2.1.3 | 30 | 71 | **31 / Room 71** |
| Fresh install | — | — | **31 / Room 71** |

No new Room migration. **No** `fallbackToDestructiveMigration`.

## Pre-upload gates

```bash
cd compose && ./gradlew :app:assembleDebug
cd compose && ./gradlew :app:testDebugUnitTest --tests 'com.lucasdss.ftpmusic.app.ui.AppHeaderRidingClipHostComposeTest' --tests 'com.lucasdss.ftpmusic.app.ui.AppHeaderContentPaddingTest'
cd compose && ./scripts/build-signed.sh
adb install -r compose/app/build/outputs/apk/release/app-release.apk
```

## Manual smoke

- Library → Playlists at rest (header expanded): teal **+** visible without scrolling
- Tap **+** → create sheet shows **Import from Server**
- Create playlist succeeds; empty state shows “Add a playlist with +”
