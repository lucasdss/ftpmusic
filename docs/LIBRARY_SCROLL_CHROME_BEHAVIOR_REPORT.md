# Library Scroll Chrome — Behavior Report

Date: 2026-10-10 (amended 2026-10-14)  
Status: Delivered  
Related: ADR-0114, ADR-0097, ADR-0107  
Market: `docs/SCROLL_FLUIDITY_MARKET_RESEARCH.md`

## Verdict (Caveman)

`4aed74f` pad Column → chips stuck under empty header band.  
Fix: fixed inset stay; chrome `graphicsLayer` ride `-offsetPx`.  
Chrome-only ride left Background gap between chrome and Lazy.  
Amend: chrome **+ list** one ride — mid black row gone.  
Bug: `fillMaxSize` translate left bottom paint strip ≈ `offsetPx`; mini hide
made strip obvious; mini reveal left strip above mini.  
Fix: `appHeaderRidingContent` = measure `H+maxHeaderPx` + translate + parent
`clipToBounds` — bottom strip **not** acceptable.  
Regression `5d2fb5d`: clip **after** top pad → top black row; tabs clipped.  
Fix: `appHeaderRidingClipHost` = clip **before** `padding(top)` so chips/search
reclaim header band; bottom overhang still clipped.  
Mini: hide on vertical scroll; show after 200ms idle. Nav fixed.  
Spotify/YTM keep mini — we diverge on purpose (more list space).

## Contracts

### Library / Favorites chrome

- Outer `Column` uses `Modifier.appHeaderRidingClipHost(headerPad)`:
  `fillMaxSize` → `clipToBounds` → `background(Background)` → `padding(top)`.
  **Clip before pad** is required — clip after pad discards top reclaim.
- Chips + search (Library) / mode chips (Favorites) **and** list body share
  `Modifier.appHeaderRidingContent` via `LocalAppHeaderOffsetPx`.
- Measure height = viewport `H` + fixed header-pad px; layout reports `H`;
  paint translate `-offsetPx` (ADR-0107: size stable during fling).
- Collapsed: brand AppHeader off-screen; library nav chips + search visible in
  former header band (not covered by Background strip).
- No Background gap between chrome and Lazy when header collapses.
- **No** bottom paint strip above nav/mini at any offset (incl. mini hidden).

### Mini hide-on-scroll

- Shell-owned on primary tabs (Home / Library / Search / Favorites).
- Vertical nested-scroll activity → mini slot height → 0 (discrete anim).
- Idle debounce **200ms** → mini returns.
- Idle nested-scroll frame while hidden (drag end **without fling**) also settles/reveal.
- `onPostFling` still settles (unchanged).
- Route change → force visible.
- Bottom `NavigationBar` never hides.
- No track / Now Playing / login → unchanged ADR-0070 gates.

### Perf

- Artists / Playlists / Radio: `LazyLayoutCacheWindow(0.5 / 0.2)` (Albums already).
- No per-frame AppHeader layout-height reclaim.

## Manual

- Library Albums fling → chips/search enter header band fully visible; **no top
  black row** over tabs; **no mid black row** under chrome; Settings not tappable collapsed.
- Mini gone mid-fling; **no black band** where mini was; mini back ~200ms after stop with **no band above mini**; nav stays.
- Favorites mode chips + list same ride as Library.

## Design review (interface-review / better-interface)

- Chip row / search: opaque `Background` under translate — contrast over album art OK.
- SegmentedChip hit targets unchanged (≥48dp row).
- Mini: `expandVertically`/`shrinkVertically` + fade — discrete, no bounce past nav.
- Nav labels/icons stay; optical bottom edge = nav only while mini hidden.
- TalkBack: mini removed from tree while hidden (`AnimatedVisibility`).
- Market: top enterAlways ≈ YTM; mini hide = intentional FTPMusic divergence.
