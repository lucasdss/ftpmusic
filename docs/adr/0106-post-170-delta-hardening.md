# ADR 0106 — Post-1.7.0 Delta Hardening (BT / Sync / Cellular)

Date: 2026-10-09
Status: Accepted
Related: ADR-0101, ADR-0105, ADR-0018

## Context

Seven commits landed after Play internal `1.7.0` (versionCode 11) without a
version bump. Review of `v1.7.0..HEAD` confirmed several user-visible failure
modes at the BT seat, Syncing UI, DownloadManager, and policy migration seams.

## Decision

1. **BT network block = OfflineAwareHttpDataSource gates** — include cellular
   `LOCAL_ONLY` via `computeBtPlaybackNetworkBlocked` /
   `isBtPlaybackNetworkBlocked`.
2. **Offline seat wrap** — `resolveOfflineStartIndex` prefers at/after current,
   then wraps before current so a mid-queue BT wake still finds cache.
3. **Syncing never hangs** — user-triggered force sync that cannot obtain a job
   after retries sets `_isError` + `_isDone` (RAM cellular override stays
   non-persistent per ADR-0105).
4. **DownloadManager live transport** — refresh `NetworkPolicyState` each worker
   tick; gate offline via `isOfflineEnabled` (mock-friendly).
5. **Legacy migrate once** — after writing `KEY_CELLULAR_MEDIA_POLICY`, remove
   `KEY_DOWNLOAD_MOBILE_DATA` so a wiped new key cannot re-migrate from stale
   boolean.

## Consequences

- Cellular hard-local + BT wake seeks cached seat (or offline notif) instead of
  error/skip cascade.
- Settings Resync that loses override / hits policy skip exits Syncing with error
  UI rather than an infinite spinner.
- Stale optimistic `wifiOrEthernet=true` cannot leak priority-0 downloads under
  `LOCAL_ONLY` once ConnectivityManager answers.
- Ship as Play internal **1.8.0** (versionCode 12).
