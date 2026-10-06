# ADR 0076 — Queue Sheet Sleep/Repeat + Share Publish

Date: 2026-10-06
Status: Accepted
Related: ADR-0015, ADR-0070, ADR-0074, ADR-0075,
`docs/QUEUE_SHEET_SLEEP_SHARE_BEHAVIOR_REPORT.md`

## Context

P2 left sleep/repeat on the Now Playing header only. Share used a server-only
`api.createPlaylist` exception (ADR-0015 row 18) and shared plain text without a
playlist link or public flag. Navidrome supports `updatePlaylist` with
`public=true`.

## Decision

1. **Sleep + repeat strip** in prod `PlayerBar` `queue_sheet` (below grabber).
   Same callbacks as NP header (`onSleepTimer`, `onRepeatToggle`). NP controls
   stay. Hidden in selection mode.
2. **Share = save + publish + share**
   - Local-first `PlaylistRepository.createPlaylistWithTracksSynced`
   - Await flush (`PlaylistSyncWorker.flushNowAndAwait`) for server id
   - `updatePlaylist(public=true)` via new `publicFlag` query param
   - Android share chooser with Navidrome deep link
     `{base}/app/#/playlist/{id}/show`
3. **Save button** remains local-only (no public, no chooser).
4. Supersedes ADR-0015 audit row 18: `shareQueue` is no longer a server-only
   exception; it is local-first then publish.

## Consequences

- Offline / flush failure: local playlist kept; toast; no public/share link.
- Guest anonymous links (`createShare`) remain a follow-up (needs EnableSharing).
- No YT dismiss-session; Dual/Cast unchanged.
