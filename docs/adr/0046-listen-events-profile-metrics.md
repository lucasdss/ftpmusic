# ADR 0046 — Listen Events for Profile Metrics

Date: 2026-09-28
Status: Accepted
Related: ADR 0009 (pending_scrobbles removed), ADR 0013 (local-first)

## Context

Profile showed lifetime `SUM(play_count)` and inflated
`SUM(duration_seconds * play_count)`. No week/month/year, no top lists, no
streak. Spotify / Apple Music / YT Music Profile-Replay surfaces need
time-bucketed history and honest listen minutes.

`pending_scrobbles` (removed ADR-0009) was an unused server-retry queue — not
a listening-history log. Restoring that name would confuse intent.

`play_count` on `tracks` still feeds Daily Mix weighting — must keep.

## Decision

1. Add local-only `listen_events` (Room v53) written on every successful local
   scrobble with denormalized track/artist/album/genre fields.
2. Pass honest `listenedSeconds` from MediaService player position (clamp ≥1).
3. Keep `incrementPlayCount` dual-write for mix weights.
4. Profile uses dedicated `ProfileViewModel` + period chips (Week/Month/Year/All)
   against event aggregates. Top-5 lists + day streak + recent-from-events.
5. Migration backfill expands existing `play_count` into N events so All-time
   is not empty after upgrade.
6. No prune v1; indexed `listened_at`.

## Consequences

- Period metrics and honest minutes become possible.
- DB grows ~1 row per scrobble; acceptable for personal library scale.
- Pre-migration multi-play timestamps collapse to `last_played_at` (streak/week
  may undercount until new listens).
- LibraryViewModel lifetime stats become legacy / Home unused path.
