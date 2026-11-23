# ADR-0165: Battery / background best practices

**Date:** 2026-11-23  
**Status:** Accepted  
**Relates:** ADR-0019, ADR-0123, ADR-0119, ADR-0139, ADR-0013

## Context

Audit found WifiLock held for entire uncapped track drain, Cast
`PERFORM_ACTIVE_SCAN` for whole NavHost lifetime, process-lifetime 15s/60s
reachability pings, playlist flush ticking every 15s when empty, download idle
1s spin, and WorkManager sync without `batteryNotLow`.

## Decision

1. **WifiLock** — acquire/release per network batch in `syncAlbumTracks` only.
2. **Cast** — default passive discovery; ACTIVE_SCAN while picker open or ~5s
   force-scan; Cast button always visible so picker can open with zero devices.
3. **Reachability** — `setAppForeground(false)` slows keepalive to 15m; keep
   NetworkCallback registered (never `stop()` on background).
4. **Playlist flush** — idle empty queue uses 5m interval; failure ladder
   unchanged; `flushNow` independent.
5. **Download** — conflated wake signal on pending enqueue; 60s max wait when
   empty; constraint-blocked spins keep 1s delay.
6. **WM** — periodic sync + enrich require `batteryNotLow`; in-process
   SyncHealthGuard / Resync remain ungated.

## Consequences

- Lower idle radio/Wi‑Fi drain.
- Cast discovery may be slower until picker/force-scan; always-visible button
  mitigates cold-start discoverability.
- Background reachability still tracks OS network; Subsonic ping is infrequent.
- Low-battery users who open the app still sync via SyncHealthGuard.

## Age Signals

Out of scope for this ADR. Play Age Signals remains a **legal hold** (no SDK)
until counsel decides — see `docs/PLAY_POLICY_2026_10_29_COMPLIANCE.md`.
