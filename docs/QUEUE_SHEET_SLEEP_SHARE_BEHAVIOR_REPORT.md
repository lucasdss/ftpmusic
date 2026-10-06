# Queue Sheet Sleep/Repeat + Share Publish — Behavior Report

Caveman. Surface = prod `PlayerBar` `queue_sheet` (ADR-0076).

## Ship

| Feature | Behavior |
|---------|----------|
| Sleep strip | `queue_sheet_sleep` → same `onSleepTimer` as NP; countdown when armed |
| Repeat strip | `queue_sheet_repeat` → `onRepeatToggle`; teal when repeat on |
| Share | Local create+add → await flush → `public=true` → share deep link |
| Save | Unchanged local-only (ADR-0075) |

## Edge Agent

| Edge | Status |
|------|--------|
| Selection mode | Sleep/repeat strip hidden |
| Empty queue share | No-op |
| Offline / flush fail | Local playlist kept; toast; no public/chooser link |
| Public API fail | Toast; **no chooser** (Round 2 gate) |
| NP header | Sleep/repeat unchanged |

## Perf Agent

| Concern | Mitigation |
|---------|------------|
| Strip while sheet closed | Sheet compose-gated (existing) |
| Await flush | Mutex-serialized; does not stack on AtomicBoolean skip |

## Design Agent

| Token | Use |
|-------|-----|
| Sleep/repeat hits | 48dp |
| Active tint | BrandTeal |
| Idle tint | NavUnselected |

## Coverage Agent

| Contract | Test |
|----------|------|
| Share create+public / empty / sync fail | `FavoriteTest` |
| Sleep/repeat tags + callbacks | `PlayerSurfacesUxTest` |
| Gate | assembleDebug + targeted tests |

## OOS

YT dismiss-session; Dual/Cast flatten; Navidrome `createShare` guest links.
**Follow-up:** Cast Continuous Play (UX honesty / optional enable) — see `QUEUE_AUDIT_ROUND2_BEHAVIOR_REPORT.md`.
