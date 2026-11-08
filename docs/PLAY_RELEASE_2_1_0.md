# Play release 2.1.0

> **Superseded for shipping:** use [PLAY_RELEASE_2_1_1.md](PLAY_RELEASE_2_1_1.md)
> (`versionCode` 28 / Room 71). This document describes the earlier testing cut
> (`f31e3a7`) and incorrectly claimed “Room 68 / no migration.”

**versionCode:** 27 · **versionName:** 2.1.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Why this release

Feature + correctness cut after 2.0.0: WorkManager sync self-heal, Source-error
auto-skip bounds, Continuous Play redesign (journal / prefetch / tail cap),
Daily Mix fill/freshness fixes, radio admin CRUD, artist songs browse sort +
album track order, BT car-connect parity, and post-release Cast/offline/BT P1s.

## Changelog

- **Sync heal** — HiltWorkerFactory restores periodic metadata sync; SyncHealthGuard
  triggers a bounded DELTA on foreground after silence + grace.
- **Playback errors** — SourceErrorClassification; circuit 3/15s; burst pause 8/120s;
  missing-album collapse + guarded purge; offline cached jumps pause at burst bound.
- **Continuous Play** — scored journal, 3-track low-water prefetch, ENDED retry,
  genre→artist→most-played→random fallback, autoplay tail cap 50 with Cast
  `RemoveThenAddAll` single-ack (+ heal on insert-after-remove fail).
- **Daily Mix** — soft artist cap/adjacency, listen-based freshness, history prune,
  empty-day vs all-fresh outcomes.
- **Radio admin (ADR-0151)** — create/update/delete gated on `adminRole`.
- **Artist / album sort (ADR-0152)** — Songs browse sort; album disc/track vs play-count.
- **BT / car** — seat education, FGS-deny flag clear, already-playing skip; no
  `notify()` when notifications disabled.
- **Post-2.0.0 review P1s** — see
  [POST_2_0_0_RELEASE_REVIEW_BEHAVIOR_REPORT.md](POST_2_0_0_RELEASE_REVIEW_BEHAVIOR_REPORT.md).

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 2.0.0 | 26 | 68 | 27 / Room 68 |
| Play 1.16.1 | 25 | 68 | 27 / Room 68 |
| Fresh install | — | — | 27 / Room 68 |

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

- [ ] **Upgrade from 2.0.0 (26)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `2.1.0 (27)`
- [ ] Cold start → Share diagnostics: sync worker heartbeat / `sync-health` lines appear after prior silence
- [ ] Force offline mid-queue with several bad cached spans → playback **pauses** (not endless jump); play resumes past
- [ ] Continuous Play: near end of context, new autoplay rows append; Cast session stays ≤50 autoplay; Cast disconnect/reconnect queue coherent
- [ ] Daily Mix: regenerates when stale; all-fresh day does not suppress later Home opens; new custom mix can generate same day
- [ ] Artist Songs: sort modes (most/least played, random, album, year, name); Play All order unchanged by browse sort
- [ ] Album detail: Appearance track order; Shuffle still shuffles
- [ ] Radio: admin sees create/edit/delete; non-admin denied by server
- [ ] BT car connect: FGS deny → Resume notif only when notifications enabled; already-playing reconnect does not pause
- [ ] Cast / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **About**
2. Confirm version shows `2.1.0 (27)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- CP MediaService E2E harness / cursor-based journal prefix / buffering UI
- Daily Mix freshness batching; empty-day process-memory sticky
- PlaySuppressionWatch mute detection
- Future targetSdk 37 BT/WIU car matrix completion
- Playlist large-N GET URL chunking; Crashlytics/Sentry
- `fallbackToDestructiveMigration`
