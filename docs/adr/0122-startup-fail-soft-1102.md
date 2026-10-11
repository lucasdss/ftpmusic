# ADR 0122 — Cold-start fail-soft (1.10.2)

Date: 2026-10-11  
Status: Accepted  
Related: ADR-0120 (issue tray), ADR-0117 (BT seat / queue restore),
ADR-0118 (media reactions), ADR-0018 (Play release)

## Context

Users upgrading 1.10.0 → 1.10.1 saw force-close on open. Reinstall fixed it.
Historical instinct blamed Room migrations, but schema stayed at v62 with the
same identity hash — no migration runs on that path.

New 1.10.1 cold-start surfaces (Eagerly issue tray Room Flow, queue restore,
reaction notification thumbs) could throw on existing data. Scopes used
`SupervisorJob` without `CoroutineExceptionHandler`, so uncaught failures
killed the process.

## Decision

1. Document that 1.10.0→1.10.1 was **not** a missing migration.
2. Fail-soft `AppIssueAggregator`: CEH + Flow.catch → empty tray.
3. Fail-soft queue restore via `QueueRestoreSafely` (catch + latch in finally).
4. Guard reaction notification button build; never recursive-crash in
   `notifyChanged` fallback.
5. Keep **no** `fallbackToDestructiveMigration`.
6. Ship as **1.10.2 / versionCode 17**.

## Consequences

- Upgrade-with-data must open even if conflicted-playlist Flow or queue restore
  fails; tray/queue may be empty until next successful refresh.
- Migration 61→62 unit test added for 1.9.x→1.10.x hygiene (separate path).
