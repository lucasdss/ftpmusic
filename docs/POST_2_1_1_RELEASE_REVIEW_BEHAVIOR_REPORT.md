# POST 2.1.1 → 2.1.2 release review (caveman)

**Baseline:** testing cut `2.1.1` / `ff26b49` (versionCode 28, Room 71)  
**Target:** `2.1.2` (versionCode 29)  
**Range:** `8006eb0` + `6261099` + this cut’s fixes  
**Room:** 71 (unchanged)

## Cross-model

| Pass | Model | Role |
|------|-------|------|
| Discover | Cursor orchestrator + Opus read-only | Hot-path walk |
| Phase-5 audit | OpenCode `android-plan-deepseek` / DeepSeek | Confirm / re-diagnose |
| Challenge | OpenCode `android-dd-mistral` / Mistral | Attack DeepSeek |

Orchestrator kept only double-confirmed (or code-proven vs wrong reject) bugs.

## Confirmed bugs → fixed (ship in 2.1.2)

### P1-A SyncingScreen false “Sync failed” (cooldown / already-running)

**Symptom:** First-login / Retry called `syncNowAsync` without cooldown bypass; rotation or background race → 3 nulls → error after a sync that worked or is still running.  
**Fix:** SyncingScreen passes `bypassCooldown=true`; adopts `status.isRunning && runId>0` instead of burning retries.  
**Test:** `SyncingViewModelExitGateTest` B0-7.

### P1-B `MetadataSyncWorker.start()` raced SyncingScreen

**Symptom:** Auto-FULL 2s after launch while Splash routes incomplete drain to SyncingScreen → cellular dialog vs background sync / cooldown fail on Confirm.  
**Fix:** `start()` no longer auto-FULL; SyncingScreen + SyncHealthGuard/WorkManager own empty/incomplete drain.  
**Test:** `MetadataSyncWorkerBranchTest` “start does not auto-FULL…”.

### P1-C Mix detail header collage vs pack backdrop

**Symptom:** `skipDerived=true` for MIX → backdrop pack; header `when` ignored `montageActive` and drew track collage when `covers.size≥4`.  
**Fix:** Header pack/lettermark branch when `!montageActive`.  
**Note:** DeepSeek wrongly rejected; orchestrator code-proof + Mistral challenge confused — kept as fix.

### P1-D Fine-scrub unreachable + seek-after-yield

**Symptom:** Yield at 24px blocked fine-scrub (half at 80px); yield still called `onSeek`; gesture cancel could leave `scrubbing` stuck.  
**Fix:** `AXIS_LOCK_THRESHOLD_PX=140`; skip seek when yielded; `finally { scrubbing=false }`; consume only when not yielding.  
**Test:** `FineScrubMathTest` axis-lock cases.

### P1-E Playlist create name fallback → wrong id

**Symptom:** After flush, missing remap fell back to first playlist with same name.  
**Fix:** Remap-only; keep `tempId` if remap absent.  
**Test:** `PlaylistRepositoryTest` “keeps temp id when remap missing”.

### P1-F Waveform accent always BrandTeal + play/pause haptics unwired

**Symptom:** Darkened vibrant (Y≤0.15) never cleared 4.5:1 vs dark `bgTop`; Appearance promised play/pause haptics but `playPauseKind` unused.  
**Fix:** `lightenToMinLuminance` in `accentOrFallback`; wire mini + full transport haptics via `PlayerHapticsPolicy`.  
**Test:** `PlayerCoverSchemeTest` darkened-seed lift.

## Rejected / deferred

| Claim | Verdict |
|-------|---------|
| B1 compile break missing `KEY_PLAYER_HAPTICS_ENABLED` | Rejected — constant exists |
| B4 `outcome=error` permanent SyncingScreen soft-lock | Rejected — never-drained already gated by `lastDrainCompleteMs≤0` |
| About watermark / reduce-motion re-read / jacoco CI | Deferred P2 |

## Gates

```bash
cd compose && ./gradlew :app:assembleDebug
# targeted packages green (Syncing*, PlaylistRepository*, FineScrub*, PlayerCoverScheme*, MetadataSyncWorkerBranch*)
```

**Gate results:** assembleDebug + listed unit tests green (2026-11-08).
