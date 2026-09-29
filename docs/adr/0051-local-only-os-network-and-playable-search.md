# ADR 0051 — Local-Only OS Network + Playable Search

Date: 2026-09-29
Status: Accepted
Related: ADR 0013 (local-first), ADR 0022 (offline enforcement), ADR 0043 (unreachable vs offline),
docs/OFFLINE_LOCAL_FIRST_BEHAVIOR_REPORT.md

## Context

Search offline returned full synced Room metadata; Library offline already filtered to
playable albums/artists. Airplane mode left `ReachabilityStateHolder` true until probe
failed → search still hit API; first uncached open could stall. User asked: playable-only
offline search (1A) + treat true no-OS-network like offline for search/play gates (2C).

## Decision

1. **`NetworkAvailabilityHolder`** — StateFlow OS INTERNET capability. Seeded at boot;
   updated via `ConnectivityNetworkWatcher` `onAvailable` / `onLost` /
   `onCapabilitiesChanged`. Wired from `ServerReachabilityMonitor.start` + `FtpmusicApp`.
   **Lost / caps-down:** re-check `activeNetwork` INTERNET before flipping unavailable
   (WiFi drop while cell up must not false local-only).

2. **`LocalOnlyPolicy.isLocalOnly(offline, hasOsNetwork)`** — browse/search gate.
   Does **not** flip `OfflineModeManager` (ADR 0043 preserved: Offline chip ≠ unreachable).
   Search/Library **collect** offline + OS flows; flip → re-search / reload browse.

3. **Upstream order** in `OfflineAwareHttpDataSource.open`: software offline → no OS
   network → server unreachable → else open.

4. **Playable search** when local-only: DAO `searchPlayableTracks|Albums|Artists`
   (`cached_file_path IS NOT NULL OR is_downloaded = 1`), skip API/pagination, force
   downloaded filter.

5. **Library/Home** browse / Surprise Me / API skip use `isLocalOnly()`. Banner still
   uses `isOffline()` (Simulate Offline only).

## Consequences

- Airplane: immediate playable browse/search + fail-fast uncached play.
- Simulate Offline UX unchanged (chip, Settings toggle).
- Outside-LAN with cellular still uses reachability gate for stream; search stays hybrid
  until API fails (not auto playable-only).
