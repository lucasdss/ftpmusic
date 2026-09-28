# Profile Listening — Behavior Report

Date: 2026-09-28
ADR: 0046

## Surface

Settings → Profile. Dedicated `ProfileViewModel` (not Library VM).

## Periods

| Chip | Window |
|------|--------|
| Week | rolling last 7 days `[now-7d, now]` |
| Month | calendar month start → now (device TZ) |
| Year | calendar year start → now |
| All time | unbounded |

## Metrics (per period)

| Metric | Source |
|--------|--------|
| Minutes | `SUM(listened_seconds)/60` |
| Plays | `COUNT(*)` events |
| Songs | `COUNT(DISTINCT track_id)` |
| Artists | `COUNT(DISTINCT artist_id)` (null artist_id excluded) |
| Day streak | consecutive local calendar days w/ ≥1 event ending today |
| Top songs/artists/albums/genres | `GROUP BY` count DESC LIMIT 5 |
| Recently played | distinct tracks by max(`listened_at`) DESC LIMIT 20 |

## Write path

MediaService scrobble (≥60% or AUTO transition) → `ScrobbleService.scrobble(..., listenedSeconds)` →

1. Subsonic API if not software-offline
2. `ensureTrackRow` + `incrementPlayCount` (Daily Mix weights)
3. insert `listen_events` row (`listened_seconds = max(1, positionSec)`)

## Backfill (v52→v53)

Expand each `tracks.play_count > 0` into N events at `COALESCE(last_played_at,0)`,
`listened_seconds = COALESCE(duration_seconds, 180)`. Pre-upgrade All-time/tops populated;
Week/Month empty if timestamps old.

## Edges

- Offline: event + play_count still written; server skip
- Empty period: zeros, empty tops, "Nothing played yet"
- Process death mid-track: no event until scrobble fires
- Never-played catalog rows: excluded from recent (events only)
- ADR-0009 pending_scrobbles ≠ this table (was dead server-retry queue)

## Out of scope

Product analytics SDKs, Last.fm Wrapped, share cards, avatar, prune.
