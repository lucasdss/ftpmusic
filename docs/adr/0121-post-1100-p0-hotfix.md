# ADR 0121 — Post-1.10.0 P0 hotfix (1.10.1)

Date: 2026-10-11  
Status: Accepted  
Related: ADR-0116 (track chrome), ADR-0117 (BT seat / terminal CT),
ADR-0118 (media session reactions), ADR-0119 (BT connect autoplay),
ADR-0120 (issue tray), ADR-0018 (Play release pipeline)

## Context

Six commits landed on main after Play `v1.10.0` (15) without a version bump.
Release review found four P0 regressions in that delta: disc_number NULL wipe
during genre populate, BT warm/ACL debounce key split, shade thumbs sticky
across tracks when UI dead, and SELECTED+null-MAC Resume notif spam.

## Decision

1. Ship patch **1.10.1 / versionCode 16** with only P0 fixes + tests; keep
   intentional ADR-0117/0118/0119/0120 product behavior.
2. Disc populate uses `COALESCE(cat.disc_number, t.disc_number, 1)`.
3. BT resume debounce collapses unknown (`*`) and subsequent real MAC in ANY
   mode; `selected_null_mac` uses the same process debounce map with a fixed key.
4. `MediaService` loads `CurrentTrackReactionController` on media item
   transition; reaction surface extras apply only when snap matches current
   mediaId.
5. Defer replaceMediaItem thumb metadata, notif shed, idle FGS to later work.

## Consequences

- Album disc/track chrome survives metadata densify when cache disc NULL.
- Car/BT connect fires autoplay once per debounce window.
- Lock screen / QS thumbs refresh for background skip without Activity.
- Resume notif storm under SELECTED+denied Nearby devices stops.
