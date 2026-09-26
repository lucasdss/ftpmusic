# Outside-LAN Play Hang — Behavior Report

Date: 2026-09-26
Status: Analyzed + fix shipped (ADR 0043)
Device: Pixel 8 Pro (`38091FDJG00DET`), app `1.0.0` release
Capture: `docs/logs/phone-logcat-full.txt` (23 258 lines, no wipe)

## User story

Outside local network on **5G**. UI showed “no network”. Play hung / blank ↔ broken;
Next failed; force-close loop. Cannot reproduce on demand.

## Log verdict

| Hypothesis | Evidence | Result |
|---|---|---|
| Simulate Offline ON | No `ftpmusic-offline` in buffer (release strips `Log`); not required for hang | Unlikely primary |
| Server unreachable on cellular | User on 5G outside LAN; AppHeader Offline = `ReachabilityStateHolder` | **Matches story** |
| True no OS network | 5G in use | Rejected |
| Deep sleep / Doze kill FGS | Not seen as Doze kill; see force-close | Partial |
| Force-close | `Killing … ftpmusic.app … remove task` @ 13:28:01 | **Confirmed** |
| App HTTP hang | Release build: `ftpmusic-*` tags absent (R8 `assumenosideeffects`) | No app tag proof |
| localhost ConnectException | pid 22559 = **Google Quick Search**, not ftpmusic | Noise |

## Timeline (phone clock)

- **13:26:27–13:27:50** — MediaSession for `ftpmusic` pushed top repeatedly (play/skip churn).
- **13:28:01** — `ActivityManager: Killing … remove task` (user swiped task / force stop from recents).
- **13:28:03** — Cold start `Start proc` + FGS `INITIALIZE_PLAYBACK` + new MediaSession.
- **14:23** — Unrelated Google localhost:443 refused.

## Root cause (locked)

Phone **had** cellular. Home Navidrome **unreachable** off-LAN → reachability false → UI Offline (user: “no network”). Play/Next still opened HTTP upstream (only Simulate Offline fail-fast) → long socket wait / BUFFERING / error skip storm → UI feel broken → user force-closed.

Prior fixes (keepalive badge, gapless queue edit) do **not** cover this.

## Fix (ADR 0043)

1. Header badge: **Server unreachable** (not Offline).
2. `OfflineAwareHttpDataSource` fail-fast when `!isReachable` (same as software offline).
3. Existing `onPlayerError` auto-skip then advances past uncached tracks without multi-second hangs.
