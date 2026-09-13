# ADR 0006: CastPlayer Re-introduction with MediaItemConverter

**Date:** 2026-07-05  
**Status:** Accepted  
**Supersedes:** 0001-castplayer-removal

## Context

ADR 0001 removed CastPlayer because it cleared ExoPlayer's queue on Cast start and caused UI recomposition storms. ADR 0005's PlaybackStateController solved the flicker problem by single-sourcing all playback state. The remaining issues with manual Cast (play/pause blink, QuickSettings not syncing, dual MediaSession conflict with Cast SDK's `cast_rcn_media_session`) made the manual approach unsustainable.

## Decision

**Re-introduce CastPlayer**, wrapping ExoPlayer (local) + RemoteCastPlayer (Cast SDK), with a custom SubsonicMediaItemConverter that resolves proxy URLs to direct Subsonic URLs for Cast. The key enablers:

1. **PlaybackStateController** (ADR 0005) prevents UI storms — state writes are single-sourced, so CastPlayer's `onMediaItemTransition` callbacks no longer cause flicker
2. **MediaItemConverter** preserves the canonical local queue — ExoPlayer items always hold proxy URLs; the converter extracts remote URLs on-the-fly when CastPlayer sends items to the receiver. No queue clearing needed
3. **MediaSession auto-sync** — CastPlayer implements `Player`, so the MediaSession (notification, QuickSettings, lock screen) reflects actual Cast state without manual `notifyChanged()` calls
4. **No volume-based muting** — CastPlayer pauses ExoPlayer during Cast; position comes from CastPlayer's delegated `currentPosition`

## Consequences

**Positive:**
- Play/pause button no longer blinks (CastPlayer fires accurate `onIsPlayingChanged` for both local and remote)
- QuickSettings and lock screen controls work during Cast (MediaSession reflects CastPlayer state automatically)
- ~300 LOC of manual Cast lifecycle code removed (SessionManagerListener, RemoteMediaClient polling, wifi locks)
- Single `Player` interface for both local and remote — control callbacks simplified

**Negative:**
- Lost direct access to RemoteMediaClient — advanced Cast SDK features require going through CastPlayer
- CastPlayer.Builder API in Media3 1.9.4 doesn't directly expose MediaItemConverter — must pass via RemoteCastPlayer.Builder
- Position tracking during Cast relies on CastPlayer's internal delegation (not directly verifiable in unit tests)

## Alternatives Considered

1. **Keep manual Cast + fix QuickSettings/blink** — would require calling `notifyChanged()` from the Cast progress listener and sending `CastStateUpdate` on play/pause. Rejected because Android recommends CastPlayer for MediaSession sync.
2. **CastPlayer without MediaItemConverter** — could use queue URL swapping on connect/disconnect. Rejected because it re-introduces the queue manipulation that ADR 0001 warned about.
3. **Keep manual Cast + dismiss app notification during Cast** — let Cast SDK's `cast_rcn_media_session` handle everything. Rejected because behavior varies across OEMs and Android versions.
