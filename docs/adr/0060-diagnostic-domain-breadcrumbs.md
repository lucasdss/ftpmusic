# ADR 0060 — Diagnostic Domain Breadcrumbs

Date: 2026-10-02
Status: Accepted
Related: ADR-0048, Play diagnostics export

## Context

ADR-0048 added in-app `DiagnosticLog` + Settings share. Coverage limited to
sync start/end, scrobble, reachability, playback transition. Cast, queue
sync, Daily Mix, cache/download, and UI lived only in logcat — R8 strips
`Log.d/v` in release; Play testers cannot attach logcat.

## Decision

1. Dual-write domain breadcrumbs into `DiagnosticLog` (keep logcat for DEBUG).
2. Tags: `ftpmusic-cast`, `ftpmusic-playback`, `ftpmusic-metasync`,
   `ftpmusic-download`, `ftpmusic-cache`, `ftpmusic-dailymix`,
   `ftpmusic-playlist`, `ftpmusic-ui`. Includes auto-skip recovery actions
   and queue/playlist mutation breadcrumbs.
3. Strict fields only: ids, counts, indices, flags, error codes, bytes,
   priority. No titles, URLs, tokens, Cast friendlyName.
4. Ring buffer CAP = 1000. Process death still drops buffer (no disk v1).
5. One line per state/phase transition; no 5 Hz poll spam; suppress noisy
   play-queue `cached_skip` unless priority upgrade.
6. Snapshot header may include library counts (albums/artists/tracks).

## Consequences

- Share diagnostics answers Cast/queue/mix/cache/sync support questions.
- Buffer fills faster under Cast — 1000 CAP + phase-boundary logging
  mitigates.
- Extends ADR-0048; Crashlytics/Sentry remain out of scope for OSS client.
- Append scrub: URL redact + 200-char cap on msg/throwable text (central).
- Share builds snapshot off main (`Dispatchers.Default`); chooser guarded
  against `ActivityNotFoundException`. Snapshot copies under lock, joins outside.
