# Queue Audit Follow-up — Behavior Report

Caveman. Remediation after audit of ADR-0074/75/76 ships.

## Prior ships (verified OK)

| Area | Verdict |
|------|---------|
| Clear = `clearPriority` only | OK (NavHost wired) |
| Dual bands + Autoplay toggle | OK |
| Save local-first / Share publish | OK after sync gate |
| Sleep/repeat strip | OK |
| History / batch | OK after selection key fix |

## Fixes this cycle

| P0 | Fix |
|----|-----|
| Clear during selection | Hide `queue_clear_priority` + `queue_clear_autoplay` in selection mode |
| Share empty playlist | `createPlaylistWithTracksSynced` returns id only when `synced` |
| Stale selection indices | Select by `queueRowKey` (entryId); prune on upcoming identity change |

| P1 | Fix |
|----|-----|
| CP not reactive | `PlaybackManager.continuousPlayEnabledFlow` → NavHost collect |
| Clear copy | "Clear Next in Queue" |
| Harness drift | `QueueScreen` `@Deprecated` (tests only; SoT = PlayerBar) |
| Stale market follow-up | ADR-0076 marked shipped |

## Intentional (document only)

- Share chooser still opens if `setPlaylistPublic` fails
- History refresh on sheet open (+ after play-next-from-history); not live while open
- YT dismiss-session / Dual-Cast flatten / `createShare` — OOS

## Coverage

- Compose: selection hides Clear + sleep; Cancel restores Clear
- Repo: synced=false → null server id
- Gate: assembleDebug + targeted tests
