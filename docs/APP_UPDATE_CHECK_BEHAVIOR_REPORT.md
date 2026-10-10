# APP_UPDATE_CHECK — Behavior Report

Caveman map. Feature: Play Settings update check (ADR-0113).

## Scope

- Settings → ABOUT / DIAGNOSTICS only
- Play Store API only
- Manual tap check
- Soft flexible update OR open listing
- Same-session `completeUpdate` after flexible download
- No banner / no gear badge / no GitHub

## Files

| Layer | Path |
| --- | --- |
| Dep | `compose/app/build.gradle.kts` (`app-update`, `app-update-ktx`) |
| Seam | `data/update/AppUpdateChecker.kt` |
| Mapper | `data/update/UpdateCheckMapper.kt` |
| Impl | `data/update/PlayAppUpdateChecker.kt` |
| DI | `di/UpdateModule.kt` |
| State | `ui/settings/SettingsViewModel.kt` (`UpdateCheckUi`, `AppUpdateEvent`) |
| UI | `ui/settings/SettingsScreen.kt` (`UpdateCheckRow`) |

## States

```
Idle --tap--> Checking --Play--> UpToDate | Available | InProgress | Error
Available --tap--> flexible flow OR OpenPlayStore Intent
Available --flexible started--> InProgress
InProgress --InstallStatus.DOWNLOADED--> ReadyToInstall
ReadyToInstall --tap--> completeUpdate (Play restarts)
InProgress / check DEVELOPER_TRIGGERED--> observe install (no re-start)
Error --tap--> Checking (retry)
Error --Open Play Store--> market/https listing
Checking --ignore second tap--> stay Checking
```

## Edges

| Case | Behavior |
| --- | --- |
| Play install + newer version + flexible OK | `Available(flexible=true)` → Play flexible UI → InProgress → ReadyToInstall |
| Play install + newer + flexible blocked | `Available(flexible=false)` → Play listing Intent |
| Already current | `UpToDate` subtitle “You're up to date” |
| Offline / Play fail / non-Play | `Error(message)` + Open Play Store button |
| DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS | `InProgress` (not Available); observe → ReadyToInstall if DOWNLOADED |
| Flexible download finished same session | `ReadyToInstall` → tap → `completeUpdate()` |
| Sideload debug | Usually UpToDate or Error — never fake Available |
| Process death mid-flexible | Next Settings open = Idle; no resume UI v1 (re-check may show InProgress if Play still tracks) |
| Play UI dismiss before download | Stay Available / InProgress; user can tap again |
| completeUpdate fail | `Error` + stop observe |

## UI tags

- `settings_check_updates`
- `settings_start_update`
- `settings_update_in_progress`
- `settings_complete_update`
- `settings_open_play_store` (Error only)

## Non-goals (v1)

- Home top banner
- Header gear alert badge
- Immediate (blocking) update
- GitHub Releases
- WorkManager polling
- Full process-death flexible resume UI
