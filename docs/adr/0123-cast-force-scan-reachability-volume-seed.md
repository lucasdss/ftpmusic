# ADR-0123: Cast Force Scan, Reachability Honesty, Volume Seed Republish

**Date:** 2026-10-12
**Status:** Accepted
**Relates:** ADR-0008, ADR-0010, ADR-0026, ADR-0028
**Supersedes (Android volume/discovery UX gaps):** none — ADR-0003 is Flutter-only historical

## Context

Cast works, but three product gaps remain:

1. **Force search** — MediaRouter already discovers (`PERFORM_ACTIVE_SCAN`, dialog-open refresh, startup poll), yet the picker has no user-facing “search again”.
2. **Connectivity without Google Services** — callers want a non-GMS ping. Cast playback path is Cast Framework + MediaRouter; ICMP/raw TCP/custom mDNS do not prove receiver readiness and create false positives.
3. **Now Playing volume blast** — first raise after connect can send ~`1.0` because `seedFromSessionVolume` updates `PlayerHolder` without republishing `PlaybackState`, so the slider baseline stays at local/`PlaybackState` default `1.0f`.

## Decision

### 1. Force scan UI

- Expose `CastButtonState.onForceScanRequested` + `isScanning`.
- Picker shows **Search again** (header + empty state).
- NavHost handler: remove discovery callback → re-add with `REQUEST_DISCOVERY | PERFORM_ACTIVE_SCAN | UNFILTERED_EVENTS` → `refreshCastRoutes`.
- Keep dialog-open auto-refresh. Shorten startup poll (callbacks + explicit scan suffice).

### 2. Reachability = SDK-honest, not non-GMS

- Pre-session: MediaRouter route add/remove remains list truth.
- Live session: while casting, periodic `RemoteMediaClient.requestStatus()` with timeout; soft “connection weak” via existing Cast error/UI channels; hard teardown still `SessionManagerListener`.
- **Out of scope:** ICMP, raw socket ping, parallel NsdManager discovery as cast health.

### 3. Volume seed + gesture baseline

- After every `seedFromSessionVolume`, call `MediaSessionPlaybackProvider.republishAfterCastVolumeSeed()` so UI volume matches holder immediately.
- Cast slider uses `resolveCastDragBaseline(uiVolume)` at drag start: prefer pending → confirmed `castVolume` → `castDeviceVolume` when UI looks stale (~1.0 with holder elsewhere, or ~0 with holder mid-scale).
- Keep cube-root curve, spurious-zero filter, pending 3s TTL, untrusted-0 ignore.

## Consequences

- Users can force rediscovery without leaving the picker.
- Connectivity signaling stays on the real Cast path (no fake “device online but cannot cast”).
- First volume gesture after connect tracks device level instead of blasting to max.
- Tests cover seed→republish and stale-1 baseline resolution.
