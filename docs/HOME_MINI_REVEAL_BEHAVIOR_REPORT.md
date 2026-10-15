# Home Mini Reveal — Behavior Report

Date: 2026-10-15  
Status: Delivered (amended: Home header riding)  
Related: ADR-0143, ADR-0114, ADR-0070  
Shell: `MiniPlayerScrollVisibility`, NavHost Scaffold `bottomBar`

## Verdict (Caveman)

Scroll Home → AppHeader hide (enterAlways) + mini hide.  
Opaque `Background` rides with list (ADR-0114) → Settings/Cast stay readable
on reveal; list not paint-through chrome.  
Reach list end → idle 200ms → mini reveal → viewport shrink ~64dp.  
LazyList keep `firstVisibleItem` → last ~miniHeight below fold without pad.  
Fix: `contentPadding.bottom = miniHeight` while track loaded (ADR-0143).  
Pad stay during hide → absorb reveal delta. No `scrollBy` (nested-scroll loop).

## Contracts

### Header ride (Home)

- Outer `Column` uses `appHeaderRidingClipHost(headerPad)` + inner
  `appHeaderRidingContent` + `.background(Background)` (Library parity).
- Lazy top pad = local `spacingS()` only (header clearance on Column).
- Overlay `AppHeader` stays transparent Row; opacity comes from riding body.

### Reserve rule (mini)

- Shell provides `LocalMiniPlayerBottomContentPadding` via
  `resolveMiniPlayerBottomContentPadding(trackLoaded, miniPlayerHeight())`.
- `trackLoaded` = `playbackState.isVisible && !fullPlayer && !login`
  (mini-slot gates minus `miniScroll.visible`).
- Home LazyColumn: `PaddingValues(top = spacingS(), bottom = miniBottomPad)`.
- Search LazyColumn: `PaddingValues(top = headerPad, bottom = miniBottomPad)`.
- No track → bottom pad `0`. Non-positive mini height → `0`.

### Explicit non-goals

- No `scrollBy` / `animateScrollBy` compensation on reveal.
- Pad not toggled with `miniScroll.visible` (avoid AnimatedVisibility tween race).
- Search not on riding path yet (follow-up).
- Resting ~miniHeight blank at list end while track loaded — intentional.

## Edge map

| Case | Expected |
|------|----------|
| Home scroll reveal mid-list | Settings readable on solid Background |
| Home scroll hide | Header/chrome reclaim; no list behind Settings |
| Track loaded, scroll hide, idle reveal at end | Last Home/Search item fully above mini |
| No track | No extra bottom pad |
| Mid-list reveal | Pad present; no forced scroll jump |
| Search same class (mini) | Same Local bottom pad; still top headerPad |
| Process death | Local re-provided from playback state |

## Verification

- Unit: `resolveMiniPlayerBottomContentPadding` branches.
- Compose: Local override → Lazy last item sits above reserved band;
  riding clip-host tests cover opaque reclaim.
- Manual: Home end + playing → hide → reveal → no blocked last card;
  Settings gear readable while header re-enters.
