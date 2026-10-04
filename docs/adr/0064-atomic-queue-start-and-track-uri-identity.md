# ADR 0064 — Atomic Queue Start and Track/URI Identity

Date: 2026-10-04  
Status: Accepted  
Related: ADR-0007, ADR-0004, ADR-0032, ADR-0002, ADR-0060

## Context

User report (v1.4.2): Now Playing showed **KATSEYE — ANIMAL** while audio was
**IVE — TAKE IT**. Diagnostics showed playlist rebuild (`reason=3`) at `idx=8`
then process restart (`splash`→`home`).

Two independent seams allowed UI metadata and decoded audio to diverge:

1. `QueueManager.playAll` called `setMediaItems(items)` (starts at 0), then
   `seekToDefaultPosition(startIndex)`, then `play()`. Restore used that path
   and sought again — metadata could settle on the saved index while the
   decoder period lagged on an earlier item.
2. `buildMediaItems` zipped `Track` rows with stream URLs without checking that
   URI query `id` equals `track.id`. UI reads metadata/`mediaId`; SimpleCache
   keys off URI `id` (`streamCacheKey`). A torn zip yields ANIMAL chrome with
   TAKE IT bytes.

## Decision

1. **Atomic queue start.** `playAll(items, startIndex, positionMs)` uses
   `Player.setMediaItems(items, startIndex, positionMs)` only — no post-set
   seek for the start index. `restoreQueue` accepts `positionMs` and passes it
   through. Cold-start restore does not call a second `seekTo`.
2. **Track/URI identity.** `alignStreamUrlToTrackId` rewrites mismatched
   `id=` query values to `track.id` before MediaItem build. Transition
   breadcrumbs log `identity mismatch mediaId=… uriId=…` (ids only; ADR-0060).
3. URLs without an `id=` query are left unchanged (radio / non-Subsonic).

## Consequences

- Restore and album play share one atomic Media3 entry path with Cast/local
  sync helpers that already used the 3-arg overload.
- Torn persisted URL rows self-heal on next build; wrong cache spans under the
  old URI id are not reused for the rewritten id (may re-buffer once).
- Existing unit tests that expected 1-arg `setMediaItems` updated to 3-arg.
- Behavior report: `docs/PLAYER_TRACK_IDENTITY_DESYNC_BEHAVIOR_REPORT.md`.
