# ADR 0104 — List chrome: reactions + duration prefs

Date: 2026-10-09  
Status: Accepted  
Related: ADR-0020 (like/dislike), ADR-0059 (reaction sizing), ADR-0096 (SongListRow), ADR-0103 (Appearance)

## Context

Track lists showed like/dislike only on Album / Artist / Mix detail, with
duration buried in the meta row under thumbs. Users need Symfonium-style
control: optional list fields, more title space when reactions are off, and
duration aligned with the title. Now Playing must keep thumbs always.

## Decision

1. **`ListChromePrefs`** (`showListReactions`, `showListDuration`, both default
   **true**) persisted as `list_show_reactions` / `list_show_duration` via
   SecureStorage; hydrated by SettingsViewModel; provided through
   `LocalListChromePrefs` in `FtpmusicTheme` (same pattern as typography).
2. **Settings → Appearance** exposes two switches:
   - Like and dislike on track lists
   - Duration on track lists
3. **`SongListRow`**: duration on the **title line** (end-aligned) when enabled;
   meta row = cache + gated thumbs only. Prefs gate rendering even if callbacks
   are passed.
4. **Browse wire-up:** Album / Artist / Mix keep existing toggles; Home /
   Search / Playlist / Downloads gain `TrackListReactionCoordinator` + Room
   watch. Favorites keeps mode-specific **trailing** thumb when reactions ON;
   hides it when OFF. Add Songs picker and queue sheet excluded.
5. **Now Playing / mini:** ignore list chrome prefs — reactions always shown.

## Consequences

- Disabling list reactions frees title width without removing NP controls.
- Duration always optically aligned with title when shown.
- Optimistic like/dislike shared via `TrackListReactionCoordinator`.
- See `docs/LIST_CHROME_BEHAVIOR_REPORT.md`.
