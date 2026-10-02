# Logs Capture & Share — Behavior Report

Date: 2026-10-02
Related: ADR-0048, ADR-0060

## Market vs FTP Music

| Pattern | Market | FTP Music |
|---------|--------|-----------|
| Crash breadcrumbs (Crashlytics/Sentry) | Common | Out of scope (OSS) |
| Product analytics UI events | Common (Firebase/Amplitude) | Not used |
| Support export (user share) | Common for Play testing | Yes — Settings Share |
| App-owned buffer (not logcat) | Recommended (Android log disclosure) | Yes — `DiagnosticLog` |
| Domain coverage Cast/queue/cache | Expected in support crumbs | Bridged in ADR-0060 |

Verdict: shell matches support-export market. Pre-0060 coverage hollow for Cast/queue/mix/cache/UI. Post-0060 dual-write fills gaps under strict privacy.

## Pipeline

Writers → `DiagnosticLog` ring (CAP=1000) → Settings Share `ACTION_SEND` text/plain.
Clear wipes buffer. Process death → empty. DEBUG mirrors to Log.

## Domain matrix (post-0060)

| Domain | Tag | In share? | Key fields |
|--------|-----|-----------|------------|
| Cast session | `ftpmusic-cast` | yes | deviceId, sessionId, error, remote=, attempt |
| Cast queue | `ftpmusic-cast` | yes | count, startIdx, pos, actionType, ack, remote vs local size |
| Queue persist | `ftpmusic-playback` | yes | casting, tracks, contextSize, idx, pos, queueSaveFailed |
| Auto-skip / IO | `ftpmusic-playback` | yes | player error + `autoSkip action=` SKIP_NEXT / CACHED_JUMP / STOP_NO_CACHED / LAST_TRACK_STOP / RETRY_LIMIT_STOP / RADIO_IGNORE |
| Queue edit | `ftpmusic-playback` | yes | add/playNext/addAll/remove/move/reorder/clear/rollback/castAckFail |
| Metadata sync | `ftpmusic-metasync` | yes | mode, force, phase + counts, elapsed, fail |
| Download | `ftpmusic-download` | yes | trackId, priority, isDownload, result, bytes |
| Cache | `ftpmusic-cache` | yes | trackId, bytes, isDownload, refused_pinned, mix missing |
| Daily Mix | `ftpmusic-dailymix` | yes | mixId, skip/regen, listened/total, count, syncScreen fail |
| Playlist edit | `ftpmusic-playlist` | yes | create/import/add/remove/move/rename/delete + flush timeout/fail |
| UI | `ftpmusic-ui` | yes | route template, share/clear, cast user action deviceId |
| Scrobble/reach | existing tags | yes | unchanged |

Forbidden in share: titles, URLs, passwords, Cast friendlyName.

## Edge cases

- Cast disconnect mid syncLocalToRemote → ack fail + revision
- queueSaveFailed flip → DiagnosticLog
- Daily Mix cache hit → explicit skip regen line
- Metadata offline/unreachable/cooldown → skip crumbs (existing)
- Download failed terminal → one line; no resurrect loop
- Pinned remove → refused_pinned
- Offline blocks download worker → skip crumb (rate-limited)
- Player IO / uncached while unreachable → `autoSkip action=CACHED_JUMP` or `STOP_NO_CACHED`
- Corrupt cache while reachable → `SKIP_NEXT` + `removeCached`
- Cast queue ack fail → `queueEdit action=castAckFail` + local rollback
- Playlist flush timeout → retry crumb with changeType + playlistId

## UI events

Route enter (template + ids OK). Share/Clear diagnostics. Cast connect/disconnect user action (deviceId only). Not every Compose click.
