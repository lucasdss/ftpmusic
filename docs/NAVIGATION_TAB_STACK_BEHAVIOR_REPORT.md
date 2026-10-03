# Navigation Tab Stack — Behavior Report

Date: 2026-10-03
Related: ADR-0062, ADR-0055, ADR-0056
Code: `TabNavigationPolicy`, `NavHost.kt` bottom bar

## Market matrix

| Flow | Spotify | YT Music | Apple Music | FTP Music (after fix) |
|------|---------|----------|-------------|------------------------|
| Home → nest → other tab → Home | Nest kept | Nest kept | Nest kept | Nest kept (`saveState`/`restoreState`) |
| Nest open, re-tap Home | Pop to Home root | Pop toward root | `popToRoot` | Pop to tab root |
| Already at tab root, re-tap | Scroll top | Scroll / Library switcher | Scroll top | Stub (scroll/refresh TBD) |
| Nest under Home — Home selected? | Yes | Yes | Yes | Yes (`activeBottomTab`) |

## FTP pre-fix divergence

1. **Flow A (Library round-trip):** market-OK. Flat graph + `popUpTo("home"){saveState}` + `restoreState` restored Daily Mix.
2. **Flow B (re-tap Home on Daily Mix):** broken. `mix/{id}` not under `home/` prefix → Home unselected → click always `navigate`+`restore` → stayed on mix. Reselect stubs empty.
3. **Selection:** prefix `startsWith(tab.route)` failed for `mix/`, `album/`, `artist/`, `playlist/`, `genre/`.

## Policy (post-fix)

- Cross-tab: keep save/restore multi-back-stack.
- Same-tab: `popBackStack(tabRoot, inclusive=false)` when current route ≠ tab root.
- Selection: `activeBottomTab` (`rememberSaveable`), not route prefix.
- Programmatic tab jump (`library?tab=playlists`): set `activeBottomTab=library`.
- Settings / nowplaying: do **not** steal bottom-tab selection.

## Edge cases

- Process death: `rememberSaveable` active tab + Nav saveState for nest.
- System back on mix: pop → home; active tab stays home.
- Tab spam: `launchSingleTop` + idempotent pop-to-root.
- Overlay routes (settings, nowplaying): prior tab stays highlighted.

## Tests

`TabNavigationPolicyTest` — pop vs navigate, selection, playlists ownership, overlay non-steal.
