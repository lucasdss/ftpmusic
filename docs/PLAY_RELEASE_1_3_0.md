# Play release 1.3.0

**versionCode:** 5 · **versionName:** 1.3.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Continuous Play** — autoplay tail into context queue; offline-safe journal
  picks; in-queue Autoplay section + toggle (ADR-0053).
- **Surprise Me** — decoupled from Continuous Play (ADR-0052).
- **Favorites** — Liked | Disliked segment; `disliked_at` persistence.
- **Design / nav** — brand tokens + detail chrome; market-aligned bottom nav
  (Favorites kept); Settings stack-back + 48dp chrome (ADR-0054–0056).
- **Typography** — Outfit/Inter theme; track titles `textHeadingS` Medium;
  no baked theme colors; asp-paired lineHeight (ADR-0057–0058).

## Build artifact

```bash
cd compose
./gradlew :app:bundleRelease
# Optional full APK check:
./gradlew :app:assembleRelease
```

Signed output (when `keystore.properties` is present):

`compose/app/build/outputs/bundle/release/app-release.aab`

Upload that AAB to Play Console → Internal testing → promote when OK.

## Pre-upload gates

```bash
make test
cd compose && ./gradlew :app:lintVitalRelease
cd compose && ./gradlew :app:bundleRelease
```

## Tester checklist

- [ ] Fresh install / upgrade from 1.2.0 (4)
- [ ] Save server URL + Test Connection
- [ ] Library sync completes (Settings → Library Metrics)
- [ ] Bottom nav: Home / Search / Library / Favorites; Settings via gear
- [ ] Favorites Liked | Disliked segments work
- [ ] Continuous Play on: autoplay section appears near end of queue
- [ ] Nav selected label is teal; track list titles Medium weight
- [ ] Profile / About shows `1.3.0 (5)`
- [ ] Share diagnostics → paste into bug report

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.3.0 (5)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- `IMAGE_DIAGNOSTICS` default-on in production
- Closed-testing 12-tester → production promote
