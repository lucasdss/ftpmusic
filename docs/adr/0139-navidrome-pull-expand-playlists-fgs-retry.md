# ADR-0139: Navidrome Pull Expand (Playlists Inbound, Caps, FGS, Retry)

## Status

Accepted

## Context

Library metadata sync densified the catalog adaptively (ADR-0045 / 0085) with
soft caps (`DELTA_MAX_PAGES=3`, album-track drain 400, orphan page budgets).
Playlists were outbound-only in the background (ADR-0015); inbound refresh
happened only on detail open / explicit import. Soft skips returned WorkManager
`success`, so a cooldown/cellular miss waited a full interval. Per-album
`getAlbum` failures could abort the whole track phase (3 consecutive / >5%
ratio). Uncapped drains need process survival — there was no sync FGS.

## Decision

1. **Remove soft caps** — DELTA album list walks until a short page; album-track
   drain has no 400 limit; orphan genre offset runs until caught up; random
   rounds stop when a round yields no new track ids (safety max 50).

2. **Playlists inbound 1A** — Metadata sync phase refreshes **already-imported**
   playlists only (`getPlaylists` ∩ local IDs). Skip temp ids, conflicted rows,
   and playlists with pending outbound changes. Silent refresh (no auto-download).
   Import picker remains for unimported server playlists.

3. **Retry-on-skip 2C**
   - WorkManager: soft skips `offline` / `unreachable` / `cellular_policy` →
     `Result.retry`. `cooldown` and `already_running` → `Result.success()`
     (avoid hot retry loops; see Amend §6).
   - Durable `sync_skip_albums` queue: failed `getAlbum` upserts; success deletes;
     next sync prioritizes skip IDs. Exhausted rows (`fail_count >= 10`) stay
     in the table and are excluded from DELTA pending — do not delete-to-suppress.
     Scheduled FULL (and force) re-arms exhausted rows so server repairs can heal.
     Empty `song[]` fails when catalog `songCount > 0`; `songCount == 0` is
     success (clears stale tracks). Soft-stop after 8 consecutive nulls keeps
     `phase != error` but does **not** advance FULL/DELTA cadence watermarks
     (ADR-0068). Album-list fail-closed unchanged.

4. **`LibrarySyncService` (`dataSync`)** — Started when a sync Job actually
   begins; stopped in `finally`. Separate from `MediaService`. On
   `ForegroundServiceStartNotAllowedException`, sync continues best-effort
   without FGS. `onTimeout` cancels [MetadataSyncWorker] sync Jobs then stops
   the FGS. No fake 6h restart loops (ADR-0019).

## Consequences

- Full / long DELTA syncs can run much longer; user sees a low-priority sync
  notification while FGS is allowed.
- Imported playlists stay closer to server without opening detail.
- Transient album fetch failures no longer fail the whole sync watermark; they
  converge across runs via the skip queue.
- Tests and behavior report updated; Room v66 adds `sync_skip_albums`.

## Amend — post-audit hardening

1. Album-list walk: safety max pages + stop when a page yields zero new ids
   (guards offset-ignoring servers). **Fail-closed:** zero-new-id and max-pages
   set `listIncomplete` and throw `AlbumListIncompleteException` — never
   FULL `replaceAlbumsDiffPreserveEnrich` from a truncated walk.
2. Skip queue: `fail_count` (Room v67); keep exhausted rows at 10 (exclude from
   pending unless force); soft-stop track drain after 8 consecutive nulls
   without `phase=error`, but skip cadence watermarks on soft-stop.
3. `LibrarySyncService.stop` uses `Context.stopService` (no start-to-stop).
4. `onTimeout` cancels sync Jobs via `MetadataSyncWorker.stop()` then stops the
   dataSync FGS (Android 15+ 6h limit).
5. Playlist silent refresh re-checks pending/conflict **after** `getPlaylist`
   before write (TOCTOU). Track writes use `upsertTracksPreserveCache` (never
   bare `REPLACE`). Abort entry clear when `songCount > 0` but `entry`
   missing/malformed.
6. WM: `cooldown` → `Result.success()` (with `already_running`) to avoid hot
   retry loops; offline / unreachable / cellular still retry.
