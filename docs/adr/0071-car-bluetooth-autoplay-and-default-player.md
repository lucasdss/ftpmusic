# ADR 0071 — Car Bluetooth Autoplay + Default Player

Date: 2026-10-04
Status: Superseded by ADR-0072
Related: ADR-0019 (FGS lifecycle), ADR-0032 (MediaService recovery),
ADR-0072, `docs/BT_RESUME_SYSTEM_MEDIA_BEHAVIOR_REPORT.md`

## Context

FTP Music already exposes Media3 `MediaLibrarySession`, lock/notif/QS controls,
and queue restore after process death — but restore **always pauses**. No Bluetooth
connect path, no default-music role claim, no car-specific resume. Users want the
app to wake from deep sleep when the phone joins a car BT audio sink and resume
the last queue, and to integrate cleanly with Android system media controls.

## Decision

1. **Opt-in + allowlist only.** Settings toggle + bonded-device MAC multi-select.
   Never autoplay on arbitrary A2DP (headphones).
2. **`ACTION_CAR_BT_AUTOPLAY`** starts `MediaService` via `startForegroundService`
   (same promote contract as `ACTION_PLAYBACK` / ADR-0019). Restore path skips
   pause when car-BT flag set; already-loaded paused queue → `play()`.
3. **FGS fallback.** On `ForegroundServiceStartNotAllowedException`, post a
   high-priority notification whose PendingIntent retries `ACTION_CAR_BT_AUTOPLAY`.
4. **Default player claim.** `MediaLibrarySession.setSessionActivity(MainActivity)`
   + Settings CTA deep-link to `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` (no
   `RoleManager.ROLE_MUSIC` on current SDKs — role does not exist).
5. **Skip Android Auto browse tree** in this slice (`onGetLibraryRoot` / children
   remain unimplemented).
6. **Casting wins.** If `PlayerHolder.isCasting`, car resume is a no-op.

## Consequences

- New perms: `BLUETOOTH` (legacy) + `BLUETOOTH_CONNECT` (runtime API 31+).
- Manifest receiver for A2DP connection + ACL connected.
- Continuous Play (ADR-0053) naming/semantics unchanged.
- Coverage gate ≥80% on new policy/receiver helpers/Settings VM paths.
