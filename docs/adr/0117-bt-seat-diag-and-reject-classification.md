# ADR 0117 — BT seat-without-eager-play, DiagnosticLog BT crumbs, CT-reject classification

Date: 2026-10-11  
Status: Accepted  
Related: ADR-0087, ADR-0088, ADR-0101, ADR-0110, ADR-0048, ADR-0060  
Source: `/tmp/ftpmusic-phone.logs` (1.10.0 / Pixel 8 Pro / sdk=37)

## Context

Phone diagnostics showed:

1. Daily-mix auto-cache gaps: `worker reject contentType=application/json` ×108 on
   36 tracks (3 network-style retries each), then `failed` rows that every restore /
   mix regen re-touched as `failed_terminal` (log storm, permanent holes).
2. BT connect symptom on Android 17: brief play then stop; open app shows start of
   the seated track while Room still holds mid-track `pos`. Cause: `QueueManager.playAll`
   always `play()`; `applyBtPlayAfterSeat` then `pause()` + WIU notif; position poller
   only updates while `isPlaying`.
3. Zero `ftpmusic-bt` lines in Share diagnostics — BT path used `android.util.Log` only
   (R8-stripped / not in ring buffer).

Product must **not** bypass Android WIU / BAL / FGS rules. Unrestricted battery helps
reliability; it is not the fix.

## Decision

1. **CT / non-audio stream reject is terminal** — log HTTP `code=` plus Subsonic
   `errCode=` / short `errMsg=` only (no raw JSON paths), mark queue row `failed`
   immediately (no 3× short retry). Transient HTTP/network failures keep exponential
   retry.
2. **`failed_terminal` thrash** — priority 0/2 (prefetch / mix warm) hard-skip failed
   rows (debug log). **Exception:** priority 1 / `isDownload` user Download tap
   resurrects failed → pending (`result=retry_user`) so album/mix Download is not a
   silent no-op after a sticky server error. Mix auto-cache still skips terminal ids.
3. **BT seat without eager play** — restore path seats with `playWhenReady=false` when
   BT autoplay owns the decision; clear `playWhenReady` **before** `setMediaItems`+
   `prepare` to avoid residual flash. `applyBtPlayAfterSeat` still `play()` on API≤36
   and pause + Resume notif on API≥37 (ADR-0088/0101 unchanged).
4. **Paused position publish** — position mirror updates while paused after seek/restore
   so UI cannot stick at 0 when Exo holds saved offset.
5. **Mirror `ftpmusic-bt` → `DiagnosticLog`** — policy / start / seat / WIU / offline /
   fgsBlocked / result breadcrumbs in Share export (sync + async empty-queue).
6. **Never** launch Activity on A2DP or request mandatory battery exemption as the
   correctness path.

## Consequences

- Sticky bad trackIds become one reject + terminal hole for mix/prefetch; user can
  re-tap Download (pri=1) to retry after server fix.
- Next diagnostics dump can classify Navidrome errors without Share PII from raw bodies.
- A17 BT wake should not flash audible play before WIU pause.
- Open-app UI should show restored mid-track position when paused.
- OS background rules remain; HU Play / Resume notif stay the WIU path.

## Amendment (2026-10-11 correctness review)

Post-ship review of `ae38c15` found M1 (user download blocked) and M2 (prepare-then-pause
race). Remediation updates decisions 1–3 and 5 above. See
[AE38C15_CORRECTNESS_REVIEW_BEHAVIOR_REPORT.md](../AE38C15_CORRECTNESS_REVIEW_BEHAVIOR_REPORT.md).
