# ADR 0101 — BT A17 Silent Wake + Off-LAN Cache Seat

Date: 2026-10-09
Status: Accepted
Related: ADR-0088 (A17 WIU), ADR-0087 (resumption), ADR-0072 (A2DP resume), ADR-0043 (unreachable)
Toolchain: compileSdk/targetSdk 36; Android 17 via SDK_INT ≥ 37 guards

## Context

Field report on Android 17 with Bluetooth resume **Selected + allowlist filled**:

1. **Silent no-media** — A2DP connect sometimes yields empty HU and **no** phone
   Resume notification. Root gaps vs ADR-0088:
   - `SELECTED` + unreadable MAC (`BLUETOOTH_CONNECT` deny → `safeAddress` null)
     hard-skips in `BtResumePolicy` before `BtAutoplayStarter` runs → total silence.
   - Empty Room seat clears `btAutoplayRequested` with no user signal.
   - `POST_NOTIFICATIONS` denied makes `notify()` a silent no-op even when posted.
2. **Play then die off LAN** — BT seat restores last index; if that track is not
   fully cached and the Subsonic server is unreachable, `OfflineAwareHttpDataSource`
   fails fast → `onPlayerError` → `STOP_NO_CACHED` / skip cascade. User must force-skip.

## Decision

1. **Never silent on enabled A2DP connect when SELECTED cannot read MAC.**
   Post the same high-pri Resume notification as the FGS-fallback path
   (`BtAutoplayStarter.postResumeNotification`). Do **not** autoplay without a
   verified allowlist match. Log `ftpmusic-bt` reason=`selected_null_mac`.
2. **Empty Room on BT seat** — post Resume/empty-queue notification instead of
   clearing the flag quietly.
3. **Notif permission denied** — log loud when `areNotificationsEnabled` is false;
   still seat MediaSession when FGS succeeds so HU Play → `onPlaybackResumption`.
4. **Off-LAN BT seat** — before play/WIU, if offline / no OS net / server
   unreachable / cellular LOCAL_ONLY (ADR-0106), seek to first fully-cached
   index at/after current then wrap (`resolveOfflineStartIndex`). If none, do
   not start doomed playback; post offline hint notification.
5. Keep ADR-0088 A17 gate: no silent-only background `play()` on API ≥ 37.

## Consequences

- SELECTED users without CONNECT still get a tappable Resume prompt on A2DP.
- Off-LAN car trips prefer downloaded tracks without waiting for the first error.
- Partial cache still counts as miss (`isStoredInCache` requires full span from 0).
- `targetSdk` remains 36.
