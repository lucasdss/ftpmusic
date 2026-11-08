# Play release 2.1.2

**versionCode:** 29 · **versionName:** 2.1.2 · **Room:** 71

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

Supersedes the 2.1.1 (28) testing cut: post-cut review of sync-exit / covers /
Now Playing visuals closed the P1s below.

## Why this release

Everything in [PLAY_RELEASE_2_1_1.md](PLAY_RELEASE_2_1_1.md), plus correctness
fixes from [POST_2_1_1_RELEASE_REVIEW_BEHAVIOR_REPORT.md](POST_2_1_1_RELEASE_REVIEW_BEHAVIOR_REPORT.md):

- SyncingScreen cooldown bypass + adopt already-running worker
- Remove `MetadataSyncWorker.start()` auto-FULL race with SyncingScreen
- Mix detail header matches pack backdrop when derived collage is skipped
- Fine-scrub axis lock above half-speed pull; no seek after vertical yield
- Playlist create remap-only (no duplicate-name fallback)
- Cover-tinted waveform accent lift; play/pause haptics honor Appearance

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 2.0.0 | 26 | 68 | **29 / Room 71** (68→69→70→71) |
| Local 2.1.1 testing cut | 28 | 71 | **29 / Room 71** |
| Fresh install | — | — | **29 / Room 71** |

No new Room migration. **No** `fallbackToDestructiveMigration`.

## Pre-upload gates

```bash
cd compose && ./gradlew :app:assembleDebug
cd compose && ./gradlew :app:testDebugUnitTest --tests 'com.lucasdss.ftpmusic.app.ui.library.SyncingViewModel*'
make install   # Pixel replace-install, keep data
```

## Tester checklist (Pixel)

- [ ] Profile / About shows `2.1.2 (29)`
- [ ] Soft-stop → relaunch → SyncingScreen Retry works (no instant “Sync failed”)
- [ ] Cellular first-login dialog: Cancel does not claim success; Confirm starts sync
- [ ] Mix detail without fixed cover: header art matches backdrop (pack, not collage)
- [ ] Now Playing: vertical pull on waveform fine-scrubs before dismiss threshold
- [ ] Appearance haptics ON → play/pause click vibrates; reduce-motion / OFF → silent
- [ ] Create playlist with duplicate name → opens the new playlist, not an older twin

## Out of scope / known deferrals

- Cast sticky CT REMOVE_TERMINAL parity (ADR-0150)
- CP MediaService E2E harness
- About watermark duration/timestamp pairing polish
- `ProvideReduceMotion` live re-read of system animator scales
- Jacoco gate wired into CI
