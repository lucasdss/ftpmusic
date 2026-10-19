# POST 1.16.1 → 2.0.0 release review (caveman)

**Baseline:** `v1.16.1` (versionCode 25)  
**Target:** `2.0.0` (versionCode 26)  
**Range:** 6 commits after tag + P1 review fixes  
**Room:** 68 (unchanged)

## Committed themes since 1.16.1

| Area | Commits | Notes |
|------|---------|-------|
| Home TTL | `a98c645` | RESUMED TTL + revision id-equality |
| Cache | `7fe4ff2` | play-queue `isDownload=false` vs CT storm |
| Settings | `c9c08cd` `ae394ec` | nav hub + leaf screens + i18n/anchors |
| Cover | `6c9bdb1` `ce14785` | downscale + long-press + OOM fallback |

## Vibe Discover (headless ask)

Edge/Perf agents: cover rotation sheet-over-crop; homeRecentRevision race;
double fetch cold start; failed sticky conflation. No P0 crash found.

## Confirmed bugs → fixed (ship in 2.0.0)

### P1-1 Cover edit rotation — High

**Symptom:** crop open + rotate → sheet re-opens on top of crop.  
**Cause:** `LaunchedEffect` → `openSheet()` after `rememberSaveable` restores
`pendingCropUri`.  
**Fix:** `canAutoOpenCoverEditSheet` gates `openSheet`.  
**Test:** `CollectionCoverEditControllerTest`.

### P1-2 Home stars race — High

**Symptom:** new-id TTL bump → Room repaint wipes `rating`.  
**Cause:** `writeHomeRecentSnapshot` before API paint; snapshot omits stars.  
**Fix:** paint `_state.randomAlbums` first; merge prior ratings in
`paintHomeRecentFromSnapshot`.  
**Test:** `home recent revision merges prior ratings by id`.

### P1-3 Double fetch cold start — Med

**Symptom:** init + first RESUMED both hit `getAlbumList2(newest)`.  
**Fix:** `homeRecentMutex.tryLock` single-flight.  
**Test:** `loadRecentlyAddedHome single-flight skips concurrent resume`.

### P1-4 Transient failed sticky — Med

**Symptom:** retry-exhausted `failed` treated like CT for `isDownload=false`.  
**Fix:** CT → `failed_ct` (sticky); exhausted `failed` → `retry_transient`;
legacy low-retry `failed` stays sticky.  
**Test:** DownloadManager enqueue + worker CT → `failed_ct`.

## Looks correct (no further code)

- Settings hub search + leaf smoke coverage
- Cover ImageDecoder / URI BitmapFactory OOM path
- Header hide on `settings/*` + downloads
- Play-queue remainder not pinned as Download

## P2 deferred (not blockers)

- Settings search query not `rememberSaveable`
- Fuzzy keyword false positives (`bt` ⊂ subtitle)
- Cover Cancel vs `releaseOwnedDecode` consistency
- Cover tmp orphan sweep; hard-coded cover toasts
- D6 SettingsViewModel split (ADR 0149)
- Migrated `is_download=1` remainder pins (ADR-0117 Gap A)

## Gates (must green before tag)

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```

**Gate results (2026-10-19):** all green — `test-report` (JaCoCo), `quality`,
`lintVitalRelease`, `bundle-release` + native-symbols, `:app:assembleDebug`.
