# Samsung SyncingScreen trap (2026-11-11)

## Symptom

Returning user (Samsung SM-S942B, sdk=36, was on 2.1.6/33) opened the app and
saw the first-login Syncing page, could not dismiss it, had to wait; sync felt
like it restarted. Library was already present (queue restore + playback during
sync).

## Root cause

1. Prior session left `last_sync_outcome=soft-stopped` (or never stamped
   `last_drain_complete_ms`).
2. Splash treated `drainIncomplete` like empty library → `syncing?returnTo=home`.
3. That route sets `userTriggered=false` → BackHandler no-op, no Skip while
   running.
4. SyncHealthGuard silence DELTA raced SyncingScreen FULL (`already_running`
   adopt) → visible “restart.”

Album drop / corpus prune / `failed=N` in the dump are intentional heal paths,
not the UX bug.

## Fix (2.1.7 / 34)

- **SplashRouter:** only `albumCount == 0` opens SyncingScreen. Albums present →
  Home even if drain incomplete.
- **MetadataSyncWorker.start:** if metadata exists and drain incomplete, kick
  background FULL (`bypassCooldown`); empty library still SyncingScreen-owned.
- Sync-end diagnostics: `drainAttempted` / `drainFailed` (was ambiguous
  `pending` / `failed`).

## Manual check

Force prefs soft-stopped with albums → relaunch → Home immediately; sync heals
in background. Empty library first login still blocked on Syncing until usable.
