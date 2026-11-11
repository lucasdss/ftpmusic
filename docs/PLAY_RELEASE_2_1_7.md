# Play release 2.1.7

**versionCode:** 34 · **versionName:** 2.1.7

## Highlights

- Returning users with a cached library no longer land on the blocking
  SyncingScreen after a soft-stopped drain; heal runs in the background.
- Warm-player user play reasserts mediaPlayback FGS (from 2.1.6 hotfix path).
- Sync-end diagnostics: `drainAttempted` / `drainFailed` (was ambiguous
  `pending` / `failed`).

See `docs/SAMSUNG_SYNC_DIAGNOSTICS_20261111_BEHAVIOR_REPORT.md` and
`docs/PLAY_WARM_FGS_DESTROY_BEHAVIOR_REPORT.md`.
