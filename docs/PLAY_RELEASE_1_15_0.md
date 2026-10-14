# Play release 1.15.0

**versionCode:** 23 · **versionName:** 1.15.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Why this release

Post-1.14.0 Desired/Discover catalog enrichment (Room 68, share JSON v2) plus
Home/Search mini-player reveal list-padding fix, and pre-release correctness
fixes (album in-library false positive, blank cover URL, Desired subtitle).

## Changelog

- **Desired search enrichment (ADR-0144)** — Deezer/Last.fm thumbs +
  description on Discover and Favorites “Add from catalog”; persist
  `image_url` / `description` / `extra_json` on `desired_items`; hide Add when
  already in library (Discover parity).
- **Room 68** — additive nullable enrichment columns; coalesce on Desired merge.
- **Share Desired JSON v2** — Intent body is versioned JSON
  (`schema: "ftpmusic.desired.v2"`) with embedded `textSummary` human lines.
  Plain-text consumers must parse JSON or read `textSummary`.
- **Home / Search mini reveal padding (ADR-0143)** — Lazy `contentPadding`
  reserves mini height while a track is loaded so hide→reveal does not clip
  the last rows (accepted double inset when mini is visible).
- **Album in-library false positive** — `DiscoverLibraryResolver.resolveAlbum`
  requires non-blank artist (DAO null-artist is a title wildcard).
- **Blank cover URL** — `SearchCoverThumb` treats blank/whitespace URL as
  placeholder, not a Coil load.
- **Desired subtitle** — keeps `artist · description` (or `artist · source`).

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 1.13.1 | 21 | 67 | 23 / Room 68 via 68 |
| Play/internal 1.14.0 | 22 | 67 | 23 / Room 68 |
| Fresh install | — | — | 23 / Room 68 |

Downgrade 68 → older APK fails closed (Room refuse open) — not a wipe.

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

- [ ] **Upgrade from 1.14.0 (22)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `1.15.0 (23)`
- [ ] Favorites Desired → Add from catalog: thumbs, In library gate, description
- [ ] Search Discover: thumbs + in-library; Add Desired hidden when in library
- [ ] Share Desired: chooser body is JSON with `ftpmusic.desired.v2` + `textSummary`
- [ ] Home / Search: scroll to end, hide mini by scrolling down, scroll up to
  reveal — last rows stay above mini (not clipped)
- [ ] Album catalog hit without artist does **not** show false “In library”
- [ ] Android Auto root browse still works
- [ ] Cast / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.15.0 (23)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Deduping `DesiredRepository.matchAlbum` into `DiscoverLibraryResolver`
- Album resolve-by-MBID path
- Last.fm default placeholder star images
- Catalog N+1 Room lookup batching
