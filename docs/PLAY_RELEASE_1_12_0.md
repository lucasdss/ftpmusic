# Play release 1.12.0

**versionCode:** 19 · **versionName:** 1.12.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Desired wishlist (ADR-0129)** — Favorites **Desired** hub; add from Discover /
  stub detail; share text export; Room `desired_items` (v64); cross-key merge
  (name/Deezer → MBID) with Mutex-serialized writes.
- **Discover (ADR-0130)** — Deezer + ListenBrainz popularity fan-out; Desired
  badges use bidirectional `probeKeys` ∩ row aliases so mb↔dz upgrades keep
  bookmark state; remove via `removeMatching` (not exact key).
- **Profile / Last.fm (ADR-0128)** — Pause-aware heard time; half-duration / 4m
  threshold (no AUTO under-threshold); tops by play count; activity charts.
- **Shell chrome (ADR-0127)** — Denser nav / header.
- **Fix** — Discover Desired badge false / remove no-op after identity upgrade;
  concurrent `addDesired` duplicate-row race.

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

- [ ] **Upgrade from 1.11.0 (18) with existing library + queue** (do not wipe)
- [ ] Profile / About shows `1.12.0 (19)`
- [ ] Favorites → Desired: add from Discover, filter, share text
- [ ] Discover: add Desired on Deezer hit → badge stays after MB merge; remove clears
- [ ] Profile: Week/Month charts; tops show plays; short tracks ≤30s do not scrobble
- [ ] Nav / header density still readable
- [ ] Cast / queue / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.12.0 (19)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Play Developer API upload automation
- Scrobble when `player.duration` stays `TIME_UNSET` (ADR-0128 follow-up)
- Discover list paint during typeahead debounce (anti-flicker; cancel rethrows)
- Title-bridge false merges on empty-artist Desired rows (pre-existing)
