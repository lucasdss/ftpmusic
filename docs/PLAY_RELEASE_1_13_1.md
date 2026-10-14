# Play release 1.13.1

**versionCode:** 21 · **versionName:** 1.13.1

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Why this patch

Post-1.13.0 ADR-0139 pull-expand needed upgrade-safety fixes before field
rollout past Room 65. Dual-path upgrades must stay non-destructive.

## Changelog

- **Room 66–67** — `sync_skip_albums` + `fail_count` (ADR-0139); additive only.
- **FULL album-list fail-closed** — zero-new-id / max-pages abort; no catalog wipe.
- **Playlist inbound safety** — `upsertTracksPreserveCache`; abort clear when
  `songCount > 0` but entries missing/malformed.
- **Skip-queue honesty** — keep exhausted rows (`fail_count >= 10`); exclude from
  DELTA pending; FULL/force re-arms. Empty `song[]` fails when `songCount > 0`.
- **Soft-stop watermarks** — consecutive-fail soft-stop no longer advances
  FULL/DELTA cadence or `metadata_version` stamp (ADR-0068); UI still completes
  without `phase=error`.
- **FGS timeout** — `onTimeout` cancels sync Jobs then stops dataSync FGS.
- Carries 1.13.0 features (Auto peer-parity, Play Next, LATE queue, chrome).
- Post-review notes: `docs/POST_1_13_0_RELEASE_REVIEW_BEHAVIOR_REPORT.md`.

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 1.12.0 | 19 | 64 | 21 / Room 67 via 65→66→67 |
| Play/internal 1.13.0 | 20 | 65 | 21 / Room 67 via 66→67 |

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

- [ ] **Upgrade from 1.12.0 (19)** with existing library + queue + downloads (no wipe)
- [ ] **Upgrade from 1.13.0 (20)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `1.13.1 (21)`
- [ ] After first sync: album count not collapsed; downloaded playlist tracks still play
- [ ] Imported playlists retain entries after background sync
- [ ] Settings → Add to Queue placement still works
- [ ] Android Auto root browse still works
- [ ] Cast / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.13.1 (21)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Soft-stop consecutive-fail race under parallel batches
- Genre densify page safety cap
- Cast JumpTo throw UX on Auto Queue seek
