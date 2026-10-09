# ADR 0102 — Vertical Scroll FPS Pass 3

Date: 2026-10-09
Status: Accepted
Related: ADR-0096, ADR-0097, ADR-0036, ADR-0037
Supplements: `docs/SCROLL_FPS_BEHAVIOR_REPORT.md`, HOME/LIBRARY scroll reports

## Context

After Pass 1–2, Home / Library / other vertical lists still showed subtle fling
jank. Residual causes vs Spotify / YT Music list hygiene:

1. Home LazyRows lacked `contentType`; covers decoded at 300.dp on ~144.dp cards.
2. FittingText still on Home hero / Tuned In and Library artist/playlist/radio rows.
3. Infinite EQ transitions ran during fling on active album cards.
4. `randomAlbums.take(10)` reallocated inside Lazy `items` composition.

Library Paging remains deferred (ADR-0037).

## Decision

1. **contentType** on Home sections/rows and Favorites / Queue vertical lists.
2. **decodeSize ≈ display** on Home cards (`albumCardWidth()`).
3. **Fixed Text + ellipsis** on all vertical scroll-path titles (ADR-0096 rule).
4. **ScrollAwareEqBars** — static bars while `isScrollInProgress`; animate only when idle.
5. Hoist Home `take(10)` via `remember`.

## Consequences

- Micro-jank reduced without windowed catalog.
- EQ may freeze mid-fling (intentional).
- FittingText reserved for non-scroll chrome.
