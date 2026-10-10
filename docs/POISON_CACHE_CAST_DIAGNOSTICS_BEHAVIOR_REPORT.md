# Poison Cache + Cast + Scrobble — Behavior Report

Date: 2026-10-10  
Source: device diagnostics v1.8.0 Pixel 8 Pro  
Status: fixed (client harden)

Follow-up (Cast CP ack flood, Source circuit, storm guards):
[CAST_PLAYBACK_DIAGNOSTICS_BEHAVIOR_REPORT.md](CAST_PLAYBACK_DIAGNOSTICS_BEHAVIOR_REPORT.md) / ADR-0111.

## Symptom (logs)

- Mix auto-cache: `writeCached … bytes=182` (cluster) mixed with real MB sizes
- Play mix track → `player error: Source error` → `autoSkip SKIP_NEXT` → `removeCached`
- User reselect same track → loop
- Cast: connect timeout + `session ended error=2161` / later `2055`
- Scrobble: `secs=125400` (ms treated as sec)
- Dual `enqueue … result=promoted` noise

## Root cause

1. **Poison cache:** HTTP 200 Subsonic error JSON/XML accepted as audio. `writeCachedTrackFromFile` rejected only `size==0`.
2. **Scrobble units:** `QueueManager` stores duration **ms**; scrobble read as seconds.
3. **Cast reconnect:** all session-end codes retried; non-transient 2055 thrashed.
4. **Promote noise:** completed+downloaded re-enqueue re-logged `promoted`.

## Final behavior

| Path | Behavior |
|------|----------|
| Download / mix cache write | Reject non-audio (min 4KiB, Subsonic sniff, magic). Never `completed` on poison. |
| `isPlayableCached` | Span + audio valid; heals poison (remove span + clear Room). |
| Mix `missing` | Uses playable check — poison re-enqueued. |
| Source error SKIP_NEXT | Await `removeCached` before seek/prepare. |
| Scrobble | durationMs/1000; blank trackId skip. |
| Cast end | Reconnect only 2155/2161. |
| Connect timeout | Abort when live **or** session starting on target. |
| Enqueue pri=1 | `already_download` when completed+download+playable. |
| Transition diag | Skip blank mediaId; while casting log local Exo index. |

## Edge cases mapped

- HTTP 200 + `Content-Type: application/json` → worker retry, no write
- 182B / 304B / 379B poison → reject at write + heal if already stored
- Pinned download refuse still applies in `removeCached`; poison heal clears false pin
- Cast EMPTY mediaId → no scrobble, no transition breadcrumb as real track

## Coverage targets

- `AudioCacheValidation` + DownloadManager content-type reject + Mix poison enqueue + scrobble helper + cast end filter ≥80% line/branch on those units.
