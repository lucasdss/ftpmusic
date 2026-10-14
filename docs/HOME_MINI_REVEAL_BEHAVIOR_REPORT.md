# Home Mini Reveal — Behavior Report

Date: 2026-10-14  
Status: Delivered  
Related: ADR-0143, ADR-0114, ADR-0070  
Shell: `MiniPlayerScrollVisibility`, NavHost Scaffold `bottomBar`

## Verdict (Caveman)

Scroll Home → mini hide → viewport grow via Scaffold remasure.  
Reach list end → idle 200ms → mini reveal → viewport shrink ~64dp.  
LazyList keep `firstVisibleItem` → last ~miniHeight below fold.  
Look blocked by mini. Extra scroll → hide again → oscillation.  
Not paint-under-mini (Scaffold already inset). Not Library riding clip.  
Fix: `contentPadding.bottom = miniHeight` while track loaded.  
Pad stay during hide → absorb reveal delta. No `scrollBy` (nested-scroll loop).

## Contracts

### Reserve rule

- Shell provides `LocalMiniPlayerBottomContentPadding` via
  `resolveMiniPlayerBottomContentPadding(trackLoaded, miniPlayerHeight())`.
- `trackLoaded` = `playbackState.isVisible && !fullPlayer && !login`
  (mini-slot gates minus `miniScroll.visible`).
- Home + Search LazyColumns: `PaddingValues(top = headerPad, bottom = miniBottomPad)`.
- No track → bottom pad `0`. Non-positive mini height → `0`.

### Explicit non-goals

- No `scrollBy` / `animateScrollBy` compensation on reveal.
- Pad not toggled with `miniScroll.visible` (avoid AnimatedVisibility tween race).
- Library/Favorites riding/clip unchanged (ADR-0114).
- Resting ~miniHeight blank at list end while track loaded — intentional.

## Edge map

| Case | Expected |
|------|----------|
| Track loaded, scroll hide, idle reveal at end | Last Home/Search item fully above mini |
| No track | No extra bottom pad |
| Mid-list reveal | Pad present; no forced scroll jump |
| Search same class | Same Local bottom pad |
| Process death | Local re-provided from playback state |

## Verification

- Unit: `resolveMiniPlayerBottomContentPadding` branches.
- Compose: Local override → Lazy last item sits above reserved band.
- Manual: Home end + playing → hide → reveal → no blocked last card.
