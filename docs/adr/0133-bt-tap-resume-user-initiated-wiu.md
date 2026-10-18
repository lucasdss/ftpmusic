# ADR 0133 — BT Resume Notif Tap = User-Initiated Play (A17 WIU)

Date: 2026-10-13
Status: Accepted
Superseded in part by: ADR-0148 (happy-path WIU notif removed; tap plumbing kept for FGS-deny/null-mac)
Related: ADR-0088 (A17 WIU seat), ADR-0101 (offline seat), ADR-0117 (no audible flash)
Field: `docs/BT_TAP_RESUME_WIU_LOOP_BEHAVIOR_REPORT.md`

## Context

ADR-0088 correctly withheld background `play()` on API ≥37 after A2DP seat and
posted a high-pri Resume notification, intending the tap to grant WIU and start
playback. Implementation reused `ACTION_BT_AUTOPLAY` without a user-gesture
signal, so `applyBtPlayAfterSeat` re-entered the WIU branch on every tap
(`pause` + re-post). Field logs (Pixel 8 Pro sdk=37): `Started` → `wiu`, then
manual in-app play — never `play()` from the notif.

## Decision

1. Resume PendingIntent sets `EXTRA_USER_INITIATED_RESUME=true`.
2. `MediaService.onStartCommand` copies that extra into
   `MediaServiceStartRequest.userInitiatedResume`.
3. Background `BtAutoplayStarter.startAutoplay` forces `userInitiatedResume=false`
   so stale tap state cannot leak into A2DP wakes.
4. `PlaybackResumptionMapper.shouldPlayAfterBtSeat(sdk, userInitiated)` =
   `userInitiated || shouldBackgroundAutoplay(sdk)`.
5. On play path: cancel Resume notif ID 1002; record `tap_play` or `autoplay`
   in `BtResumeEvents`. On WIU path: record `wiu`.

## Consequences

- API ≥37: A2DP connect still seats + WIU notif (no silent autoplay).
- Notif tap / action button calls `play()` and dismisses Resume notif.
- Diagnostics header now surfaces `wiu` / `tap_play` alongside `Started`.
- Media-key / HU Play paths unchanged (MediaSession, not this flag).
- `selected_null_mac` Resume notif (ADR-0101) also plays on tap — user gesture,
  not unverified autoplay.
- Tap while casting clears flags silently (Cast owns audio); pre-existing.
- `startAutoplay` preserves an in-flight tap (`btAutoplayRequested &&
  userInitiatedResume`) so reconnect storms do not clobber pending play.
- Pre-update Resume PendingIntents lack the extra → one WIU re-entry, then
  second tap plays after upgrade.
