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
2. **Cancel generation:** set `castConnectUserAborted` when user aborts; `onSessionStarted` / `onDeviceInfoChanged(remote=true)` ignore/reject late success and end the unwanted session.
3. **Picker UX:** explicit **Stop connecting** (keep sheet open) + sticky **This phone**; mid-connect device tap = abort-then-connect; Cancel/dismiss = hard abort + close.

## Consequences

- User can leave Connecting without waiting for timeout.
- Late SDK success cannot stick casting after abort.
- 10s timeout remains a safety net.
- Tests cover fresh-connect abort, late-start reject, Stop connecting Compose.
