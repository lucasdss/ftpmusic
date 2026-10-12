# POST 1.10.2 → 1.11.0 release review (caveman)

**Baseline:** `v1.10.2` (versionCode 17)  
**Target:** `1.11.0` (versionCode 18)  
**Range:** 4 commits after tag + auto-center WIP + ship fixes

## Committed themes since 1.10.2

| Area | Commits | Notes |
|------|---------|-------|
| Cast | `a42ea18`, `ce72749` | force scan, heartbeat, volume seed, hard abort |
| Library chrome | `b445f1e` | collapse header+list — no black gap |
| Covers | `6d296db` | square crop, JPEG ≤1024 / ≤512KiB |

## Confirmed bugs → fixed

### Cast (ADR-0124) — High

1. **Abort wiped by `onSessionEnded`** — `cancel` → `endCurrentCastSession` → `clearCastConnectAttempt` cleared `connectUserAborted` → late `onSessionStarted` could stick.  
   **Fix:** `clearCastConnectAttempt(clearUserAbort=false)` from session-ended; sticky abort until new connect / success.  
   **Test:** `abort flag survives onSessionEnded so late start still rejects`

2. **Supersede A→B late A** — `onSessionStarted` only checked abort flag.  
   **Fix:** reject when `session.deviceId != castConnectTargetDeviceId`; set `castSwitchInProgress` so teardown does not kill B in-flight. Also gate `remote=true`.  
   **Test:** `supersede A to B rejects late onSessionStarted for A`

### Auto-center (ADR-0126) — High/Med

3. **Suppress drop** — delay then `return` if suppress extended → lost follow.  
   **Fix:** wait loop until suppress clear.

4. **Queue yank** — every `trackId` scrolled.  
   **Fix:** `onlyIfNotVisible=true` + `isTargetReasonablyVisible`.

5. **Config yank** — `remember` suppress lost.  
   **Fix:** `rememberSaveable(suppressUntil)` + `onlyIfNotVisible` on detail/queue.

6. **Hero size offset / short layout wait** — median row estimate; layout wait 50 frames.

### Cover (ADR-0125) — Med

7. **Soft 512KiB last resort** — now reject if still over budget.  
8. **Export toast = load toast** — `onExportFailed` / `notifyCoverCropExportFailed`.  
9. **Orphan `mix_new_*`** — delete prior staged file on re-crop.

## Document-only (not blockers)

- Library gap: manual tester checklist (no compose scroll-collapse gate)
- Cast heartbeat weak-banner latch: soft UX; no rewrite this cut
- Auto-center default ON: product (ADR-0126)

## Hygiene

- Deleted junk Coil path `compose/app/File(child^2 of #247#268#270)/` (never commit)

## Gates (must green before tag)

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```
