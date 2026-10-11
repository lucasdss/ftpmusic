# 1.10.1 startup force-close — behavior report

Date: 2026-10-11  
Status: fixed in **1.10.2** (ADR-0122)  
Upgrade path: 1.10.0 (15) → 1.10.1 (16); reinstall cured

## Verdict

**Not a Room migration failure.** DB stays version **62** with identical identity
hash. Uninstall/reinstall wiped persisted queue/playlists that tripped **new**
1.10.1 cold-start code without fail-soft.

## Confirmed non-cause

- Missing migration / schema bump (Entities / AppDatabase / schemas unchanged)
- `disc_number` COALESCE SQL (runs in sync worker, already caught)

## Likely causes (reinstall-cures-data)

1. `AppIssueAggregator` Eagerly + `observeConflicted` — no Flow.catch / CEH
2. `MediaService` queue restore — try/finally without catch
3. Reaction notification rebuild double-throw on thumb buttons

## Fixes (1.10.2)

- Aggregator: CEH + `mapConflictedFailSoft` + catch on issues/header maps
- Queue restore: `QueueRestoreSafely` + clear BT autoplay flag on failure
- Notification: per-button try/catch; notifyChanged fallback also guarded
- Hygiene: unit test for migration 61→62

## Tester note

Upgrade from 1.10.1 **with existing library/queue** — do not wipe to validate.
