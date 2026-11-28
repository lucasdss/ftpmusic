# Spec: Navidrome listen-history sync

Status: Accepted  
Related: ADR-0166, ADR-0009, ADR-0022, ADR-0046, ADR-0047

## Goal

1. **Client → server:** every honest local scrobble eventually reaches Navidrome with `time = listened_at`.
2. **Server → client (first setup):** seed local play counts / Profile backfill from Subsonic `playCount` + `playDate` once.

## Contracts

### Outbox (`listen_events`)

| Field | Rule |
|-------|------|
| `server_synced_at` | null = pending server submit; non-null = acked or grandfathered / seed |
| `sync_attempts` | incremented on drain failure; poison-skip after 8 |
| `is_backfill` | never drained to server |

Live path: local write first → `api.scrobble(id, submission=true, time=listenedAt)` → mark synced on Subsonic `status=ok`. Offline / fail leaves pending.

Drain: oldest pending first, batch ≤20, skip offline / no creds / backfill.

### First-setup seed

- Gate: SecureStorage `listen_history_seeded` false **and** no honest (`is_backfill=0`) events.
- Runs after successful FULL track drain.
- For each song map seen in sync with `playCount > 0`: `play_count = max(local, server)`, `last_played_at = max(local, playDate)`, insert `min(playCount, 100)` backfill events with `server_synced_at` set.
- Then set `listen_history_seeded=true`.

### Non-goals (v1)

- Push pre-feature local history (grandfathered).
- Continuous other-client play merge.
- Navidrome native JWT `/api/scrobble` history read.

## Edge cases

See ADR-0166 edge matrix (offline, poison id, process-death double-submit, re-login skip seed).
