# Queue Audit Round 2 — Behavior Report

Caveman. After `bffa4a8` remediation audit.

## Shipped this cycle

| Fix | Behavior |
|-----|----------|
| Share public gate | Chooser only when `setPlaylistPublic` succeeds; else toast, no share sheet |

## Prior ships (re-verified)

ADR-0074/75/76 + audit remediation (selection Clear hide, entryId keys, sync-gated server id, CP Flow) still OK.

## Follow-up: Cast Continuous Play

**Step 1 (shipped):** UX honesty — Autoplay Switch disabled while casting +
“Unavailable while casting” caption (`queue_autoplay_cast_unavailable`).
`ContinuousPlayGate` still `isCasting → false` (ADR-0031). Preference value
preserved for after Cast ends.

**Step 2 (still follow-up):** Optional enable — Dual append + Cast mutation;
no Cast timeline callback triggers; ADR update.

Market: Apple AirPlay keeps AutoPlay; Spotify Chromecast autoplay is weak —
keep CP off on Cast until step 2.

## Other backlog (unchanged)

`createShare`; history live refresh; QueueScreen delete; Overwrite prune; sheet Go-to-album.

## Coverage

`FavoriteTest`: public fail → no `startActivity`; success → chooser; sync null → no public call.
