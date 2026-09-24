# Reachability Keepalive — Behavior Report

Date: 2026-09-24
Status: Implemented
Related: ADR 0041, ADR 0023

## Signals (do not confuse)

| UI | Source | Auto-recover? |
|---|---|---|
| AppHeader Offline chip + ServerErrorBanner | `ReachabilityStateHolder.isReachable` | **Yes** — keepalive + NetworkCallback |
| Settings Simulate Offline + PlayerBar Offline | `OfflineModeManager` | **No** — user sticky (ADR 0022) |

## Bug (before)

`BaseUrlInterceptor` set `isReachable=false` on connect/timeout/DNS fail. Clear only on next successful `chain.proceed`. Idle UI → badge stuck after net/server back. `ServerReconnectionService` dead, never started.

## Fix

`ServerReachabilityMonitor` (process-scoped, started in `FtpmusicApp.onCreate` after config + offline restore):

1. **Keepalive ping** via `SubsonicApi.ping` — 15s when unreachable, 60s when reachable
2. **NetworkCallback** (`NET_CAPABILITY_INTERNET`) → debounced (1s) immediate probe
3. **Single-flight** `Mutex` — callback + timer never stack concurrent pings
4. **Guards** — skip if Simulate Offline ON, URL blank, or missing credentials
5. **State write** — `ReachabilityStateHolder.onApiSuccess` / `onApiFailure` (also interceptor path in prod)

## Flow

```
API fail → isReachable=false → AppHeader Offline + Banner
OS net up → NetworkCallback → probe (debounced)
Timer tick → probe
probe ok → isReachable=true → UI clear
Simulate Offline ON → probe skip (banner already suppressed)
```

## Intervals

| State | Interval |
|---|---|
| unreachable | 15_000 ms |
| reachable | 60_000 ms |
| network debounce | 1_000 ms |
| probe timeout | 5_000 ms |

## Out of scope

- Auto-clear Simulate Offline
- Compose UI changes (already collect `isReachable`)
- Cast session reconnect
