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

## Search → leaf anchor

- Hub search click builds `settings/<leaf>?anchor=<key>` (URL-encoded).
- Keyword map e.g. `clear cache` → `clear_cache` on Storage; leaf scrolls via `BringIntoViewRequester`.
- Hub summaries resolve from `strings_settings.xml` (Context-aware `summaryProvider`).

## Section labels

- `SectionLabel` uppercases title (restored convention).
