# Queue Audit Round 2 — Behavior Report

Caveman. After `bffa4a8` remediation audit.

## Shipped this cycle

| Fix | Behavior |
|-----|----------|
| Share public gate | Chooser only when `setPlaylistPublic` succeeds; else toast, no share sheet |

## Prior ships (re-verified)

ADR-0074/75/76 + audit remediation (selection Clear hide, entryId keys, sync-gated server id, CP Flow) still OK.

## Follow-up: Cast Continuous Play

**Not this PR.** Market:

- Apple AirPlay/HomePod — AutoPlay continues while remote.
- Spotify Chromecast — autoplay after playlist end weak/inconsistent.
- FTPMusic — `ContinuousPlayGate(isCasting=true)` → false (ADR-0031).

**Next Cast CP PR (when scheduled):**

1. UX honesty — Autoplay control unavailable/disabled while casting (toggle must not imply CP will fire).
2. Optional enable — Dual append + Cast mutation; no Cast timeline callback triggers; ADR update.

Until then: keep Cast CP off.

## Other backlog (unchanged)

`createShare`; history live refresh; QueueScreen delete; Overwrite prune; sheet Go-to-album.

## Coverage

`FavoriteTest`: public fail → no `startActivity`; success → chooser; sync null → no public call.
