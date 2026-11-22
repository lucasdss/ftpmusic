# ADR-0164: Warm BT force-rebind + sticky last-track + CP idle resume

Date: 2026-11-22  
Status: Accepted  
Related: ADR-0150, ADR-0154, ADR-0161, OfflineAwareDataSource, Continuous Play (ADR-0053)

## Context

Field dumps showed three residual gaps after ADR-0161 / ADR-0150:

1. Warm `AudioDeviceCallback` A2DP often reports `isBluetoothA2dpOn` before Exo
   rebinds → cold-style `skip_playing` still silenced Tesla.
2. Sticky/poison tracks (`failed_ct`, live 3003 + span NONE) mid-queue already
   used `REMOVE_TERMINAL`, but **last index** used bare `LAST_TRACK_STOP` and
   left the corpse seated. NETWORK/UNKNOWN errors on already-sticky ids never
   entered the REMOTE_MISSING sticky branch.
3. Continuous Play often appended after that stop while IDLE — no seek/play,
   so the cabin stayed silent until a manual gesture.

Also: OfflineAware throws `IOException("… network blocked")` under
`ERROR_CODE_IO_UNSPECIFIED`; classifiers only saw cause **class name**
(`IOException`) → UNKNOWN.

## Decision

1. Plumb `MediaServiceStartRequest.btAutoplaySource` (`warm` / `cold`) via
   Intent extra + static flag from `BtConnectionReceiver` / `BtAutoplayStarter`.
2. Warm + `!userInitiated` + `isPlaying` → always `autoplay_rebind` (ignore
   `a2dpOut`). Cold keeps ADR-0161 `skip_playing` only when `isPlaying && a2dpOut`.
3. `LAST_TRACK_STOP`: IO-sample span + `hasFailedCt`; sticky + !casting →
   `applyRemoveTerminal`; else stop and set `autoSkipLeftIdle`.
4. Early sticky branch in `handleSkipNext` (before kind switch): sticky +
   !casting → `REMOVE_TERMINAL`. Cast stays seek/stop-only (ADR-0150).
5. `classifySourceError(..., causeMessage)`: `IO_UNSPECIFIED` + message contains
   `network blocked` → `NETWORK`.
6. On CP `appendToContext(asAutoplay=true)` while `autoSkipLeftIdle` and player
   not casting / not user-paused → seek to first new index + `resumeAfterSkip`.
   Clear flag on user pause, verified play, Cast, or after resume.

## Consequences

- Warm Path B no longer false-skips when the OS already claims A2DP.
- Poison last track is excised (or CP resumes past a non-sticky last stop).
- OfflineAware blocks classify as NETWORK; sticky corpses still REMOVE on
  live NETWORK/UNKNOWN when `failed_ct` is already set.
