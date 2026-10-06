# Queue Sheet UI/UX Behavior Report

Caveman. Surface = prod `PlayerBar` `queue_sheet` (ADR-0070 / ADR-0074). Harness = `QueueScreen`.
Cycle: red-bleed fix + token parity + Apple Autoplay controls + Spotify Next in Queue lexicon.

## Root cause (all rows RED)

| Layer | Before | After |
|-------|--------|-------|
| `SwipeToDismissBox.backgroundContent` | `Color.Red` / `#E84040` | `DestructiveRed` |
| Row foreground fill | `Color.Transparent` | opaque `SurfaceElevated` |
| Idle paint | red shows through | elevated surface hides swipe bg |

Not error state. Not red typography. Mid-swipe still reveals delete red.

## Token map (sheet)

| Role | Token |
|------|-------|
| Row idle bg | `SurfaceElevated` |
| Row dragging bg | `colorScheme.surfaceVariant` |
| Swipe / Clear destructive | `DestructiveRed` |
| Title idle | `Foreground` |
| Artist / duration / handle | `NavUnselected` / `Dimmed` |
| Offline / saving chip | `OfflineYellow` |
| Art placeholder | `Surface` |
| Priority / Next from / Autoplay headers | `BrandPurple` / `NavUnselected` / `BrandTeal` |

## Edge Agent

| Edge | Status |
|------|--------|
| Idle row opaque | Fixed |
| Mid-swipe reveal red | Kept (`backgroundContent`) |
| Dragging highlight | `surfaceVariant` over elevated |
| Harness `QueueScreen` same bleed | Fixed (opaque + `DestructiveRed`) |
| Process death | N/A paint |

## Perf Agent

Opaque solid fill. No extra `remember`. No list key change. Recomposition same as before.

## Design Agent

- Hit areas: 48dp remove / drag / shuffle — kept
- Typography: `textHeadingS` title, `textLabelM` artist, Inter duration — ADR-0057
- Density: sheet `adp(6)` + `spacingS` (not Library `spacingL`) — ADR-0070
- Market: delete red only on swipe/Clear (Spotify/Apple pattern)

## Coverage Agent

Targeted: `PlayerSurfacesUxTest` (idle fill opaque + `queue_track_row` tag + existing sheet lexicon/remove) + `QueueScreenComposeTest`.
**Run:** `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests …PlayerSurfacesUxTest --tests …QueueScreenComposeTest` → **BUILD SUCCESSFUL** (2026-10-05).
Token contract asserted (`QueueTrackRowIdleBackground == SurfaceElevated`, α≥1, ≠ `DestructiveRed`).
Gate: player UX Compose suite green this cycle.

## Files

- `compose/.../player/PlayerBar.kt` — `QueueDismissTrackRow` / `QueueTrackRow`
- `compose/.../player/QueueScreen.kt` — harness parity
- `docs/adr/0073-queue-sheet-opaque-swipe-rows.md`
