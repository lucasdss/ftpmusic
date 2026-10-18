# Sync Consistency Review — Behavior Report

Date: 2026-10-18
Scope: Playlist sync (outbound + inbound) correctness & consistency.
Related: ADR-0013 (amend), ADR-0025 (amend), `LIBRARY_SYNC_BEHAVIOR_REPORT.md`.

Caveman terse. Code truth after the sync-consistency pass.

## What was broken (and fixed)

| # | Sev | Bug | Fix |
|---|---|---|---|
| 0 | P0 | `updatePlaylist`/`createPlaylist` sent comma-joined `songIndexToRemove` / `songIdToAdd` / `songId` strings. Subsonic spec = **repeated params**; Navidrome `Ints`/`Strings` read one value per key → multi-index remove silently dropped, multi-id add parsed as one bogus id. | `SubsonicApi` params now `List<Int>` / `List<String>` (Retrofit emits `k=v&k=v`); call sites pass lists. Also repairs `copyPlaylist` and multi-track `addToPlaylist` / `createPlaylistWithTracksSynced`. |
| 1 | P1 | `sync_tracks` read `songCount` at top level of `getPlaylist` envelope (always null → 0) → never removed server tracks → append-only duplicates. | Parse `subsonic-response.playlist.songCount`; fall back to `entry.size`; fail-closed (leave pending) when both absent. |
| 2 | P2 | Flush loop `break`s at 10 consecutive failures → permanent pause, pending changes never self-heal. | Loop runs forever at capped 30m backoff; `consecutiveFailures` reset on offline/empty-creds idle. |
| 3 | P2 | `removeFromPlaylist` emitted position-based `remove_tracks` → wrong track removed when local order ≠ server. | Removal now enqueues `sync_tracks` full-replace (matches `moveTrack`/`syncToServer`). |
| 4 | P2 | `getPending` `ORDER BY created_at ASC` no tiebreaker → `create`+`add_tracks` same-ms could flush add-before-create. | `ORDER BY created_at ASC, id ASC`. |
| 5 | P2 | `create` ok-status with null `serverId` still `markFlushed` → temp `new-*` id stranded forever. | Fail-closed: mark conflicted (surfaces via issue tray ADR-0120), no retry-create (avoids duplicate). |

## Verified correct (no change)

- Honest watermarks (`last_full/delta_sync_ms` only on `phase=complete`; soft-stop skips cadence).
- Fail-closed album-list walk (truncated / offset-ignoring / max-page → `AlbumListIncompleteException`).
- No cascade delete on `playlist_entries` → create-flush `delete → remapEntries → upsert` preserves pre-create entries.
- Playlist inbound TOCTOU re-check (pending/conflict re-verified after `getPlaylist`).
- Temp-id remap re-points entries (`remapEntries`) + pending (`remapPlaylistId`).
- Skip-queue retry prioritizes retryable ids; re-arms exhausted rows on FULL/force only.

## Deferred (not this pass)

- `importPlaylist` post-fetch TOCTOU (import is explicit user overwrite).
- Large-N URL length on repeated params (chunk / `formPost`).
- `sync_tracks` mid-flight TOCTOU (self-consistent under `flushMutex`).
- DELTA "newest" walk fetches full catalog (ADR-0139 product decision).

## API contract

`updatePlaylist` → `songIndexToRemove` (repeated, zero-based indices, applied
before adds), `songIdToAdd` (repeated). `createPlaylist` → `songId` (repeated).
Empty list omits the param.
