# ADR-0001: Three-Tier Cast URL Resolution

## Status
Accepted (2026-06-15)

## Context
Google Cast operates on a hand-off protocol: the mobile device transfers a URL
to the receiver hardware, which then connects and streams independently. This
creates a conflict with Subsonic's salted-hash authentication, where stream URLs
contain per-request tokens that may be stale by the time the receiver fetches
them.

Additionally, cached tracks exist only on the phone's local filesystem, which
Cast hardware cannot access. Offline scenarios (rural driving, RV Wi-Fi) mean
the remote Subsonic server may be unreachable when Cast is initiated.

Three strategies were considered:
1. **Remote URL only** — regenerate a fresh auth token just before Cast. Simple,
   zero battery impact, but fails when the server is unreachable or token has
   expired on the receiver side.
2. **Local proxy only** — always stream from phone to receiver over LAN.
   Universal compatibility but high battery drain (~10-15%/hour).
3. **Three-tier resolution** — try remote first, fall back to local proxy.

## Decision
Implement a **three-tier URL resolution strategy** for Cast:

| Tier | Strategy | When used |
|------|----------|-----------|
| 1. Remote URL (stream) | Fresh auth token, receiver fetches from Subsonic server | Default. Server reachable, track not cached. |
| 2. Remote URL (cached) | Same as Tier 1, even though track is locally cached | Track cached, but server is online. Saves phone battery. |
| 3. Local Cast proxy | Phone runs HTTP proxy, receiver streams from phone over LAN | Server unreachable OR user-enabled proxy setting AND track is cached |

A user-facing toggle **"Use local proxy (saves data, uses more battery)"** in
Cache & Storage settings allows the user to opt into Tier 3. Without it, Tier 3
only activates as a fallback when Tier 1 fails.

## Consequences

**Positive:**
- Zero battery impact for the common case (Tier 1/2)
- Works offline with cached tracks via local proxy when needed
- Universal compatibility across all Subsonic API servers
- Reuses the same local HTTP proxy pattern mandated for iOS caching
  (see Architecting.md §"iOS Implementation: The Local Proxy Server Pattern")

**Negative:**
- Three code paths to maintain for Cast URL resolution
- Adds a Cast-specific setting to the Cache & Storage UI
- Local proxy implementation is ~2 days of development
- Proxy consumes battery when active — must be clearly communicated to user
