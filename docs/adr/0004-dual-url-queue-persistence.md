# Dual-URL Queue Persistence

**Status**: accepted

The queue URL swapping approach (`swapQueueUrls`) was fundamentally fragile — rebuilding MediaItems by reading from one player and writing back, with tight coupling to CastPlayer lifecycle timing. After extensive debugging, the approach was replaced with a **router-style architecture**: each track stores both URLs, and `isCasting` acts as a routing flag selecting which URL to use.

## Architecture

```
┌─────────────┐     isCasting?     ┌──────────────┐
│  TrackInfo  │ ──── true ────────▶ │ castUrl       │ ──▶ CastPlayer
│ localUrl    │                    │              │
│ castUrl     │ ──── false ───────▶ │ localUrl      │ ──▶ ExoPlayer
└─────────────┘                    └──────────────┘

Same queue, same UI, same state. Only the URL changes.
```

## Decision

- **TrackInfo** stores `url`, `localUrl`, `castUrl` — computed once in `buildMediaItems`
- **`isCasting`** is the routing flag — no URL computation at runtime
- **URL swaps** are suppressed from UI updates (`isUrlSwapInProgress` flag prevents `onMediaItemTransition` from resetting position/metadata)
- **Cast state persisted** via SharedPreferences (`cast_is_casting`, `cast_device_name`) with stale-check on startup — never auto-reconnects

## Consequences

- No runtime `proxyUrl` computation during connect/disconnect
- UI is identical for local and Cast playback — same PlayerBar, same queue, same art
- Queue survives app restart with both URLs pre-computed (pipe-separated in DB `url` column)
- Cast state doesn't persist across app restart as active — always starts local
