# Spec: Tesla BT A2DP rebind (Path B)

## Problem

Warm A2DP connect while `player.isPlaying == true` logged `skip_playing` and
never called `play()`. Tesla often attaches A2DP while the session is still
“playing” on the phone route; Android Auto still works because it sends Path A
MediaSession PLAY.

## Contract

| Condition | Result token | Action |
|-----------|--------------|--------|
| paused / not playing | `autoplay` | `player.play()` |
| playing **and** A2DP media route active | `skip_playing` | no-op (F3) |
| playing **and** A2DP route **not** active | `autoplay_rebind` | `player.play()` to rebind |
| user Resume tap | `tap_play` | `player.play()` |

Route probe: `BtAudioRoute.isA2dpOutputActive` uses `AudioManager.isBluetoothA2dpOn`
(and API 31+ communication device). Device *presence* alone is ignored (warm
callback always implies presence).

Diagnostics extras: `isPlaying=… a2dpOut=…` on skip/rebind/autoplay crumbs.

## Edge cases

- Flaky ACL that never paused → rebind on next warm connect.
- Already on car speakers → skip (no pause/replay glitch).
- Casting → Path B cleared; no rebind.

## Tests

- Playing + `a2dpOut=true` → skip, no `play()`
- Playing + `a2dpOut=false` → `autoplay_rebind` + `play()`
- Paused → `autoplay`
