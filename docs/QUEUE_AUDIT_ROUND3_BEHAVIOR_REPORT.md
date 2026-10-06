# Queue Audit Round 3 — Behavior Report

Follow-up after Cast CP UX honesty (`2ab2c00`). Remediation PR.

## Verified (previous ships)

Dual bands, Clear=PRIORITY, Save local-first, Share flush+public gate, sleep/repeat,
CP `StateFlow`, Cast Autoplay switch disabled + caption. `ContinuousPlayGate` unchanged.

## Shipped this round

| Item | Change |
|------|--------|
| Share sync | Do not `consumeIdRemap` when flush incomplete |
| Cast notice | Show flatten banner whenever `isCasting`; tag `queue_cast_flatten_notice` |
| QueueScreen harness | Autoplay switch disabled while casting + caption |
| PlaybackViewModel | Remove unused `SubsonicApi` DI; Share/Save toasts → strings |
| PlayerBar | Remove dead `onAddToPlaylist` / `onSongInfo` / `onEqualizer` stubs |
| Docs | ADR-0075 consequences updated (sleep/repeat → 0076) |
| Tests | Remap regression, Cast notice/clear autoplay, selection hides repeat, history VM |

## Still open (product)

Cast CP enable (Step 2), guest `createShare`, Dual/Cast flatten, history live refresh,
Overwrite prune, Go-to-album, YT dismiss-session.

