# ADR 0126 — Auto-center now playing in track lists

Date: 2026-10-12  
Status: Accepted  
Related: ADR-0104 (list chrome), lyrics auto-scroll in PlayerBar, ADR-0070 (player surfaces)

## Context

Detail track lists highlight the active row but leave scroll at the hero/top, so a playing track deep in a playlist is easy to miss. Queue pins current as `now-playing-anchor`, but after the user scrolls into Recently played there is no re-center on track change. Market apps follow in queue/context lists and avoid fighting user scroll.

## Decision

1. **Shared helper** `ScrollToNowPlayingEffect`: `LaunchedEffect` + ~3s user-scroll suppress (same pattern as synced lyrics). Pure helpers resolve lazy index and center offset.
2. **Pref** `ListChromePrefs.autoCenterNowPlaying` (SecureStorage `list_auto_center_now_playing`, default **true**), Settings → Appearance toggle.
3. **Surfaces:** Mix / Playlist / Album / Artist tracks / Queue sheet. Scroll by `currentTrackId` even when paused. Missing id → no-op.
4. **Queue:** keep pinned now-playing anchor layout; when pref ON, scroll back to anchor only if not reasonably visible (`onlyIfNotVisible`).
5. **Suppress:** wait until quiet window clears (do not drop a pending track-change follow). Persist `suppressUntil` across config change.
6. **Not chosen:** one-shot-on-enter only; always-follow with no suppress.

## Consequences

- One CompositionLocal gate for all list consumers.
- Artist paging: only centers if track already loaded.
- See `docs/AUTO_CENTER_NOW_PLAYING_BEHAVIOR_REPORT.md`.
