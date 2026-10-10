# ADR 0113 — Play Settings Update Check

Date: 2026-10-10
Status: Accepted
Related: ADR 0048 (About / Diagnostics), Play release pipeline (ADR 0018)

## Context

Users need a way to learn when a newer Play build exists. GitHub Releases,
Home banners, and header gear badges were considered and rejected for v1
(Play-only distribution + Settings-only UX).

## Decision

1. **Source:** Google Play In-App Updates API (`app-update` / `app-update-ktx`)
   only. No GitHub release polling.
2. **UX:** Settings → About / Diagnostics → **Check for updates** (manual).
   No cold-start banner. No settings-icon badge.
3. **Update style:** Soft **flexible** flow when Play allows it; otherwise open
   the Play Store listing (`market://` then HTTPS fallback). Never block the app.
4. **Same-session install:** Register `InstallStateUpdatedListener` while a
   flexible update is in flight; on `DOWNLOADED` show **Restart to install** and
   call `AppUpdateManager.completeUpdate()`. Without this, downloads never apply.
5. **In-progress mapping:** `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` maps to
   `InProgress` (observe + complete), not a fresh “Update available” CTA.
6. **Seam:** `AppUpdateChecker` / `PlayAppUpdateChecker` keep Play SDK types out
   of the ViewModel; unit tests use a fake checker. Play Task await uses
   `suspendCancellableCoroutine`.
7. **No auto-check** on Settings open (privacy + noise).

## Consequences

- Sideload / `make install` debug builds typically get `UPDATE_NOT_AVAILABLE`
  or an error — UI shows Up to date / Error + optional Open Play Store; never
  a false “update available” without Play confirmation.
- Full flexible-update resume UI after process death remains out of scope for
  v1; a later manual check may still surface `InProgress` / Ready if Play
  reports it.
- About card gains update row states (Idle/Checking/Available/InProgress/
  ReadyToInstall/Error); diagnostics export unchanged.
