# Settings Nav Hub Fixup — Behavior Report

Caveman terse. Post–ADR-0149 polish (release review `v1.16.1` → HEAD).

## Header

- Hub `settings`, leaf `settings/*`, and `downloads` → global AppHeader **hidden**.
- Leaves keep `SettingsPageScaffold` / `DetailBackButton` only (no double chrome).

## Resync

- Start from Network leaf → `syncing?returnTo=<encoded origin>`.
- Complete → navigate origin with `launchSingleTop` + pop syncing.
- Hub leaf pushes also use `launchSingleTop`.

## Section labels

- `SectionLabel` uppercases title (restored convention).

## Out of scope here

- Full leaf i18n / search scroll-anchor (tracked separately if still open).
