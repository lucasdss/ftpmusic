# ADR 0140 — Update-available issue tray

Date: 2026-10-14
Status: Accepted
Related: ADR-0113 (Play Settings update check), ADR-0120 (issue status tray),
ADR-0122 (startup fail-soft), ADR-0045 (library sync)

## Context

ADR-0113 shipped manual Play In-App Updates in Settings and rejected a Home
banner / header gear badge for v1. Users still miss new Play releases until
they open Settings and tap Check for updates.

ADR-0120 already provides a global AppHeader issue tray for connectivity and
other alerts. A soft “update available” row fits that chrome without reviving
yellow Home banners.

## Decision

1. **Source:** Keep Google Play In-App Updates API only (ADR-0113). No GitHub /
   APK channels.
2. **Probe triggers:** Cold start (`FtpmusicApp`) and after successful periodic
   library sync (`SyncScheduleWorker`). In-process 5-minute throttle avoids
   cold-start + immediate WM double-hit. Probe failures never fail WM / startup.
3. **Surface:** New `AppIssueKind.UPDATE_AVAILABLE` in the ADR-0120 tray
   (`SystemUpdate` icon). Primary CTA → Settings (existing flexible update row).
   Secondary → Dismiss (“Not now”).
4. **Snooze:** Dismiss persists `dismissed_update_version_code` in
   `ftpmusic_sync` prefs. Issue stays hidden while Play reports the same (or
   lower) `availableVersionCode`; a higher code shows again. Transient
   `Unavailable` does not clear a previously confirmed update (avoids tray
   flicker); only `UpToDate` clears.
5. **Install:** Still happens only in Settings (flexible / Play listing). Tray
   does not start the update flow.
6. **Supersession:** ADR-0113 “no home surface / no auto-check” non-goals are
   superseded for the issue-tray auto-probe path. Settings remains manual
   check + install surface; no auto-check on Settings open.

## Consequences

- Sideload / debug builds still never fake Available without Play confirmation.
- Update reminder coexists with connectivity issues (priority 80 — below net).
- Tests cover holder publish/dismiss, probe throttle/errors, builder row,
  SyncScheduleWorker success-path probe.
