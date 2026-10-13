# Play release 1.13.0

**versionCode:** 20 · **versionName:** 1.13.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Android Auto peer-parity (ADR-0131/0132)** — Downloads / Mixes / Genres /
  Live Radio browse; in-Auto search; Dual adopt for voice/Auto seats; radio
  reaction gates; adopt suppress after local queue sync.
- **BT Resume tap (ADR-0133)** — Resume notification tap plays under A17 WIU;
  background A2DP still seat-only.
- **Play Next align (ADR-0134)** — Align Dual to live playhead before mutate;
  Exo insert at Dual index.
- **Add to Queue placement (ADR-0135)** — Settings toggle `manual_end` /
  `session_end` (LATE); Home album **Play Next**; queue sheet order matches
  Dual (Autoplay then Queued later).
- **Library chrome** — Fix black bar above mini on Library/Favorites fling.
- **Fix** — Persistence save verifies include `is_late`; Room migration 65.

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

- [ ] **Upgrade from 1.12.0 (19) with existing library + queue** (do not wipe)
- [ ] Profile / About shows `1.13.0 (20)`
- [ ] Android Auto: root shows Downloads/Mixes/Genres/Radio; search plays
- [ ] Settings → Add to Queue placement: session_end → Queued later after Autoplay
- [ ] Home album sheet Play Next inserts at PRIORITY front
- [ ] BT Resume notif tap starts playback (API 37+)
- [ ] Library / Favorites fling: no black bar above mini
- [ ] Cast / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.13.0 (20)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Play Developer API upload automation
- Compose UI gate for Library scroll / Auto HU hardware QA
