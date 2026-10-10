# ADR 0110 — Audio cache payload validation

Date: 2026-10-10  
Status: Accepted  
Related: ADR-0002 (cache), ADR-0022 (download safety), ADR-0047 (scrobble honesty), ADR-0016 (cast mirror)

## Context

Device diagnostics (v1.8.0) showed mix auto-cache writing ~182-byte Subsonic
error documents as cached audio. ExoPlayer then failed with Source error;
auto-skip deleted the span asynchronously and users could reselect the poison
track immediately. Cover art already had magic/error-body validation
(`CoverArtFiles`); audio did not.

Post-1.8.0 review: offline/BT jump and `CacheService.initialize` still trusted
`isStoredInCache` (span exists). Strict magic heal also risked wiping download
pins for exotic codecs (WMA/APE) without matched magic.

Separate log issues: scrobble duration extras are milliseconds but were read as
seconds; Cast reconnect ran for all session-end codes including 2055; connect
timeout raced with `onSessionStarting`.

## Decision

1. Add `AudioCacheValidation` with `PayloadVerdict` (`PLAYABLE` / `POISON` /
   `UNKNOWN`), min size 4 KiB, Subsonic JSON/XML sniff, audio magic (ID3, MPEG,
   fLaC, OggS, RIFF/WAVE, ftyp, AIFF/AIFC, WavPack). Gate
   `CacheService.writeCachedTrackFromFile` on `PLAYABLE`. Soft Content-Type gate
   in `DownloadManager.downloadItem` allows `audio/*`, octet-stream,
   `application/ogg`, `video/mp4`, `application/mp4`, `application/x-flac`
   (magic remains authoritative).
2. Hybrid heal: `isPlayableCached` / `initialize` delete + clear pin **only** for
   `POISON`. `UNKNOWN` → not playable, **keep file + pin**. Sync
   `hasPlayableSpan` for offline/BT seek (no Main-thread heal).
3. Await `removeCached` on Source-error SKIP_NEXT before seek/prepare; abort if
   auto-skip generation superseded or current item left `failedId`.
4. Scrobble: convert duration ms→sec; skip blank trackId
   (`computeScrobbleListenedSeconds`).
5. Cast: reconnect only for 2155/2161; abort connect timeout when session is
   starting on the target device; diagnostic transition logs use local Exo
   index while casting.
6. Download enqueue: `already_download` short-circuit for completed+pinned+playable.

## Consequences

- Mix auto-cache cannot mark API error bodies completed.
- Poison on disk is healed when playable checks run; exotic unknown magic keeps
  the pin until the user or a real re-download replaces it.
- Offline/BT jumps never seek to non-playable spans.
- Scrobble logs show real seconds (~125 not 125400 for ~3.5min tracks).
- Cast non-transient ends fall back to local without reconnect thrash.
- Existing CacheService unit fixtures must supply fake audio headers (≥4 KiB).
