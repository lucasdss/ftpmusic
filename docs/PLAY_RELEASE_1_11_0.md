# Play release 1.11.0

**versionCode:** 18 · **versionName:** 1.11.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Cast** — force scan (“Search again”), session heartbeat weak signal, volume
  seed/resync, hard connect abort (Stop connecting / This phone). Abort flag
  survives session-ended teardown; late handshake for superseded device rejected
  (ADR-0123, ADR-0124).
- **Collection covers** — square pan/zoom crop after Device pick; JPEG ≤1024px /
  hard ≤512KiB; distinct export-failure toast; unsaved Daily Mix staged covers
  cleaned on re-crop (ADR-0125).
- **Library chrome** — collapse header + list together (no black strip under chips).
- **Auto-center now playing** — Settings → Appearance toggle (default ON); Mix /
  Playlist / Album / Artist tracks / Queue follow current track with ~3s user-scroll
  suppress; queue only re-centers when anchor left (ADR-0126).
- Includes 1.10.2 cold-start fail-soft baseline (ADR-0122).

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

- [ ] **Upgrade from 1.10.2 (17) with existing library + queue** (do not wipe)
- [ ] Profile / About shows `1.11.0 (18)`
- [ ] Cast: Search again; Stop connecting mid-handshake stays local; switch A→B
- [ ] Cast: first volume raise after connect is not a jump to 100%
- [ ] Device cover: crop → Use writes square JPEG; cancel discards
- [ ] Library / Favorites: fling mid-collapse — no black gap under chips
- [ ] Auto-center ON: open playlist with deep current track → centers; drag then
      skip follows after ~3s quiet
- [ ] Auto-center OFF: no list jump
- [ ] Queue: scroll into History, skip track → returns to now-playing anchor
- [ ] Issue tray / queue restore / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.11.0 (18)`
3. Tap **Share diagnostics**

## Out of scope

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Play Developer API upload automation
