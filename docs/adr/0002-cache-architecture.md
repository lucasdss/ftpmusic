# ADR-0002: Cache Architecture — Single Source of Truth, Eviction, and Warming

## Status
Accepted (2026-06-16)

## Decision

The cache layer is refactored around three principles:

1. **Drift DB is the single source of truth.** The in-memory `_index` map and `_cache_index.json` file are removed. All cache lookups, stats, and eviction queries go through the `Tracks` table. Queries use paginated limits + offsets to handle large caches without performance degradation.

2. **LRU eviction uses `last_played_at`.** A new `last_played_at` column on the `Tracks` table is updated on every scrobble. Auto-cache eviction queries `WHERE is_auto_cached = 1 AND is_downloaded = 0 ORDER BY last_played_at ASC`. Downloaded tracks are never evicted. When a track is both auto-cached and downloaded, the download flag takes precedence — it is immune to eviction.

3. **Cache warming is a first-class feature.** Three new columns — `last_played_at`, `play_count`, and `cached_at` — enable ranking tracks by listening patterns. Warming operates in phases: Phase 1 does passive batch pre-caching on Wi-Fi + charging (smart mix: starred → top-played → recent), initially seeded from server API data. Phase 2 does opportunistic per-track warming after playback. Phase 3 (future) does smart processing on Wi-Fi + charging.

## Context

The previous cache implementation had critical flaws:
- **Double source of truth**: in-memory `_index` map persisted to JSON diverged from the Drift `Tracks` table on crashes.
- **No auto-cache pipeline**: `writeCachedTrack()` was only called by explicit downloads, never during streaming playback — contradicting the CONTEXT.md definition of auto-cache.
- **No play tracking**: no `playCount` or `lastPlayedAt` columns meant "most played" and "recently played" features were impossible.
- **LRU was lossy**: `lastAccess` timestamps were updated in memory but only flushed to disk on writes/removes, so restarts lost up-to-date LRU ordering.
- **Performance**: full `_cache_index.json` was serialized on every write (O(n)).
- **Broken UX**: the "clear auto-cache" button iterated an empty list — no API existed to enumerate auto-cached tracks.

## Considered Options

### Source of truth
- **Dual system with startup reconciliation**: Keep JSON + DB, cross-check on startup. Rejected — adds complexity without eliminating the divergence window.
- **JSON file only, no DB columns**: Rejected — DB tracks already exist, and the columns are needed for UI queries (cache status badges).
- **Drift DB only (chosen)**: Single source of truth. Startup queries are fast with paginated limits. No reconciliation needed.

### Auto-cache implementation
- **Unified local proxy on both platforms**: Rejected — adds TCP loopback overhead on Android where ExoPlayer's native `CacheDataSource` is more battery-efficient.
- **Dart-layer byte interception**: Rejected — `just_audio` does not expose raw stream bytes to Dart.
- **Platform-native (chosen)**: ExoPlayer `CacheDataSource` on Android (per Architecting.md §"Android Implementation: Media3 and CacheDataSource"), local HTTP proxy on iOS (per Architecting.md §"iOS Implementation: The Local Proxy Server Pattern").

### Auto-cache quota
- **Default 100MB**: Rejected — too small for meaningful warming.
- **Default 1000MB with 100MB streaming floor (chosen)**: Large enough for warming to be useful, with a guaranteed minimum for stream-to-cache accumulation.

## Consequences

**Positive:**
- Single source of truth eliminates an entire class of cache inconsistency bugs.
- `play_count` and `last_played_at` enable cache warming and "most played" / "recently played" features.
- LRU eviction is now accurate across restarts.
- No more O(n) JSON serialization on every write.
- Clear auto-cache button becomes functional.

**Negative:**
- Schema migration required (3 new columns on Tracks table).
- `CacheService.initialize()` must be called after construction to rebuild the in-memory write-through cache from DB. This requires the app's composition root (Riverpod provider tree) to be wired first — `databaseProvider`, `cacheServiceProvider`, and all dependent services are currently declared as stubs with no overrides in `main.dart`. This is scoped as a separate prerequisite task (see plan §Future Tasks).
- Two platform-specific auto-cache implementations to maintain (Android CacheDataSource + iOS proxy).
- Cache warming adds a background worker that must handle connectivity and battery constraints.
