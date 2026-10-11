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

1. **CT / non-audio stream reject is terminal** — log `code=` + body snippet (~200B
   scrubbed), mark queue row `failed` immediately (no 3× short retry). Transient
   HTTP/network failures keep exponential retry.
2. **`failed_terminal` thrash** — keep skip-resurrect semantics; log at debug for
   restore/prefetch noise; mix auto-cache skips terminal ids until explicit clear/retry.
3. **BT seat without eager play** — restore path seats with `playWhenReady=false` when
   BT autoplay owns the decision; `applyBtPlayAfterSeat` still `play()` on API≤36 and
   pause + Resume notif on API≥37 (ADR-0088/0101 unchanged).
4. **Paused position publish** — position mirror updates while paused after seek/restore
   so UI cannot stick at 0 when Exo holds saved offset.
5. **Mirror `ftpmusic-bt` → `DiagnosticLog`** — policy / start / seat / WIU / offline /
   fgsBlocked / result breadcrumbs in Share export.
6. **Never** launch Activity on A2DP or request mandatory battery exemption as the
   correctness path.

## Consequences

- Sticky bad trackIds become one reject + terminal hole (honest) instead of retry storms.
- Next diagnostics dump can classify Navidrome errors from body snippet.
- A17 BT wake should not flash audible play before WIU pause.
- Open-app UI should show restored mid-track position when paused.
- OS background rules remain; HU Play / Resume notif stay the WIU path.
