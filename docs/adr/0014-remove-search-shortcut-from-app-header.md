# ADR 0014: Remove Search Shortcut From App Header

## Status
Accepted

## Context
The `AppHeader` composable (persistent top bar across all 5 main tabs) currently contains a search shortcut pill (112dp) to the right of the Cast button. This pill is hidden on the Search tab itself but present on the other four tabs.

The Search bottom nav tab provides the same navigation entry point. The top shortcut is redundant and consumes horizontal space that could be used by the app branding.

## Decision
Remove the search shortcut pill from `AppHeader`. The Search bottom nav tab remains as the sole entry point.

Additionally, adjust the horizontal weight distribution between the app name column and the trailing spacer:
- Before: `weight(1f)` / `weight(1f)` (equal split)
- After: `weight(2f)` / `weight(1f)` (2/3 for branding, 1/3 for spacer)

The app name "FTP Music" and tagline "Flow Tempo Pulse" will occupy up to 2/3 of the available space between the logo and right-side controls (Offline badge, Cast button). The freed ~112dp from the search pill removal goes entirely to the branding area and spacer.

The tagline font size (12sp) is unchanged.

## Consequences
- Search is still one tap away via the bottom nav bar (unchanged)
- App branding has more horizontal breathing room across all tabs
- One fewer visual element in the header, reducing clutter
- The search pill was the only user-facing clue that the search tab exists at all on non-search tabs; losing this may slightly reduce discoverability for first-time users. Mitigated by the Search tab icon+label in the bottom nav bar, which is always visible.
