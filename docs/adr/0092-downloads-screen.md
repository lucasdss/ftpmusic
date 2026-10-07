# ADR 0092 — Offline Downloads Screen

Date: 2026-10-07
Status: Accepted
Related: ADR-0013 (local-first), ADR-0022 (offline mode)

## Context

Cache engine pinned explicit downloads, but Settings only showed bytes + clear-all.
Peers expose a Downloads browse UI. Stale `cached_file_path` could outlive SimpleCache spans.

## Decision

1. Settings → **Manage downloads** → `downloads` route.
2. List `is_downloaded = 1` via `TrackDao.getDownloadedPaged`.
3. Actions: play (CONTEXT album-style list), remove one (`CacheService.removeDownload`), clear all.
4. On load: `healStaleCachePath` when Room path set but span missing; keep `is_downloaded` flag.

## Consequences

- Users can audit and reclaim storage without wiping auto-cache.
- Stale rows surface a warning instead of silent play failures.
