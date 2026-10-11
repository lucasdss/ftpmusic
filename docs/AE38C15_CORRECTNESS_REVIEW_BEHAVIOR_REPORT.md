# ae38c15 correctness review — Behavior Report

Date: 2026-10-11  
Status: Reviewed + remediated (follow-up commit).  
Related: [ADR-0117](adr/0117-bt-seat-diag-and-reject-classification.md),
[FTPMUSIC_DIAGNOSTICS_20261011_BEHAVIOR_REPORT.md](FTPMUSIC_DIAGNOSTICS_20261011_BEHAVIOR_REPORT.md).  
Ship under review: `ae38c15` (mix terminal + A17 BT seat).  
Phone dump: `/tmp/ftpmusic-phone.logs` — **still PRE-fix** `1.10.0 (15)`.

## Log vs ship

Dump has zero post-fix tokens (`ftpmusic-bt`, `code=`, `body=`, `skipTerminal`,
`worker terminal`, `result=wiu`). Cannot prove device behavior of `ae38c15` until
new Share diagnostics after install.

## What ae38c15 got right

- CT reject → immediate `failed` (no 3× retry); OkHttp peek safe.
- Mix skip terminal on regen / source change.
- BT seat without eager play + paused position mirror.
- Sync-path BT DiagnosticLog crumbs.

## MUST-FIX found (remediated)

| # | Bug | Fix |
|---|---|---|
| M1 | `enqueue` hard-skip `failed` for **all** priorities → user pri=1 Download tap silent no-op | Resurrect failed → pending when `isDownload` / pri=1; log `result=retry_user`. Pri 0/2 stay hard-skip. |
| M2 | `playAll` prepare-then-pause → residual playWhenReady flash on BT seat | Set `playWhenReady=false` before `setMediaItems`+`prepare` when seating. |

## SHOULD-FIX (remediated)

| # | Gap | Fix |
|---|---|---|
| S1 | Raw body snippet → Share may leak paths | Log Subsonic `error.code` + short message only |
| S2 | Async BT empty Room missing DiagnosticLog | Add `result=empty_queue` (match sync seat) |
| S3 | Thin tests | User-retry enqueue; playAll order; scrub; onSourceChanged skip |

## Uno-Reverse

ADR-0117 said “explicit clear/retry” but Downloads UI never wired `DownloadViewModel.retryDownload`.
Terminal-for-mix correct; terminal-for-user-download over-broad → M1.

## Validation need

Re-share diagnostics after installing build with this remediation. Expect:
`result=retry_user` on user Download, `ftpmusic-bt` crumbs on A2DP, reject lines with
`errCode=` not raw JSON bodies.
