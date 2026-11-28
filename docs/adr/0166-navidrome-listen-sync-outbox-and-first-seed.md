# ADR 0166: Navidrome listen sync outbox + first-setup seed

Date: 2026-11-28  
Status: Accepted  
Related: ADR 0009, ADR 0022, ADR 0046, ADR 0047

## Context

Scrobble API calls were fire-and-forget without `time`, retries, or ack. Offline / failed submits never reached Navidrome. ADR 0009 removed a dead `pending_scrobbles` table; ADR 0022 listed queue reintroduction as a follow-up. First setup ignored server `playCount` / `playDate`, so a new client started with empty listening weights.

OpenSubsonic has no per-listen history **read** API. Aggregate song fields are the portable seed source.

## Decision

1. Add `server_synced_at` + `sync_attempts` on `listen_events`; grandfather existing rows as synced.
2. Pass `time=listened_at` on `rest/scrobble` submissions; ack only on Subsonic ok.
3. `ScrobbleSyncWorker` drains pending honest events (PlaylistSyncWorker pattern).
4. First FULL sync seeds `tracks.play_count` / `last_played_at` and capped `is_backfill` events from song `playCount`/`playDate`, then sets `listen_history_seeded`.
5. Never drain `is_backfill` rows. No native ND JWT history client in v1.

## Consequences

- Offline listens catch up with correct timestamps.
- Upgrade users do not double-count (grandfather).
- Profile honest minutes stay ADR-0047 clean; seed fills play charts / Daily Mix weights only.
- Rare double-submit possible if process dies after ok before ack.
