# ADR 0047 — Profile Listen Metrics Honesty (v54)

Date: 2026-09-28
Status: Accepted
Related: ADR 0046 (listen_events)

## Context

ADR-0046 backfill used full `duration_seconds × play_count`, inflating All-time
minutes. Genre/artist denorm often missing from MediaItem extras → empty Top
Genres / undercounted artists. Week was rolling 7d; recent ignored period chip.
Last queue track often never scrobbled (no transition).

## Decision

1. Room v54: `is_backfill` column; mark all pre-existing events `1`. Minutes =
   `SUM` where `is_backfill=0` only. Plays/tops still include backfill.
2. Tops rank by honest-seconds sum, then play count.
3. Scrobble enriches genre/artist/album from `tracks` when extras blank; cap
   `listened_seconds` to track duration; `STATE_ENDED` scrobble with dedupe guard.
4. Week = calendar Monday start (device TZ). Recent = period-scoped.
5. Streak remains all-time; UI labels "All time".

## Consequences

- Upgrade users: All-time minutes restart from live scrobbles (honest).
- Tops still populated from backfill via count tiebreak when honest seconds = 0.
- Calendar week aligns with Spotify/Apple week framing.
