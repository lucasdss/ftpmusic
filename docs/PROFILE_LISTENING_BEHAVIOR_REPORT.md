# Profile Listening — Behavior Report

Date: 2026-09-28
ADR: 0046, 0047

## Surface

Settings → Profile. Dedicated `ProfileViewModel`.

## Periods

| Chip | Window |
|------|--------|
| Week | calendar ISO week Mon 00:00 (device TZ) → now |
| Month | calendar month start → now |
| Year | calendar year start → now |
| All time | unbounded |

## Metrics (per period)

| Metric | Source |
|--------|--------|
| Minutes | `SUM(listened_seconds) WHERE is_backfill=0` / 60 |
| Plays | `COUNT(*)` (includes backfill) |
| Songs | `COUNT(DISTINCT track_id)` |
| Artists | `COUNT(DISTINCT COALESCE(artist_id, artist_name))` |
| Day streak | all-time consecutive local days (UI: "All time") |
| Tops | GROUP BY; rank `SUM(honest seconds) DESC, COUNT(*) DESC` LIMIT 5 |
| Recently played | distinct tracks in **period** by max(`listened_at`) LIMIT 20 |

## Write path

MediaService (≥60% transition **or** `STATE_ENDED`) → `ScrobbleService.scrobble` →

1. Subsonic API if not software-offline
2. `ensureTrackRow` + enrich missing genre/artist/album from Room
3. `incrementPlayCount`
4. insert `listen_events` (`is_backfill=0`, seconds clamped `[1, duration]`)

One-shot guard avoids double scrobble transition + ENDED.

## Backfill

v53: expand `play_count` into N events (approximate).
v54: all existing rows marked `is_backfill=1` — excluded from minutes only.

## Edges

- Offline: event still written
- Empty period: zeros / empty lists
- Process death mid-track: no event until scrobble
- Seek past duration: seconds capped

## Out of scope

Wrapped/share, discovery %, prune, product analytics.
