# Play release 2.0.0

**versionCode:** 26 · **versionName:** 2.0.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Why this release

Major cut after post-1.16.1 correctness review: Settings grouped nav hub,
collection cover import/edit resilience, Home Recently Added TTL/ratings
hardening, and play-queue download terminal classification (CT sticky vs
transient retry). Semver major marks the Settings IA + cover/edit surface
change set.

## Changelog

- **Settings IA (ADR-0149)** — grouped nav hub, searchable leaves, localized
  summaries + scroll anchors; AppHeader hidden on settings leaves / downloads.
- **Collection covers (ADR-0150)** — Device import downscale, long-press edit
  sheet, OOM-safe decode; rotation no longer stacks sheet over crop.
- **Home Recently Added (ADR-0109)** — TTL on RESUMED; single-flight fetch;
  star ratings preserved across snapshot/revision races.
- **Download queue (ADR-0117 / 0022)** — play-queue remainder `isDownload=false`;
  CT rejects → `failed_ct` sticky; retry-exhausted `failed` requeueable for
  prefetch.
- **Post-1.16.1 review P1s** — see
  [POST_1_16_1_RELEASE_REVIEW_BEHAVIOR_REPORT.md](POST_1_16_1_RELEASE_REVIEW_BEHAVIOR_REPORT.md).

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 1.16.1 | 25 | 68 | 26 / Room 68 |
| Play 1.16.0 | 24 | 68 | 26 / Room 68 |
| Play 1.15.0 | 23 | 68 | 26 / Room 68 |
| Fresh install | — | — | 26 / Room 68 |

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

- [ ] **Upgrade from 1.16.1 (25)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `2.0.0 (26)`
- [ ] Settings hub → each leaf; search → anchor scroll (e.g. clear cache)
- [ ] Resync from Network leaf returns to Network (not duplicate hub)
- [ ] Playlist / Mix long-press cover → Library / Device / Clear; rotate mid-crop → no sheet on top of crop
- [ ] Home Recently Added stars survive resume + background sync
- [ ] Cold start Home: single newest fetch (no double network blip)
- [ ] Play-queue prefetch: CT reject stays skipped; after retry-exhausted fail, later transition can requeue
- [ ] Explicit Download tap still retries sticky CT rejects
- [ ] Cast / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **About**
2. Confirm version shows `2.0.0 (26)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- D6 per-domain SettingsViewModel split
- Settings search query rotation persistence / keyword precision
- Cover tmp orphan sweep + cover toast i18n
- Playlist large-N GET URL chunking
- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
