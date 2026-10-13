# BT Tap-Resume WIU Loop — Behavior Report

Date: 2026-10-13
Device: Pixel 8 Pro sdk=37 / app 1.12.0 (19)
Related: ADR-0088, ADR-0133

## Symptom

Bluetooth connect seats queue + posts Resume notif (`result=wiu`), but tapping
Resume does not start audio.

## Log map (field)

```
warm_a2dp macPresent=true
policy=allow mode=ANY sdk=37
fgsBlocked=false result=Started sdk=37
result=wiu sdk=37          ← seat + pause + Resume notif (correct on A17)
```

`btResumeEvents` showed only `warm_a2dp|allow|Started` — WIU lived in Detailed
Log only (pre-fix). No `tap_play`, no `notif_disabled`.

After WIU, user opened app + started mix manually = expected recovery path that
should have been notif tap.

## Root cause

Resume PendingIntent reused `ACTION_BT_AUTOPLAY` with no user-gesture flag.
`applyBtPlayAfterSeat` gated solely on `shouldBackgroundAutoplay(sdk)` → false
on API ≥37 → `pause()` + re-post WIU. Tap indistinguishable from background
A2DP wake → infinite notif loop, never `play()`.

Contradicts ADR-0088 intent: notif tap = user-initiated FGS = WIU = may play.

## Fix (ADR-0133)

1. `EXTRA_USER_INITIATED_RESUME` on Resume PendingIntent.
2. `MediaServiceStartRequest.userInitiatedResume` set in `onStartCommand`.
3. `shouldPlayAfterBtSeat(sdk, userInitiated)` → play + cancel notif on tap.
4. Background A2DP still WIU-only on API ≥37.
5. `BtResumeEvents` records `wiu` / `tap_play` / `autoplay`.

## Expected post-fix crumbs

```
… result=Started → result=wiu          (connect)
… tap → result=tap_play sdk=37         (user gesture)
```

## Non-goals

Silent A2DP autoplay alone on API ≥37 remains unsupported (AudioHardening).
