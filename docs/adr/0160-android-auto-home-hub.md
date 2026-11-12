# ADR 0160 — Android Auto Home Hub

Date: 2026-11-12
Status: Accepted
Related: ADR-0091, ADR-0131, ADR-0136 (Home was P2 market gap)
Behavior: `docs/ANDROID_AUTO_HOME_BEHAVIOR_REPORT.md`

## Context

Auto browse was a flat library tree. Phone Home already surfaces Continue /
Daily Mixes / Surprise Me / favorite playlists / artists / radio. Drivers
need a Spotify-like Home entry without Car App carousels.

## Decision

1. Insert `auto_home` as first root child; keep library nodes after Queue/Recent.
2. Home sections (omit when empty): Continue listening, Surprise Me (playable),
   Daily Mixes, Favorite playlists (synced only), Favorite artists, Favorite radio.
3. Reuse Room DAOs / existing expand paths; list content-style only.

## Consequences

- Driver-first discovery on Auto without AAOS.
- Root grows to 11 top-level nodes.
