# Update-available alert — behavior report

ADR: 0140. Surface: AppHeader issue tray (not Home banner).

## Triggers
- Cold start: `FtpmusicApp.onCreate` → `AppUpdateProbe.probe()` (IO, fail-soft)
- Library sync: `SyncScheduleWorker` after successful sync → same probe
- Throttle: skip if last probe &lt; 5 minutes (in-process)

## Show / hide
- Show when Play returns `UpdateAvailable` or `InProgress` and
  `availableVersionCode` &gt; dismissed prefs value
- Hide on `UpToDate` / dismissed same code
- `Unavailable` (Play blip) keeps last confirmed tray row; does not consume
  probe throttle so a retry can follow sooner
- Never fake Available on sideload

## Tray UX
- Kind: `UPDATE_AVAILABLE` — icon `SystemUpdate`
- Title: Update available
- Body: A newer version is ready. Open Settings to update.
- Primary: Update → navigate Settings
- Secondary: Not now → snooze this versionCode

## Snooze
- Pref: `ftpmusic_sync` / `dismissed_update_version_code`
- Next higher Play versionCode → show again

## Edge cases
| Case | Behavior |
|------|----------|
| Offline probe | Soft fail; no issue |
| Sync skipped | No probe that path; cold start still covers |
| Process death | Prefs keep dismiss; next probe republishes |
| User installs via Settings | Next probe UpToDate → clear |
