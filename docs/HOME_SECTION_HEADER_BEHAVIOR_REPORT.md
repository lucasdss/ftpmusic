# Home Section Header Behavior Report

Date: 2026-10-07
Related: ADR 0089, ADR 0021, ADR 0017

## Problem (before)

Home section headers used four patterns:

| Pattern | Sections | Trailing | Wired? |
|---|---|---|---|
| Icon Refresh | Daily Mixes | Refresh teal | Yes |
| Text "See all" | Playlists | Teal text | Yes |
| Decorative trailing | Fav Artists / Albums / Radio | ThumbUp / Bookmark | No |
| Dead affordance | Tuned In / Recently Added | Chevron (± Refresh) | No |

Spacing: LazyColumn no `spacedBy`. Inter-section gap ≈ header `spacingXS` (~4dp). Hardcoded `4.dp` / `6.dp` / `8.dp`. Market (Spotify / YTM) ≈ 16–24dp carousel → next title. **Fail.**

## Standard (after)

Rules:

1. Leading category icon tight to title (teal, ~16dp). Never trailing decoration.
2. Trailing = real action only: `Refresh` (regen) or `ChevronRight` (see all). Hit ≥40dp. Teal. `contentDescription` required.
3. No text "See all" — icon-only (YTM 2024 Home chevron pattern).

| Section | Leading | Trailing | Destination |
|---|---|---|---|
| Daily Mixes | AutoAwesome | Refresh | `refreshAllMixes()` |
| Playlists | QueueMusic | ChevronRight | `library?tab=playlists` |
| Favorite Artists | ThumbUp | ChevronRight | Favorites tab |
| Favorite Albums | ThumbUp | ChevronRight | Favorites tab |
| Favorite Radio | Bookmark | ChevronRight | Favorites tab |
| Tuned In | Tune | None | FlowRow shows all genres |
| Recently Added | NewReleases | ChevronRight | `library?tab=albums` |

Shared composable: `HomeSectionHeader` in `HomeScreen.kt`.

## Spacing (after) vs market

| Token | Role | ≈dp |
|---|---|---|
| `spacing2XL` | Section top (carousel → next title) | 24 |
| `spacingXS` | Header bottom → row | 4 |
| `spacingM` | Content bottom breathing | 12 |
| `spacingL` | Horizontal inset | 16 |

Hardcoded section dp removed. Matches Spotify/YTM ~16–24dp section rhythm.

## Edge cases

- Section hidden (toggle off / empty data): no header, no trailing.
- Tuned In: no chevron — avoid lying "see all" when all genres already listed.
- Recently Added: dead Refresh removed; only live Chevron.
- Fav Artists cards remain clickable (ADR 0021 display-only note superseded).

## Tests

`HomeScreenComposeTest`: chevron contentDescriptions drive `onPlaylistsClick` / `onFavoritesClick` / `onRecentlyAddedClick`; Tuned In has no See-all CD; Daily Mixes Refresh CD present when mixes exist.
