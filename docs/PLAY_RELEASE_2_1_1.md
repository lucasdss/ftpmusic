# Play release 2.1.1

**versionCode:** 28 · **versionName:** 2.1.1 · **Room:** 71

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

Supersedes the 2.1.0 (27) testing cut: nine commits landed after `f31e3a7`,
Room advanced **68→71**, and post-cut review closed upgrade-safety + P1s below.

## Why this release

Everything in [PLAY_RELEASE_2_1_0.md](PLAY_RELEASE_2_1_0.md), plus:

- Sticky Daily Mix versioned active/pending pointers (Room 69)
- Collection cover packs + Saved library (Room 70)
- Cover-reactive detail backdrops / art palette cache (Room 71)
- BT always-autoplay on A2DP (ADR-0154)
- Continuous Play market timing + session Autoplay kill switch
- Sticky CT REMOVE_TERMINAL + Previous warm-before-seek
- Atomic Daily Mix activate; leave→activate survives nav-pop

## Changelog (delta since 2.1.0 notes)

- **Room 68→71** — sticky DM, user/genre covers, art palette cache. Real SQLite
  migration tests for 68→69 backfill, 69→70, 70→71, and full 68→71 chain.
- **Daily Mix activate** — status + pointers + cleanup in one Room transaction;
  MixDetail leave uses process-scoped `activatePendingDetached`.
- **Previous past sticky CT** — await Room warm (500ms budget) then seek; notification
  path shares `StickyPreviousBridge` with in-app SKIP_PREV.
- **Docs** — this file replaces the stale “Room 68 / no migration” matrix.

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 2.0.0 | 26 | 68 | **28 / Room 71** (68→69→70→71) |
| Play 1.16.1 | 25 | 68 | **28 / Room 71** |
| Local 2.1.0 testing cut | 27 | 68–71 | **28 / Room 71** |
| Fresh install | — | — | **28 / Room 71** |

Migrations are registered via `ALL_MIGRATIONS_71`. **No**
`fallbackToDestructiveMigration`.

## Build artifact

```bash
make bundle-release
```

Signed output (when `keystore.properties` is present):

`compose/app/build/outputs/bundle/release/app-release.aab`

## Pre-upload gates

```bash
cd compose && ./gradlew :app:testDebugUnitTest --tests 'com.lucasdss.ftpmusic.app.data.db.Room68To71MigrationTest'
cd compose && ./gradlew :app:assembleDebug
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release
```

## Tester checklist

- [ ] **Upgrade from 2.0.0 (26)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `2.1.1 (28)`
- [ ] Home Daily Mix cards still present after upgrade (active pointer backfill)
- [ ] Leave MixDetail with “update available” → re-enter shows new list (pending activate)
- [ ] Previous after process kill skips sticky `failed_ct` corpses (local player)
- [ ] Continuous Play: mid-album does **not** radioize; first burst on last user track; Queue Autoplay OFF clears tail
- [ ] BT A2DP connect autoplays when enabled (seat education / already-playing skip)
- [ ] Cover packs / Saved covers / genre defaults; detail screens show tinted backdrops
- [ ] Cold start → Share diagnostics: sync-health after prior silence
- [ ] Offline mid-queue with bad spans → playback **pauses** at burst bound
- [ ] Cast sticky CT remains seek-only (known limitation — [ADR-0150](adr/0150-remove-terminal-sticky-unplayable.md))
- [ ] Artist Songs sort / album track order / radio admin still work

## How testers export diagnostics

1. Open **Settings** → **About**
2. Confirm version shows `2.1.1 (28)`
3. Tap **Share diagnostics**

## Tester notes (residuals accepted for this cut)

- **Previous sticky warm** is best-effort (≤500ms Room probe). On a slow cold
  start the first Previous may briefly use an incomplete process registry; retry
  Previous once registry is warm.
- **Do not sideload an older APK** over 2.1.1 — Room has no downgrade path
  (destructive fallback is deliberately disabled).
- Leave→activate is process-scoped and idempotent (`leaveActivated` + pending
  pointer clear); it does **not** survive process death mid-write (Room txn rolls
  back; next open reconciles).

## Out of scope / known deferrals

- Cast sticky CT REMOVE_TERMINAL parity (ADR-0150 seek-only)
- CP MediaService E2E harness / cursor-based journal prefix / buffering UI
- Daily Mix freshness batching; empty-day process-memory sticky
- PlaySuppressionWatch mute detection
- ArtSeedExtractor montage perf instrumentation
- SyncHealthGuard OEM throttle E2E (manual OEM smoke only)
- Future targetSdk 37 BT/WIU car matrix completion
- Playlist large-N GET URL chunking; Crashlytics/Sentry
- `fallbackToDestructiveMigration`
