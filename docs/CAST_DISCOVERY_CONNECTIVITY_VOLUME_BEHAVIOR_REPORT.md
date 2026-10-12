# Cast Discovery / Connectivity / Volume — Behavior Report

Caveman. ADR-0123. Android Cast Framework + Media3. ADR-0003 Flutter historical only.

## Surface map

| Surface | Behavior |
|---------|----------|
| MediaRouter | `REQUEST_DISCOVERY` + `PERFORM_ACTIVE_SCAN` + `UNFILTERED_EVENTS` |
| Dialog open | Auto `refreshCastRoutes` |
| Force scan | Picker "Search again" → discovery callback re-toggle + refresh |
| Live check | `CastSession.isConnected` + `remoteMediaClient` |
| Heartbeat | `requestStatus()` while casting (UI-visible window) |
| Non-GMS ping | Out of scope (ICMP/mDNS/TCP fake reachability) |
| Volume seed | `seedFromSessionVolume` then **republish** PlaybackState |
| Volume drag | Gesture baseline from holder when UI stale at ~1.0 or ~0 |
| Spurious zero | Keep `isSpuriousZeroDisplay` + untrusted-0 ignore |

## Force scan

- User tap Search again (header + empty state).
- Spam guard: scanning flag ~5s.
- Implementation: removeCallback → addCallback(ACTIVE_SCAN) → refreshCastRoutes.
- Startup 2s×15 poll shortened (callbacks + force scan carry discovery).

## Connectivity honesty

- Pre-session: route add/remove owns list (removed = gone).
- Live session: periodic `remoteMediaClient.requestStatus()`; timeout → soft weak-link signal; hard end still `SessionManagerListener`.
- No independent non-GMS probe — Cast media path is GMS/MediaRouter.

## Volume blast-to-1 root

1. Local `PlaybackState.volume` often `1.0f`.
2. Connect seeds `PlayerHolder` only — UI stayed stale.
3. First drag `dragStartVolume = 1.0` → first `SET_VOLUME` near max.

Fix: republish after seed + `resolveCastDragBaseline` prefers holder when UI disagrees.

## Edge

- Seed null/negative → ignore (session not ready).
- Real device mute 0 → trusted when muted or pending≈0.
- Heartbeat only while casting + provider connected.
- Force scan no-op if MediaRouter callback not registered yet.
