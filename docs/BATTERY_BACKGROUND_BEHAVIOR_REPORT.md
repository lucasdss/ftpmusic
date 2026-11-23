# Battery / background behavior report

**Date:** 2026-11-23  
**ADR:** [0165](adr/0165-battery-background-best-practices.md)  
**Spec:** [specs/battery-background-best-practices-spec.md](../specs/battery-background-best-practices-spec.md)

## Changes

| Area | Before | After |
|------|--------|-------|
| Track-drain WifiLock | Entire uncapped drain | Per network batch only |
| Cast discovery | Continuous ACTIVE_SCAN | Passive default; ACTIVE_SCAN on picker / 5s force-scan |
| Cast button | Hidden until devices | Always visible |
| Reachability | 15s/60s forever | 15m keepalive in background; NetworkCallback kept |
| Playlist flush | 15s when empty | 5m idle when empty |
| Download idle | 1s spin | Wake signal + 60s max wait |
| WM periodic + enrich | Network only | + `requiresBatteryNotLow` |

## Non-changes

- MediaPlayback FGS while paused (ADR-0019 / 0032)
- Soft battery CTA (no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- SyncHealthGuard in-process DELTA on foreground (bypasses WM batteryNotLow)
- Age Signals: legal hold only (no SDK)
