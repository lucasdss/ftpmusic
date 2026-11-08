# Play release 2.1.6

**versionCode:** 33 · **versionName:** 2.1.6 · **Room:** 71

Post-2.1.5 correctness cut: refresh must not wipe pending local playlist
order; Save-to-playlist toasts only after actually-added count.

## Why this release

Everything in [PLAY_RELEASE_2_1_5.md](PLAY_RELEASE_2_1_5.md), plus:

- `refreshFromServer` skips entry wipe when outbound pending or reorder in flight
- `PlaylistRepository.addToPlaylist` returns added count; NP/Search/Home/Artist
  toast success / Already / failure honestly

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play / local 2.1.5 | 32 | 71 | **33 / Room 71** |
| Fresh install | — | — | **33 / Room 71** |

No new Room migration. **No** `fallbackToDestructiveMigration`.

## Pre-upload gates

```bash
cd compose && ./gradlew :app:assembleDebug
cd compose && ./gradlew :app:testDebugUnitTest \
  --tests 'com.lucasdss.ftpmusic.app.ui.playlist.*' \
  --tests 'com.lucasdss.ftpmusic.app.data.repository.PlaylistRepository*' \
  --tests 'com.lucasdss.ftpmusic.app.data.db.Playlist*'
cd compose && ./scripts/build-signed.sh
adb install -r compose/app/build/outputs/apk/release/app-release.apk
```

## Manual smoke

- Reorder playlist offline → leave → reopen: order preserved until sync
- Save already-present track from NP/Search: “Already in playlist”
- Home album → Add to Playlist: toast only after result
