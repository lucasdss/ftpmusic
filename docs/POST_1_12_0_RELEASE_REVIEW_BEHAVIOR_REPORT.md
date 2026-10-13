# POST 1.12.0 → 1.13.0 release review (caveman)

**Baseline:** `v1.12.0` (versionCode 19)  
**Target:** `1.13.0` (versionCode 20)  
**Range:** 6 commits after tag + review fixes

## Committed themes since 1.12.0

| Area | Commits | Notes |
|------|---------|-------|
| Android Auto | `0da3814`, `2d32ece` | peer-parity browse, Dual adopt, radio gates |
| BT Resume | `2af40ea` | user-initiated Resume tap → play (A17 WIU) |
| Library chrome | `5d2fb5d` | riding over-measure; black bar above mini |
| Play Next | `21f218f` | align-before-mutate; Dual≠Exo |
| Queue placement | `f898272` | LATE band + Settings toggle + Home Play Next |

## Confirmed bugs → fixed

### Queue sheet honesty (ADR-0135) — High

1. **LATE before Autoplay in sheet** — Dual merge puts LATE after full CONTEXT
   (incl. autoplay); sheet showed Queued later before Autoplay → playback ≠ UI.  
   **Fix:** `QueueSheetGrouping` + PlayerBar sections
   PRIORITY → Next from → Autoplay → Queued later; bands explicitly exclude
   `isLate` from continue/autoplay (Vibe: latent double-list if
   `isPriorityFlags` ever narrows).  
   **Test:** `QueueSheetGroupingTest`

### Auto Dual adopt race (ADR-0132) — High

2. **`maybeAdoptExternalTimeline` mid local sync** — PLAYLIST_CHANGED during
   `syncDualQueueToPlayer` could see Dual≠player mediaIds → adopt →
   `clearPriority()` wiped PRIORITY/LATE.  
   **Fix:** `noteLocalQueueMutation` / `shouldAdoptExternalTimeline` (~1s
   suppress); MediaService gates adopt. Explicit voice adopt bypasses gate.  
   **Test:** `local sync suppresses external Dual adopt briefly`,
   `syncDualQueueToPlayer notes local mutation for adopt gate`

### Persistence verify drift — Med (gate blocker)

3. **`coVerify save` still 9-arg** — production always passes `isLateFlags`
   list; MockK matched 10th as `null()`.  
   **Fix:** all persistence verifies accept 10th `any()` for late flags.

## Document-only (not blockers)

- Pre-upgrade BT Resume intents without EXTRA → one WIU re-entry (ADR-0133)
- No Compose UI scroll / HU device gates (tester checklist)
- Cast `session_end` beforeEntryId thin coverage (existing Dual insert tests)

## Gates (must green before tag)

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```
