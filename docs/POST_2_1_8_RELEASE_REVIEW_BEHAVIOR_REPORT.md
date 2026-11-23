# POST 2.1.8 → 2.1.9 release review

**Baseline:** testing cut `2.1.8` / `12e3d71` (versionCode 35)  
**Delta:** `0fa88cd`…`38b300a` (BT Tesla / warm rebind, sticky terminal + CP idle,
Daily Mix sticky Home + anti-repeat + MixDetail chrome, battery/Cast) + review fixes  
**Target:** `2.1.9` (versionCode 37; 36 already used on Play)

## Cross-model

| Pass | Role |
|------|------|
| Discover | Diff walk `12e3d71..HEAD` (BT/MediaService, Daily Mix, battery/Cast) |
| Challenge | One subagent per finding — disprove or downgrade |
| Audit | Re-challenge of F1/F2/D1 fixes after patch |

## Confirmed → fixed in 2.1.9

1. **F1 — CP idle resume inverted on `playWhenReady`** — `maybeResumeAfterAutoSkipIdle`
   cleared the flag when `playWhenReady == true`. Media3 `stop()` leaves it true after
   a playing → IDLE path, so ADR-0164 resume never sought. Removed the gate (user pause
   already clears via `onPlayWhenReadyChanged`). Tests force `playWhenReady = true`.
2. **F2 — Cap-saturated CP resume used pre-append index** — at autoplay cap, eviction
   keeps `mediaItemCount` flat so `indexBeforeAppend >= count` aborted resume; even a
   coerce would seek the wrong row. `appendToContext` now returns first-new index
   (`countAfter - appended`); resume seeks that.
3. **D1 — Activate resurrected stale pending ready** — activate cleared pending without
   deleting the prior pending ready (ADR-0162 keep-outs). `reconcileOrphans` re-pointed
   pending → older “New mix ready”. Activate now `deleteUnpublishedBuilds` after clearing
   pending; reconcile skips `ready.version <= active.version`.
4. **B1 — Constraint-blocked downloads waited 60s** — pending pri1/2 with failed
   `checkConstraints` shared the empty-queue 60s wait (contradicts ADR-0165). Now 1s
   poll when pri1/2 pending; 60s only when truly empty.

## Rejected / deferred

| ID | Topic | Disposition |
|----|-------|-------------|
| F3 | Sticky REMOVE before unreachable jump | **Rejected** — ADR-0164/0150 intentional |
| F4–F7 | Cold Path B residual / warm rebind blip / route probe | Info / product tradeoff |
| D2 | MixDetail auto-center vs sticky height race | **Defer** — polish; track-change re-keys |
| D3–D4 | Anti-repeat depth test/docs drift | Info |
| C1 | Cast ACTIVE_SCAN NavHost untested | Info (always-visible button intentional) |
| B2–B3 | WifiLock / enrich constraint test gaps | Info |
| N1–N4 | Auto deferred from POST_2_1_7 | Still deferred (out of this delta) |

## Fatal

None.

## Quality notes

- Scoped assembleDebug + unit tests green ×2; `lintVitalRelease` green.
- `make quality` (ktlint/detekt) still fails on **pre-existing**
  `SyncingViewModelTest` / `MediaSessionReactionCommands` issues outside this cut —
  not introduced by 2.1.9.
