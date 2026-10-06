# ADR 0075 — Queue Sheet Save + History + Batch Select

Date: 2026-10-06
Status: Accepted
Related: ADR-0015, ADR-0039, ADR-0053, ADR-0070, ADR-0074,
`docs/QUEUE_SHEET_P2_SAVE_HISTORY_BATCH_BEHAVIOR_REPORT.md`,
`docs/QUEUE_MANAGEMENT_MARKET_COMPARISON_BEHAVIOR_REPORT.md`

## Context

P2 market gaps on prod `PlayerBar` `queue_sheet`: save queue as playlist,
Recently Played band, batch select/remove. Share already exists via
`PlaybackViewModel.shareQueue` (server `api.createPlaylist` — ADR-0015
exception). Saving must not silently replace Share or break dual-queue Clear
semantics (PRIORITY-only).

## Decision

1. **Local-first save** — `saveQueueAsPlaylist` uses `PlaylistRepository`
   (`createPlaylist` + `addToPlaylist`). Share stays server-only. Empty queue
   no-ops. Default name `Queue - {MMM dd}`.
2. **History SoT** — `TrackDao.getRecentlyPlayed(limit = 20)` (Home). Filter IDs
   already in active queue + current track. Load on sheet open only
   (`onQueueSheetOpened` → `refreshQueueHistory`). Tap / Play Next →
   `playNext` (PRIORITY insert; does not wipe session).
3. **Selection** — Enter via long-press row or Select when upcoming nonempty.
   Checkboxes on PRIORITY + CONTEXT + Autoplay (not Now Playing pin, not
   history). Remove descending indices via `removeFromQueueBatch`. Exit on
   Remove / Cancel / sheet dismiss. Clear queue remains PRIORITY-only outside
   selection mode. No YT dismiss-session.

## Consequences

- Two playlist paths: Share (remote) vs Save (Room + pending sync).
- History band uses muted section styling (not purple PRIORITY).
- Selection mode disables swipe-dismiss and reorder handles while active.
- Sleep timer / repeat strip in sheet: shipped in [ADR-0076](0076-queue-sheet-sleep-repeat-and-share-publish.md).
