# ADR 0105 — Sync and Cellular Media Policy

Date: 2026-10-09
Status: Accepted
Related: ADR 0045 (delta sync), ADR 0068 (sync completion), ADR 0013 (local-first),
ADR 0051 (local-only OS network), ADR 0043 (unreachable vs offline)

## Context

Library metadata sync (FULL + DELTA) ran on any `NetworkType.CONNECTED` network.
Media downloads used a boolean `download_mobile_data` that still allowed priority-0
queue prefetch and ExoPlayer upstream streaming on cellular. Users need:

1. Library sync (FULL **and** DELTA) optional Wi‑Fi-only.
2. Cellular media modes: auto-cache albums, minimal (queue + now playing), or
   hard local-only (no stream / prefetch / sync).
3. Warnings before large cellular transfers on first-login and gated manual resync.

## Decision

1. **`KEY_LIBRARY_SYNC_WIFI_ONLY`** — when true, automatic FULL/DELTA sync and
   enrich skip on cellular unless one-shot `allowCellularOverride`.
2. **`KEY_CELLULAR_MEDIA_POLICY`** — `auto_cache` | `minimal` | `local_only`.
   Replaces UX of `KEY_DOWNLOAD_MOBILE_DATA` (migrate once, stop writing).
3. **`NetworkTransportPolicy`** — Wi‑Fi / Ethernet vs cellular via transports.
4. **`NetworkPolicyHolder`** — live prefs + transport for workers and playback.
5. **`LocalOnlyPolicy`** extended: offline OR no OS net OR (cellular AND local_only).
6. WorkManager uses `UNMETERED` when sync Wi‑Fi-only; in-worker transport remains
   source of truth.
7. First-login always warns on cellular; manual Resync warns only when sync
   Wi‑Fi-only is ON and device is cellular.

## Consequences

- Hard local on cellular matches Simulate Offline browse/play filters without
  flipping `OfflineModeManager`.
- DELTA cannot burn cellular when Wi‑Fi-only sync is enabled.
- Override is not persisted across process death.
