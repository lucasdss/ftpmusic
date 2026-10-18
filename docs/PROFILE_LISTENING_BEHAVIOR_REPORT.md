# Profile Listening — Behavior Report

Date: 2026-10-12
ADR: 0046, 0047, 0128

## Surface

Settings → Profile. Dedicated `ProfileViewModel`.

## Periods

| Chip | Window |
|------|--------|
| Week | calendar ISO week Mon 00:00 (device TZ) → now |
| Month | calendar month start → now |
| Year | calendar year start → now |
| All time | unbounded (chart = last 12 months) |

## Metrics (per period)

| Metric | Source |
|--------|--------|
| Minutes | `SUM(listened_seconds) WHERE is_backfill=0` / 60 |
| Plays / songs / artists | COUNT / DISTINCT; **backfill excluded** for Week/Month/Year; included for All time |
| Day streak | all-time consecutive local days (UI: "All time") |
| Tops | GROUP BY; rank `playCount DESC, honestSeconds DESC` LIMIT 5; UI shows plays + minutes |
| Activity chart | honest seconds by day (W/M) or month (Y / All-time 12m) |
| Genre mix | Top Genres share strip + share bars |
| Recently played | distinct tracks in period by max(`id`) (deterministic latest); seconds from that row |

## Write path (Last.fm — ADR-0128)

MediaService accumulates heard ms while playing (pause-aware; wall × playback
speed). Scrobble when:

1. Track duration **> 30s**
2. Heard ≥ **half duration** OR **≥ 4 minutes**
3. Ghost floor (`shouldScrobbleAfterListen`) passes

Then `ScrobbleService.scrobble` → Subsonic API (if online) → null-preserving
`ensureTrackRow` → `incrementPlayCount` → insert `listen_events` (`is_backfill=0`).

`ensureTrackRow` preserves **all** local-weight fields (`play_count`,
`last_played_at`, `starred_at`, `is_disliked`, `cached_file_path`,
`is_downloaded`, …) via `copy()`; track upserts in starred-sync, search cache,
and playlist flows use `upsertTracksPreserveCache` (not bare REPLACE) so a play
never zeroes the aggregate counters (Fix 0, 2026-10-18).

## Backfill

v53: expand `play_count` into N events (approximate).
v54: all existing rows marked `is_backfill=1` — excluded from minutes.
v63: repair null `album_name` from `tracks.album`.

## Market note

| Product | Play gate | Minutes | Tops |
|---------|-----------|---------|------|
| Spotify Wrapped | >30s | actual heard | songs by play count |
| Last.fm | >30s + 50%/4min | scrobble history | charts |
| Apple Replay | plays + time | both | songs by plays |
| FTPMusic | Last.fm gate | accumulated heard | plays then minutes |

## Edges

- Offline: event still written
- Empty period: zeros / empty lists
- Process death mid-track: no event until scrobble
- Tracks ≤30s: never scrobble
- Seek: does not inflate (wall deltas while playing)

## Out of scope

Wrapped/share, discovery %, prune, fan percentiles, listening clock, product analytics.
