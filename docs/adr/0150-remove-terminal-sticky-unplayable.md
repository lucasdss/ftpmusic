# ADR 0150 — REMOVE_TERMINAL for sticky unplayable + Previous walk

Date: 2026-11-07  
Status: Accepted  
Related: ADR-0117, N11-2 / SourceErrorClassification, diagnostics dump 2.1.0 Pixel 8 Pro  
Source: Share diagnostics — track `0MPCWazH2VDIsLU5cxEeXZ` (`application/json` → `failed_ct` → Exo 3003 bounce)

## Context

Download correctly sticky-rejected non-audio CT (`failed_ct`). Playback still kept the
MediaItem. Auto-skip used seek-only `SKIP_MISSING`, so Previous re-landed on the corpse
and bounced (`circuit clear src=user-seek`).

## Decision

1. **Sticky unplayable** = Room `failed_ct` **or** live `IO_INVALID_HTTP_CONTENT_TYPE`
   **or** (`PARSING_CONTAINER_UNSUPPORTED` / `DECODING_FAILED` + span `NONE`).
   Bare first-hit 404 stays seek-only. `3003` + PLAYABLE span stays UNKNOWN (no remove).
2. On sticky + local Exo: `markFailedCt` + `PlaybackManager.removeFromQueue` by mediaId —
   log `REMOVE_TERMINAL`. **No** `seekToNext` after remove-current (Media3 advances).
3. Cast auto-skip v1 stays seek-only (no Dual Remove from error path).
4. Previous (`SKIP_PREV` + `MediaActionReceiver`) walks past `StickyUnplayableRegistry`
   / `failed_ct` neighbors; never removes on Previous.
5. Do **not** silently filter only inside `buildMediaItems` (Dual/Exo desync).

## Consequences

- Previous after a CT poison failure lands on the prior playable track.
- Prefetch `isTerminalFailed` (legacy sticky `failed`) remains prefetch-only; play-queue
  excision uses `failed_ct` / live sticky predicate.
- User Download (`isDownload=true`) can still `retry_user` after server fix.
