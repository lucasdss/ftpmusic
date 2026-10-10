# ADR 0109 — Home Recently Added TTL and Delta Trigger

Date: 2026-10-10
Status: Accepted
Related: ADR 0045 (adaptive delta), ADR 0108 (corpus reconcile),
docs/LIBRARY_SYNC_BEHAVIOR_REPORT.md

## Context

Home **Recently Added** called `getAlbumList2(newest, 10)` on every load while
online. Background DELTA only ran on WorkManager Sync Interval after a recent
FULL watermark — users who only Resync never saw delta, and Home always hit the
network.

## Decision

1. **Local snapshot** — ordered top-10 album ids + `fetched_at` in `ftpmusic_sync`
   prefs; rows from `cached_albums`.
2. **TTL** — Settings “Recently Added refresh”, default 5 minutes, range
   **1 … Sync Interval (minutes)**. Changing Sync Interval reclamps TTL.
3. **Home open** — paint snapshot first; if stale + online, fetch newest-10;
   if ordered ids change → `syncNowAsync(DELTA)` (same gates as WM).
4. **Sync process** — after album phase, refresh snapshot: DELTA reuses newest
   pages’ first 10; FULL fetches `newest` size=10 (alpha list is not newest).

## Consequences

- Home strip is offline-capable from snapshot.
- TTL cannot outlive Sync Interval.
- Discovering new albums on Home can start DELTA without waiting for WM.
- Identical newest lists after TTL refresh do not spam DELTA.
