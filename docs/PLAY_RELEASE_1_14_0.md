# Play release 1.14.0

**versionCode:** 22 · **versionName:** 1.14.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Why this release

Ships three post-1.13.1 feature ADRs (update tray, artist Play All order,
artist-view membership) plus Songs pagination / probe-cancellation correctness
fixes found in pre-release review.

## Changelog

- **Update available in issue tray (ADR-0140)** — cold-start + post-sync Play
  In-App Update probe; `UPDATE_AVAILABLE` in AppHeader tray with snooze;
  Settings seeds from holder.
- **Artist Play All order (ADR-0141)** — Settings preference (shuffle default /
  album year asc|desc / title); full catalog; artist Shuffle chrome removed.
- **Artist-view membership (ADR-0142)** — Albums / Songs / Play All / Download
  All / Add to Playlist share one membership rule (track `artist_id` **or**
  album in the merged Albums set).
- **Songs pagination keyset** — composite `(title, id)` cursor so duplicate
  titles are not skipped when a page ends mid-group.
- **Probe cancellation hygiene** — cooperative `CancellationException` rethrown
  from WM / cold-start wrappers; `TimeoutCancellationException` stays fail-soft
  inside `AppUpdateProbe` so a Play timeout cannot fail a successful sync.
- **Dead narrow artist DAO APIs removed** — only ADR-0142 membership queries remain.
- Room schema unchanged (**67**). No migration.

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 1.12.0 | 19 | 64 | 22 / Room 67 via 65→66→67 |
| Play/internal 1.13.0 | 20 | 65 | 22 / Room 67 via 66→67 |
| Play/internal 1.13.1 | 21 | 67 | 22 / Room 67 (no schema change) |

Downgrade 67 → older APK fails closed (Room refuse open) — not a wipe.

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

- [ ] **Upgrade from 1.13.1 (21)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `1.14.0 (22)`
- [ ] Artist detail Songs tab: artists with many duplicate titles (e.g. "Intro") load all rows on scroll
- [ ] Artist Play All respects Settings → Artist Play All order (default shuffle)
- [ ] Artist Albums / Songs / Play All / Download All stay consistent for OST / Various Artists albums
- [ ] AppHeader issue tray shows update-available when Play reports one; snooze dismisses
- [ ] Settings → Check for updates still works
- [ ] Android Auto root browse still works
- [ ] Cast / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.14.0 (22)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Artist display-name collision merge (ADR-0142 by design)
- Full-catalog Play All memory hitch for huge artists
- Title browse vs Play All `COLLATE NOCASE` asymmetry
- Soft-stop consecutive-fail race under parallel batches
