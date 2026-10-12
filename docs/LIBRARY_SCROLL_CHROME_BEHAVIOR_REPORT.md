# Library Scroll Chrome — Behavior Report

Date: 2026-10-10  
Status: Delivered  
Related: ADR-0114, ADR-0097, ADR-0107  
Market: `docs/SCROLL_FLUIDITY_MARKET_RESEARCH.md`

## Verdict (Caveman)

`4aed74f` pad Column → chips stuck under empty header band.  
Fix: fixed inset stay; chrome `graphicsLayer` ride `-offsetPx`.  
Chrome-only ride left Background gap between chrome and Lazy.  
Amend: chrome **+ list** one `graphicsLayer` — mid black row gone.  
Mini: hide on vertical scroll; show after 200ms idle. Nav fixed.  
Spotify/YTM keep mini — we diverge on purpose (more list space).

## Contracts

### Library / Favorites chrome

- Outer `Column` keeps full `LocalAppHeaderContentPadding` (no Lazy remeasure).
- Chips + search (Library) / mode chips (Favorites) **and** list body share
  one `graphicsLayer` via `LocalAppHeaderOffsetPx` (paint translate only).
- No Background gap between chrome and Lazy when header collapses.
- List viewport layout size stable during fling (ADR-0107); bottom paint strip
  ≈ `offsetPx` acceptable (mini hide often clears that band).

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

- Library Albums fling → chips/search enter header band; **no mid black row** under chrome; Settings not tappable collapsed.
- Mini gone mid-fling; back ~200ms after stop; nav stays.
- Favorites mode chips + list same ride as Library.

## Design review (interface-review / better-interface)

- Chip row / search: opaque `Background` under translate — contrast over album art OK.
- SegmentedChip hit targets unchanged (≥48dp row).
- Mini: `expandVertically`/`shrinkVertically` + fade — discrete, no bounce past nav.
- Nav labels/icons stay; optical bottom edge = nav only while mini hidden.
- TalkBack: mini removed from tree while hidden (`AnimatedVisibility`).
- Market: top enterAlways ≈ YTM; mini hide = intentional FTPMusic divergence.
