# Terminal track LAST_TRACK_STOP + CP idle resume — behavior report

## Symptom

Share dump (Pixel 8 Pro): prefetch `failed_terminal` → Exo `3003` → mid-queue
`REMOVE_TERMINAL` OK; last-index path logged `LAST_TRACK_STOP` then Continuous
Play `appendToContext +20` while IDLE — cabin silent; BT/play retried the corpse.

OfflineAware `IO_UNSPECIFIED` + `"network blocked"` classified UNKNOWN; sticky
ids still sought next instead of REMOVE.

## Root cause

1. Sticky excision lived only inside `applyRemoteMissingSkip` (REMOTE_MISSING).
2. Last index always stopped without REMOVE.
3. CP append did not resume after auto-skip IDLE.
4. Classifier never saw the OfflineAware message text.

## Fix

ADR-0164: early sticky REMOVE in `handleSkipNext`; sticky `LAST_TRACK_STOP` →
`applyRemoveTerminal`; `autoSkipLeftIdle` + CP seek/resume; `causeMessage` →
NETWORK for `network blocked`.

Cast remains seek/stop-only (ADR-0150).
