# Post-v1.10.0 release review — behavior report

Date: 2026-10-11  
Baseline: `v1.10.0` (versionCode 15)  
Target: `1.10.1` (versionCode 16)  
Status: P0 fixes + release cut

## Delta themes (6 commits)

| SHA | Theme |
|---|---|
| `8bf3565` | Track-number chrome + disc/track sync (ADR-0116) |
| `ae38c15` | Terminal CT fail + mix holes + A17 BT seat (ADR-0117) |
| `acb012e` | User download retry after terminal fail |
| `a3ee903` | BT deep-sleep A2DP resume UX (ADR-0119) |
| `7b58b87` | Like/dislike on system media (ADR-0118) |
| `36218e2` | Issue status tray replaces banners (ADR-0120) |

## Confirmed P0 bugs

1. **Disc NULL wipe** — `populateAllTrackGenres` wrote bare `cat.disc_number`.
   `INSERT OR REPLACE` nulls existing `tracks.disc_number` when cache NULL.
2. **BT warm+ACL double autoplay** — null-MAC debounce key `*` vs real MAC →
   two allows inside 5s in ANY mode.
3. **Stale shade thumbs** — `MediaService.onMediaItemTransition` never
   `reactionController.load()`; `fromPlayer` copies prev `isStarred`/`isDisliked`.
4. **`selected_null_mac` notif spam** — Resume notif posted every hit; no debounce.

## Fixes (1.10.1)

1. `COALESCE(cat.disc_number, t.disc_number, 1)` in populate SQL.
2. Shared connect debounce: stamp `ANY_UNKNOWN_MAC_KEY` when accepting real MAC
   in ANY mode; debounce `selected_null_mac` with fixed key.
3. Load reactions on media transition; guard extras update by current mediaId;
   clear sticky reaction until load completes.
4. Regression unit tests for all four.

## Deferred (out of scope 1.10.1)

- `replaceMediaItem` on thumb toggle (possible blip)
- Notification shed / idle FGS (pre-existing)
- Playlist conflict in-page Retry CTA (ADR-0120 intentional)
- Hard Jacoco ≥80% Makefile gate

## Gates (passed 2026-10-11)

```bash
make test-report          # green
make quality              # ktlint + detekt green
cd compose && ./gradlew :app:lintVitalRelease  # green
make bundle-release       # AAB + native symbols accepted
```

Jacoco (report only; no hard % gate): app LINE 65.4% / BRANCH 45.3%.
Touched helpers: `BtResumePolicy` 90.9% LINE, `CurrentTrackReactionController` 96.5%,
`PlaybackState` 100%, `data/db` package 87.8% LINE.

Vibe inline QA + Double-Check: ship-blocker none.
