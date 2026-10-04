# ADR 0069 — Artist Detail Albums-First UI

Date: 2026-10-04
Status: Accepted
Related: ADR-0054 (design tokens), ADR-0057 (typography),
docs/ARTIST_DETAIL_UI_BEHAVIOR_REPORT.md

## Context

Artist detail used **Top Tracks** as default tab 0 and **Albums** as tab 1.
Market music apps (YouTube Music, Apple Music, Tidal) lead artist pages with
**discography**; Spotify keeps global Play while emphasizing Popular + Discography.
Our track tab was a full catalog, so “Top Tracks” was misleading.

Action-row chrome (Play / Shuffle / ⋮) and list gutters diverged between Album
and Artist (padding XL vs L, icon–label 8 vs 6, vertical pad 16 vs 0).

## Decision

1. **Tabs:** Default **Albums** (index 0), then **Songs** (index 1).
2. **Play / Shuffle:** Remain above tabs; play/shuffle the **full artist track
   catalog** (Spotify-style), independent of which tab is visible.
3. **Shared `DetailActionRow`:** Album + Artist share pad
   (`spacingXL` × `spacingL`), height `detailActionHeight()`, gap 10dp,
   icon–label 8dp, ⋮ ≥ `minTouchTarget()` (48dp base).
4. **Tokens:** `gridGapH` / `gridGapV` / `spacingBelowArt` / `detailActionHeight`
   in Dimens for Library / Artist grids and detail CTAs.
5. **Cross-surface rhythm:** Library list rows and Home LazyRows normalize to
   spacing tokens; residual 40dp reaction thumbs left as follow-up.

## Consequences

- Opening an artist shows album grid first; Songs is one tap away.
- Copy: Artist CTA label stays **Play All**; Album stays **Play**.
- Process death resets tab to Albums (`remember` default 0) — acceptable.
