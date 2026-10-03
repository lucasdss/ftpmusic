# ADR 0062 — Tab Stack Save/Restore + Reselect Pop-to-Root

Date: 2026-10-03
Status: Accepted
Related: ADR-0055 (navbar IA), ADR-0056 (Settings stack),
docs/NAVIGATION_TAB_STACK_BEHAVIOR_REPORT.md

## Context

Bottom bar used Navigation multi-back-stack flags
(`popUpTo("home"){saveState}`, `restoreState`, `launchSingleTop`) so
Home → Daily Mix → Library → Home correctly restored the nest (market-aligned).

Same-tab re-tap did **not** pop to root. Detail routes are flat
(`mix/{id}`, not `home/mix/{id}`), so `currentRoute.startsWith("home")`
was false on Daily Mix. Tapping Home ran `navigate("home")+restoreState`
and re-landed on the mix — looked like a no-op. Empty reselect stubs claimed
scroll-to-top "per spec" but never popped.

Spotify / YT Music / Apple Music: keep nest across tabs; re-select same tab →
pop to tab root (then scroll-to-top if already root).

## Decision

1. **Keep** cross-tab save/restore. Do not clear Home nest on Library visit.
2. **Reselect** (active tab tapped again): `popBackStack` via
   `tabRootPopFallbackRoutes` (library pattern then bare `library`) when not
   already on that root. Root re-tap scroll/refresh remains stub.
3. **Selection ownership:** `activeBottomTab` (`rememberSaveable`), updated on
   tab clicks and programmatic tab jumps (`library?tab=playlists`). Not route
   prefix matching.
4. **Overlays:** Settings / nowplaying do not change `activeBottomTab`.
5. **Policy object:** `TabNavigationPolicy` pure helpers for unit tests; no
   nested per-tab `NavHost` graphs this pass.

## Consequences

- Flow A (tab switch restore) unchanged — market OK.
- Flow B (Home re-tap on Daily Mix) pops to Home root.
- Home stays highlighted while browsing Home-owned details.
- Flat graph kept; future nested graphs optional if ownership gets harder.
