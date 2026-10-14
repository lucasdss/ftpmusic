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
   - WorkManager: soft skips `offline` / `unreachable` / `cellular_policy` /
     `cooldown` → `Result.retry`. `already_running` → `Result.success`.
   - Durable `sync_skip_albums` queue: failed `getAlbum` upserts; success deletes;
     next sync prioritizes skip IDs. No 3-consecutive abort / no >5% phase=error
     for per-album fails. Album-list fail-closed (ADR-0068) unchanged.

4. **`LibrarySyncService` (`dataSync`)** — Started when a sync Job actually
   begins; stopped in `finally`. Separate from `MediaService`. On
   `ForegroundServiceStartNotAllowedException`, sync continues best-effort
   without FGS. No fake 6h restart loops (ADR-0019).

## Consequences

- Full / long DELTA syncs can run much longer; user sees a low-priority sync
  notification while FGS is allowed.
- Imported playlists stay closer to server without opening detail.
- Transient album fetch failures no longer fail the whole sync watermark; they
  converge across runs via the skip queue.
- Tests and behavior report updated; Room v66 adds `sync_skip_albums`.
