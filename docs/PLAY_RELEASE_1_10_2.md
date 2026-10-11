# Play release 1.10.2

**versionCode:** 17 · **versionName:** 1.10.2

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Cold-start fail-soft (ADR-0122)** — fix 1.10.1 upgrade force-close that was
  **not** a Room migration (schema stayed v62). Harden issue-tray Room Flow,
  queue restore, and reaction notification rebuild so existing data cannot kill
  the process on open.
- Includes 1.10.1 features/fixes (track chrome, BT resume, system thumbs, issue
  tray, disc COALESCE, BT debounce).

## Build artifact

```bash
cd compose
./gradlew :app:bundleRelease
```

Signed output (when `keystore.properties` is present):

`compose/app/build/outputs/bundle/release/app-release.aab`

**Native debug symbols:** run `make bundle-release` (includes `verify-native-symbols`).

## Pre-upload gates

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```

## Tester checklist

- [ ] **Upgrade from 1.10.1 (16) with existing library + queue** (do not wipe)
- [ ] Profile / About shows `1.10.2 (17)`
- [ ] App opens on first launch after upgrade
- [ ] Issue tray still works (offline / unreachable)
- [ ] Queue restores after kill (or fails soft without crash)
- [ ] Shade like/dislike still present when playing
- [ ] Share diagnostics still works

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.10.2 (17)`
3. Tap **Share diagnostics**

## Out of scope

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Play Developer API upload automation
