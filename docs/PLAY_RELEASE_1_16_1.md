# Play release 1.16.1

**versionCode:** 25 · **versionName:** 1.16.1

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Why this release

Post-1.16.0 correctness hardening: Android Auto car-media declaration, connect
auth gate, playlist sync integrity, metrics weight preserve, reachability probe
races, genre disc/track preserve, scroll pass 8, BT A17 seat-only market parity,
plus pre-release review fixes (stale Resume cancel, info-notif honesty, ping
`requireOk`).

## Changelog

- **Android Auto car media (ADR-0146)** — declare media so Auto shows browse,
  not mirrored phone UI.
- **Connect auth gate** — Subsonic `status=failed` blocks credential persist/sync.
- **Playlist sync** — repeated query params + fail-closed full-replace songCount.
- **Metrics upsert preserve** — sparse replace no longer zeros play_count /
  cache / star; Profile listening polish.
- **Reachability races** — config-read throw no longer crashes keepalive; ping
  `requireOk` so auth death marks unreachable.
- **Genre populate** — preserve disc/track + enrichment; album order disc→track.
- **Scroll pass 8 (ADR-0147)** — consistent prefetch windows, likes decode size,
  loadMore dedup hygiene.
- **BT A17 seat-only (ADR-0148)** — happy path no app Resume; cancel stale
  Resume `1002` on info `1003` (empty / offline-no-cache); info notif
  `info_notif_disabled` telemetry.

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 1.15.0 | 23 | 68 | 25 / Room 68 |
| Play/internal 1.16.0 | 24 | 68 | 25 / Room 68 |
| Fresh install | — | — | 25 / Room 68 |

No Room migration this cut.

## Build artifact

```bash
make bundle-release
```

Signed output (when `keystore.properties` is present):

`compose/app/build/outputs/bundle/release/app-release.aab`

## Pre-upload gates

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```

## Tester checklist

- [ ] **Upgrade from 1.16.0 (24)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `1.16.1 (25)`
- [ ] Android Auto: browse tree (not phone mirror) on HU
- [ ] Connect wrong password → error + Retry; credentials not persisted
- [ ] Playlist edit/sync: local matches server songCount after full-replace
- [ ] Profile play counts survive library re-sync / star refresh
- [ ] Offline/airplane: Offline chip honest; recover after server up
- [ ] Multi-disc album Auto / queue order disc then track
- [ ] Favorites / Artist / Playlist fling: covers + loadMore stable
- [ ] A17 BT: seat-only (no happy-path Resume); empty/offline → info only
- [ ] SELECTED + null MAC still shows actionable Resume
- [ ] Cast / Share diagnostics still work (`btResumeEvents` present)

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.16.1 (25)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- Playlist large-N GET URL chunking
- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- A17 HU AVRCP device matrix (validate on hardware)
