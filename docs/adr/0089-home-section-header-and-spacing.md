# ADR 0089 — Home Section Header Affordance and Spacing

Date: 2026-10-07
Status: Accepted
Related: ADR 0021 (section visibility / playlists row), ADR 0017 (daily mix
refresh), ADR 0054 (tokens), ADR 0059 (reaction icon sizing)

## Context

Home rendered inconsistent section headers: text “See all”, wired Refresh,
decorative ThumbUp/Bookmark in the trailing slot, and dead Chevron/Refresh
icons. Inter-section spacing (~4dp) was below market music Home feeds
(Spotify / YouTube Music ≈16–24dp). YouTube Music (2024) replaced Home
“More” pills with a trailing chevron — icon-only nav matches FTP’s preference
over Spotify’s text “Show all”.

ADR 0021 documented text “See all” and display-only fav-artist avatars; code
already navigates artists, and text must go.

## Decision

1. **Shared `HomeSectionHeader`.** Leading semantic icon (teal, ~16dp) tight
   to title; optional trailing `IconButton` (≥40dp hit) only when action is
   real. Trailing kinds: `Refresh` (Daily Mixes regen) or `SeeAll`
   (`ChevronRight` + contentDescription).
2. **Destinations.** Playlists → `library?tab=playlists`. Favorite Artists /
   Albums / Radio → Favorites tab root. Recently Added → `library?tab=albums`.
   Tuned In → **no trailing** (FlowRow already lists all genres).
3. **Spacing tokens.** Section header top `spacing2XL` (24); header→row
   `spacingXS` (4); content bottom `spacingM` (12); horizontal `spacingL`
   (16). No hardcoded section dp.
4. **Supersedes ADR 0021 UI bits:** “See all” text → chevron; fav-artist
   avatars remain interactive (cards). Toggle+data gating and section order
   unchanged.

## Consequences

- One header pattern across all Home shelves; no fake trailing affordances.
- Bottom-tab jumps reuse `TabNavigationPolicy.resolveActiveTabForDestination`.
- Favorites deep-scroll to Artists/Albums/Radio subsections deferred (v1 =
  tab root). No new “all genres” screen for Tuned In.
