# Issue Status Tray — Behavior Report

Date: 2026-10-11
Scope: 1C + 2A (ADR-0120)
Related: ADR-0023, ADR-0041, ADR-0043, ADR-0051, ADR-0095

## Problem

Yellow `ServerErrorBanner` + AppHeader text pill "Server unreachable" duplicate
same reachability signal. Header chrome clogs next to Settings. No multi-issue
stack; Search / NP / playlist conflict each invent own surface.

## Market compare

| App | Pattern | Multi-issue |
|---|---|---|
| Spotify | Thin offline mode strip | Poor |
| Slack | Bottom "Trying to connect…" | Poor |
| Gmail | Bottom bar + queued work | Poor |
| Maps | Mid-screen banner | Poor |
| YT Music | Per-item offline icons | Local only |

Consensus: one global connectivity surface. Offline = mode, not stacked error.
Chosen: compact AppHeader icon + tap ModalBottomSheet.

## Signal matrix

| Signal | Source | Tray row | Auto-clear |
|---|---|---|---|
| No OS network | `NetworkAvailabilityHolder` | WifiOff | Yes (debounced) |
| Server unreachable | `ReachabilityStateHolder` (OS net OK, not Simulate Offline) | CloudOff | Yes (debounced) |
| Simulate Offline | `OfflineModeManager` | Mode (muted) | Toggle off |
| Loopback config | `DynamicBaseUrl.isLoopback()` | Warning → Settings | URL change |
| Cellular local-only | `LocalOnlyPolicy` / `NetworkPolicyState` | Info | Policy/net change |
| Playback error | `PlayerHolder.lastPlaybackError` | ErrorOutline + Dismiss | Sticky expiry / dismiss |
| Playlist conflict | `PlaylistDao.observeConflicted` | SyncProblem → open playlist | Sync resolve |

## Icon matrix (header, left of Settings)

| Active set | Icon | Color |
|---|---|---|
| Only no OS net | WifiOff | OfflineYellow |
| Only unreachable | CloudOff | OfflineYellow |
| Only Simulate Offline | CloudOff muted | NavUnselected |
| Only config | Warning | OfflineYellow |
| Only playback | ErrorOutline | OfflineYellow |
| Only conflict(s) | SyncProblem | Orange |
| 2+ kinds | Warning + count badge | OfflineYellow |
| None | Hidden | — |

## Chrome changes (2A)

- Remove `ServerErrorBanner` from Home/Library.
- Remove AppHeader text pill.
- Remove NP sticky yellow banner; dismiss lives in sheet.
- Remove playlist detail Sync Conflict banner; open playlist from sheet.
- Keep Settings inline loopback/cleartext field warnings.
- When AppHeader hidden (NP / detail / settings), float `IssueStatusIcon` top-end
  so tray stays reachable.

## Sheet actions

- Config / unreachable → Settings
- No OS net / cellular local-only → informational
- Simulate Offline → Settings
- Playback error → Dismiss
- Playlist conflict → `playlist/{id}`

## Edge cases

- Debounce OS net / reachability show+clear ~2.5s (no flicker).
- Offline mode suppresses unreachable row (ADR-0023 offline suppression).
- Sheet open while issue clears → list updates in place.
- A11y: icon `contentDescription` = primary + count; sheet rows have text.
- Hit target ≥ minTouchTarget next to Settings.

## Tests

- `AppIssueAggregatorTest`, `IssueStatusIconComposeTest`, `AppHeaderComposeTest` icon.
- DAO Flow conflicted playlists.
- Migrated from `ServerErrorBannerComposeTest`.
