# ADR 0120 — AppHeader Issue Status Tray

Date: 2026-10-11
Status: Accepted
Related: ADR-0023 (server-unreachable UX), ADR-0041 (keepalive), ADR-0043
(unreachable vs offline), ADR-0051 (local-only), ADR-0055 (settings in header),
ADR-0095 (NP sticky error)

## Context

ADR-0023 put a full-width yellow `ServerErrorBanner` on Home/Library and an
AppHeader text pill for "Server unreachable". Those surfaces duplicate one
signal, crowd Settings/Cast, and cannot represent concurrent issues (OS
network down, loopback config, Simulate Offline, playback error, playlist
sync conflict, cellular local-only).

Market apps (Spotify, Slack, Gmail, Maps, YT Music) use a single global
connectivity surface or per-item offline cues — never stacked yellow banners.

## Decision

1. **Single global chrome** — Compact status icon immediately left of Settings
   in `AppHeader`. Tap opens `IssueStatusSheet` (ModalBottomSheet) listing all
   active `AppIssue` rows with actions. Remove `ServerErrorBanner`, AppHeader
   text pill, NP sticky yellow banner, and playlist detail Sync Conflict banner.
2. **Aggregator** — `AppIssueAggregator` combines OS network, reachability,
   Simulate Offline, loopback config, cellular local-only, `PlayerHolder`
   playback error, and Room `observeConflicted()` playlists. Connectivity
   signals debounce ~2.5s.
3. **Icon encoding** — Single-cause icons (WifiOff / CloudOff / Warning /
   ErrorOutline / SyncProblem); 2+ kinds → generic Warning + count badge.
   Simulate Offline is a muted mode row, not an error. Offline mode still
   suppresses the unreachable row (ADR-0023 §4 preserved).
4. **Actions** — Fix/Settings for config+unreachable; Dismiss for playback
   error; navigate `playlist/{id}` for conflicts; informational for OS-net /
   cellular local-only.
5. **Settings inline warnings** — Loopback/cleartext under the URL field stay
   as form validation (not tray chrome).
6. **ADR-0023 supersession** — Global chrome parts of ADR-0023 (banner + pill)
   are replaced by this tray. Sentinel removal, warn-never-block loopback,
   Fix→Settings, and offline suppression semantics remain.

## Consequences

- Header stays readable with Cast + Settings present.
- Multi-issue visibility without stacking banners.
- New test surface: aggregator unit tests + Compose icon/sheet tests.
- Search grey `SearchStatusBanner` may still appear in-screen for contextual
  search copy; tray carries the global connectivity/mode signal.
