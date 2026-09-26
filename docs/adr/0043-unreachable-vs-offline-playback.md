# ADR 0043 — Unreachable vs Offline Playback

Date: 2026-09-26
Status: Accepted
Related: ADR 0022 (offline enforcement), ADR 0041 (reachability keepalive),
ADR 0023 (server-unreachable UX)

## Context

On cellular outside the LAN, the phone has OS network but the home Subsonic
server is unreachable. `ReachabilityStateHolder` correctly goes false (AppHeader
showed "Offline" — users read this as "no network"). Playback still opened HTTP
upstream because only `OfflineModeManager` (Simulate Offline) gated
`OfflineAwareHttpDataSource`. Result: multi-second socket stalls, broken Next,
force-close loops. Keepalive (ADR 0041) fixed the badge recovery path, not the
hang.

## Decision

1. **Copy** — AppHeader / banner: **Server unreachable** when `!isReachable`
   (distinct from PlayerBar Offline = Simulate Offline).
2. **Fail-fast upstream** — `OfflineAwareHttpDataSource.open` throws if
   software offline **or** `!ReachabilityStateHolder.isReachable`. Cache hits
   never hit this source.
3. **Skip / Next** — uncached tracks error in ms; existing `onPlayerError`
   auto-skip advances the queue (bounded by retry limit). No new Cast changes.
4. Do **not** auto-enable Simulate Offline when unreachable.

## Consequences

- Outside-LAN play uses cache only; UI no longer implies the phone has no data.
- When server returns, keepalive / successful API clears reachability and streaming resumes.
- Release builds still strip `ftpmusic-*` Log lines — diagnosis prefers system MediaSession + this contract.
