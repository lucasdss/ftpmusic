# Play release 1.16.0

**versionCode:** 24 · **versionName:** 1.16.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Why this release

Post-1.15.0 artist Play All membership split (ADR-0145), Desired origin/bio +
library-match hardening, Home riding chrome under AppHeader, plus pre-release
review fixes (Desired apostrophe merge, alias tribute false positive, Last.fm
bio polish).

## Changelog

- **Artist track membership (ADR-0145)** — Songs / Play All join attribution on
  track id (not whole VA/OST albums). Albums tab unchanged. Play All default
  **shuffle albums**; Settings radios + year/name direction chips.
- **Desired / Discover polish** — artist origin labels, Last.fm bio fanout,
  apostrophe-normalized library match, Favorites catalog UX.
- **Home riding chrome** — opaque background under AppHeader reveal (Library
  parity); mini bottom pad preserved (ADR-0143).
- **Desired apostrophe merge** — title-bridge SQL folds curly/smart apostrophes;
  curly pre-upgrade rows merge with straight re-adds.
- **Alias tribute guard** — pipe-delimited `search_aliases`; whole-entry match;
  multi-candidate LIKE + Kotlin filter (no “Australian Pink Floyd Show” → Pink
  Floyd false “In library”).
- **Last.fm bio** — strip trailing “Read more…”; MBID miss falls back to name.
- **Discover stub ids** — `ext:` hash uses normalized name (curly/straight parity).

## Upgrade paths (do not wipe)

| From | App | Room | To |
|------|-----|------|-----|
| Play 1.14.0 | 22 | 67 | 24 / Room 68 |
| Play/internal 1.15.0 | 23 | 68 | 24 / Room 68 |
| Fresh install | — | — | 24 / Room 68 |

Downgrade 68 → older APK fails closed (Room refuse open) — not a wipe.

**Note:** Legacy space-joined `search_aliases` only match exact full-string
equality until Metadata enrich rewrites them with `|`. Exact name / MBID /
normalized-name paths unchanged.

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

- [ ] **Upgrade from 1.15.0 (23)** with existing library + queue + downloads (no wipe)
- [ ] Profile / About shows `1.16.0 (24)`
- [ ] Artist detail Play All: default shuffle albums; Songs exclude VA co-artists
- [ ] Settings → Play All order radios + year/name chips
- [ ] Desired: re-add “Guns N’ Roses” / “Guns N' Roses” → single row
- [ ] Discover “Pink Floyd” does **not** false-hit tribute artist in library
- [ ] Discover / Favorites: origin + bio; Add hidden when in library
- [ ] Home: scroll under AppHeader; mini hide/reveal last rows not clipped
- [ ] Android Auto root browse still works
- [ ] Cast / Share diagnostics still work

## How testers export diagnostics

1. Open **Settings** → **ABOUT / DIAGNOSTICS**
2. Confirm version shows `1.16.0 (24)`
3. Tap **Share diagnostics**

## Out of scope / known deferrals

- Crashlytics / Sentry
- `fallbackToDestructiveMigration`
- Room rewrite of legacy space-joined aliases (re-enrich only)
- Catalog N+1 Room lookup batching
