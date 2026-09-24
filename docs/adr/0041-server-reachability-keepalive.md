# ADR 0041 — Server Reachability Keepalive

Date: 2026-09-24
Status: Accepted
Related: ADR 0023 (server-unreachable UX), ADR 0022 (offline enforcement)

## Context

`ReachabilityStateHolder` was updated only by `BaseUrlInterceptor` on live API
traffic. After a connectivity failure, the AppHeader Offline chip and
`ServerErrorBanner` stayed visible until some other call succeeded. Idle
screens never recovered. `ServerReconnectionService` claimed auto-reconnect but
was never started and incorrectly documented Offline-mode coupling.

## Decision

1. Replace dead `ServerReconnectionService` with process-scoped
   `ServerReachabilityMonitor`.
2. Probe with Subsonic `ping` through the shared `SubsonicApi` (singleton OkHttp).
3. Drive probes from:
   - keepalive timer — 15s while unreachable, 60s while reachable
   - `ConnectivityManager.NetworkCallback` for `NET_CAPABILITY_INTERNET`
     (1s debounce)
4. Guard probes when Simulate Offline is on, server unconfigured, or credentials
   missing. Never auto-toggle `OfflineModeManager`.
5. Start monitor in `FtpmusicApp.onCreate` after `ServerConfigStore` +
   `OfflineModeManager` initialize.
6. Declare `ACCESS_NETWORK_STATE` (needed for NetworkCallback; also used by
   download constraints).

## Consequences

- Offline UI recovers without user navigation when the server returns.
- Background keepalive costs one lightweight ping every 15–60s while the
  process is alive; acceptable for a media app process.
- Reachability and software-offline remain separate signals (ADR 0022/0023).
- Unit tests inject a fake `NetworkWatcher` to avoid Robolectric for the
  debounce / single-flight paths.
