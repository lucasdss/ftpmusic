# ADR-0124: Cast Connect User Abort (Hard Cancel)

**Date:** 2026-10-12
**Status:** Accepted
**Supersedes:** ADR-0026 Cancel wording (flags/timeout only — not SDK abort)
**Relates:** ADR-0026, ADR-0028, ADR-0008

## Context

Users stuck on `"Connecting to …"` until the 10s timeout. Market apps (Spotify recovery Stop, YouTube This phone, Apple iPhone destination, Symfonium Local device) always allow immediate abort to phone/local.

`cancelCastConnect()` cleared in-flight flags and the timeout, but after `route.select()` the Cast SDK handshake could still complete (`onSessionStarted` / `remote=true`), surprising the user who thought they cancelled.

## Decision

1. **Hard abort:** `cancelCastConnect()` always tears down an in-flight attempt via `endCurrentCastSession()` (end SDK session + select default MediaRouter route), not only on device-switch cancel.
2. **Cancel generation:** set `castConnectUserAborted` when user aborts; sticky until a **new** `connectToCastDevice` or a successful clear. `onSessionEnded` must **not** wipe the abort flag (`clearCastConnectAttempt(clearUserAbort=false)`).
3. **Target gate:** `onSessionStarted` / `remote=true` also reject when `session.deviceId != castConnectTargetDeviceId` (supersede A→B late-A). Wrong-device teardown sets `castSwitchInProgress` so B’s in-flight connect is not cleared.
4. **Picker UX:** explicit **Stop connecting** (keep sheet open) + sticky **This phone**; mid-connect device tap = abort-then-connect; Cancel/dismiss = hard abort + close.

## Consequences

- User can leave Connecting without waiting for timeout.
- Late SDK success cannot stick casting after abort (including after session-ended teardown).
- Late handshake for a superseded device cannot steal the new connect.
- 10s timeout remains a safety net.
- Tests cover fresh-connect abort, late-start reject, abort-survives-ended, supersede late-A, Stop connecting Compose.
