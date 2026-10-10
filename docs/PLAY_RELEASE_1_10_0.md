# Play release 1.10.0

**versionCode:** 15 · **versionName:** 1.10.0

Ship signed AAB to **internal testing** first, then promote when green.
Build locally; upload in Play Console (no CI upload).

## Changelog

- **Fixed collection covers** — pin Daily Mix / playlist art from library album or
  device gallery; lettermark when nothing else (ADR-0115, Room v62).
- **Library scroll chrome** — chips/search ride header collapse via
  `graphicsLayer`; mini-player hides on vertical scroll, returns after idle
  (including drag without fling) (ADR-0114).
- **Cover cleanup** — Library “Remove locally” deletes pinned LOCAL cover files;
  new Custom Daily Mix gallery files rekey from `mix_new_*` to `mix_{id}` on save.
- **Gallery import safety** — reject >8 MiB raw copies; downsample long edge to 2048.
- **Playlist list fallback art** — primary artist/album meta loaded with montages.
- **Mix detail** — missing LOCAL file falls through to montage instead of empty fixed.

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

**Native debug symbols:** release sets `ndk.debugSymbolLevel = "SYMBOL_TABLE"`.
Run `make bundle-release` (includes `verify-native-symbols`).

## Pre-upload gates

```bash
make test-report
make quality
cd compose && ./gradlew :app:lintVitalRelease
make bundle-release   # :app:bundleRelease + verify-native-symbols (ADR-0018)
```

## Tester checklist

- [ ] Upgrade from 1.9.1 (14) (or fresh install)
- [ ] Profile / About shows `1.10.0 (15)`
- [ ] Custom Daily Mix: set library cover + gallery cover; save; reopen; covers stick
- [ ] New mix: pick gallery cover → save → delete mix → no orphan under `collection_covers/`
- [ ] Playlist detail: Library / Device / Clear cover; Library list shows art
- [ ] Library long-press Remove locally on playlist with LOCAL cover → file gone
- [ ] Mix detail with dead LOCAL path → montage/lettermark (not blank fixed)
- [ ] Library Albums fling → chips/search enter header; Settings not tappable collapsed
- [ ] Mini hides on scroll; returns ~200ms after stop (including slow drag, no fling)
- [ ] Nav bar never hides; Share diagnostics still works

## How testers export diagnostics

1. Open **Settings**
2. Scroll to **ABOUT / DIAGNOSTICS**
3. Confirm version shows `1.10.0 (15)`
4. Tap **Share diagnostics** and send the text
5. Attach that text to Play feedback or GitHub issue — no passwords / URLs included

## Out of scope this release

- Crashlytics / Sentry
- Separate `qa` product flavor
- Play Developer API / Fastlane upload automation
- Closed-testing / production promote
- Orphan cover sweeper / N+1 montage batching (P2)
- Navidrome native playlist image upload
