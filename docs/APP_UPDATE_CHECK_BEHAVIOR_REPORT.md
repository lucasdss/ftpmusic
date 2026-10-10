# APP_UPDATE_CHECK — Behavior Report

Caveman map. Feature: Play Settings update check (ADR-0113).

## Scope

- Settings → ABOUT / DIAGNOSTICS only
- Play Store API only
- Manual tap check
- Soft flexible update OR open listing
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
Idle --tap--> Checking --Play--> UpToDate | Available | Error
Available --tap--> flexible flow OR OpenPlayStore Intent
Error --tap--> Checking (retry)
Error --Open Play Store--> market/https listing
Checking --ignore second tap--> stay Checking
```

## Edges

| Case | Behavior |
| --- | --- |
| Play install + newer version + flexible OK | `Available(flexible=true)` → Play flexible UI |
| Play install + newer + flexible blocked | `Available(flexible=false)` → Play listing Intent |
| Already current | `UpToDate` subtitle “You're up to date” |
| Offline / Play fail / non-Play | `Error(message)` + Open Play Store button |
| Sideload debug | Usually UpToDate or Error — never fake Available |
| Process death mid-flexible | Next Settings open = Idle (no resume UI v1) |
| Play UI dismiss | State unchanged; user can tap again |

## UI tags

- `settings_check_updates`
- `settings_start_update`
- `settings_open_play_store` (Error only)

## Non-goals (v1)

- Home top banner
- Header gear alert badge
- Immediate (blocking) update
- GitHub Releases
- WorkManager polling
