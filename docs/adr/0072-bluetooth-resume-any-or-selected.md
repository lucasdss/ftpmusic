# ADR 0072 — Bluetooth Resume: Any or Selected A2DP

Date: 2026-10-04
Status: Accepted
Supersedes: ADR-0071
Related: ADR-0019 (FGS), `docs/BT_RESUME_SYSTEM_MEDIA_BEHAVIOR_REPORT.md`

## Context

ADR-0071 shipped car-only allowlist resume. Users want generic Bluetooth prefs:
resume on **any** A2DP audio device, or only on **selected** bonded devices.
ACL-connected non-audio peripherals must not trigger autoplay.

## Decision

1. **Rename surface** to Bluetooth resume (`BtResumePolicy`, `BtConnectionReceiver`,
   `BtAutoplayStarter`, Settings “Bluetooth”).
2. **Modes:** `any` | `selected` (default `selected`). Master toggle opt-in OFF.
3. **A2DP-only trigger.** Drop `ACL_CONNECTED` from the resume receiver.
4. **Migration.** Read `KEY_CAR_BT_*` if `KEY_BT_*` absent; writes use `KEY_BT_*`.
5. **`ACTION_BT_AUTOPLAY`** (+ legacy `ACTION_CAR_BT_AUTOPLAY` alias for queued
   PendingIntents). Restore-path play gate renamed `btAutoplayRequested`.
6. Casting still wins (no-op while `PlayerHolder.isCasting`).

## Consequences

- Headphones/speakers/car all resume in `any` mode.
- Selected + empty allowlist remains a no-op.
- Docs: `BT_RESUME_SYSTEM_MEDIA_BEHAVIOR_REPORT.md`; ADR-0071 marked superseded.
