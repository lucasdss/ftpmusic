# Offline / Local-First Behavior Report

Date: 2026-09-29
Related: ADR 0013, 0022, 0043, 0051

## Contracts

- **Simulate Offline** = user toggle (`OfflineModeManager`). PlayerBar Offline chip.
- **OS network lost** (airplane) = `NetworkAvailabilityHolder.hasOsNetwork=false`. No auto-toggle Offline.
- **Server unreachable** = `ReachabilityStateHolder`. AppHeader "Server unreachable".
- **Local-only browse/search** = `LocalOnlyPolicy.isLocalOnly` = offline OR !osNetwork.
- **Reactive flip** = Search/Library collect offline + `hasOsNetwork`; enter/leave local-only
  re-runs active search / reloads artists+albums+genres.
- **OS lost honesty** = watcher re-checks `activeNetwork` INTERNET on iface lost (multi-net).
- **Upstream block** = offline OR !osNetwork OR !reachable (`OfflineAwareHttpDataSource`).
- Cache hits never open upstream.

## Matrix

| Surface | Simulate Offline | Airplane (toggle OFF) | Unreachable (OS up) |
|---------|------------------|----------------------|---------------------|
| Search | playable Room only; no API | same | API may fail; online path; optional filterDownloaded |
| Library browse | getOfflineAlbums/Artists | same (isLocalOnly) | full Room + API fail |
| Surprise Me | getRandomCachedTracks | same | API random |
| Play downloaded/full cache | works (SimpleCache) | works | works |
| Play uncached | fail-fast IOException → skip | fail-fast | fail-fast |
| Downloads / sync / scrobble API | blocked (offline) | blocked when !osNetwork via existing constraints / fail | sync skip if !reachable |
| Offline chip | ON | OFF | OFF |
| Banner unreachable | suppressed if offline | may show after probe | ON |

## Search (1A)

Local-only → `searchPlayableTracks` / `searchPlayableAlbums` / `searchPlayableArtists`
(`is_downloaded=1 OR cached_file_path IS NOT NULL`). Forces `filterDownloaded=true`; clear refused.

## Play

MediaItem = Subsonic HTTP URL. Key = track `id` query. Full SimpleCache span → play.
Partial/missing → upstream gate throws → `findNextCachedIndex` / stop.

## Gaps (known)

- Room `cached_file_path` may outlive partial/evicted span → miss → skip.
- No dedicated Downloads screen.
- Cast needs network (out of scope).
- Unreachable alone does **not** force playable-only search (only local-only = offline|!osNetwork).

## Validation

Unit: LocalOnlyPolicy, NetworkAvailabilityHolder, OfflineAwareDataSource, SearchViewModel
local-only + mid-session OS flip re-search, Library Surprise Me local-only,
ConnectivityNetworkWatcher onLost multi-net revalidate.
