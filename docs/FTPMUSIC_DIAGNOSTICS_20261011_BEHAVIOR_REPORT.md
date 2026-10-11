# FTPMusic App Diagnostics (2026-10-11 capture) — Behavior Report

Date: 2026-10-11  
Status: Analyzed + remediation shipped (ADR-0117).  
Source: app diagnostics export `/tmp/ftpmusic-phone.logs` (800 lines). Not logcat.  
Build: 1.10.0 (15), sdk=37, Pixel 8 Pro. `offline=false reachable=true` at capture.  
Header: albums=2639 artists=321 tracks=20898. `capturedAt=2026-10-11T12:19:33Z`.  
Window: 2026-10-10T20:45 → 2026-10-11T09:24 (last event). ~3h silence before capture.  
Related: [ADR-0117](adr/0117-bt-seat-diag-and-reject-classification.md),
[ADR-0110](adr/0110-audio-cache-payload-validation.md),
[ADR-0101](adr/0101-bt-a17-silent-wake-and-offlan-cache-seat.md),
[BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md](BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md).

## Verdict (caveman)

| # | Finding | Count | Sev |
|---|---|---|---|
| F1 | Server returns `application/json` for stream — track-specific, sticky | 108 rejects / 36 tracks | High |
| F2 | `failed_terminal` replayed every queue restore | 33 tracks, 2 restores | High |
| F3 | Double log emission (~same ms) | ~68 near-dup pairs | Medium |
| F4 | Startup `healPoison` wiped **audio** poison spans (not covers) | 150 (143×182B) | Medium |
| F5 | Daily-mix auto-cache `missing` 15–32 at regen (audio holes) | 6 regens | Medium |
| F6 | Cast connect start, no result; 3h blind | 1 | Low |
| F7 | Reachability flap overnight + 1-min flip | 6 | Low |
| F8 | BT path invisible in Share dump (`Log` only) | 0 `ftpmusic-bt` | High (diag) |
| F9 | Seat stuck `idx=6 pos=106679`; `transition reason=3` = playlist restore | 2 wakes | Med (BT UX) |

No ERROR lines. Poison **write** gate healthy this capture (95 `writeCached`, all ≥619KB).

## BT background answer

**No — do not remove Android WIU/BAL/FGS blocks.** Correct path: FGS + sync seat +
API≥37 Resume notif + MediaButton / HU Play. Unrestricted battery helps; app must
still seat without eager `play()` flash and publish paused position (ADR-0117).

## Timeline (UTC)

- **10-10 20:45:35** — Start. `healPoison` ×150 audio spans (~182B JSON class).
- **20:46–20:47** — Nav + manual daily-mix regen. `mixCache missing` 15–32.
- **20:47:32+** — 66 JSON stream rejects while reachable. Interleaved healthy completes.
- **20:58 → overnight** — unreachable windows; restore @ 23:51 + 09:21 replays terminal.
- **09:22** — Mix regen day+1; more JSON rejects; new queue 79 tracks.
- **09:24** — Cast connect start only. Capture 12:19.

## Findings detail

### F1 — JSON instead of audio (High)

`worker reject … contentType=application/json` — 36 unique trackIds, 3 retries each.
Not global auth (other streams succeed same window). ADR-0117: terminal fail +
`code=` + body snippet in diagnostics.

### F2 — Terminal restore loop (High)

Same download ids hit `failed_terminal` on every reach/restore. Skip-resurrect was
correct; W-level spam + mix re-enqueue thrash were not. ADR-0117: D-level terminal
log; mix skips terminal ids.

### F4 — Poison heal = **audio** (correction)

Prior draft called these “poison covers”. Code: `healPoison` only in `CacheService`
audio path. Cover path logs `cacheNavidromeArt REJECTED` (absent this dump).
heal∩reject trackIds = 30 → same sticky server errors.

### F5 — Mix `missing` = audio only

`MixCacheCoordinator.hasMixOwnedSpan` — not cover art. Regen snapshot before worker
drain; JSON terminal holes make ~25–35% stay missing across days.

### F8/F9 — BT seat UX

Dump has no BT crumbs. User report (play one → stop; UI at track start) matches
`playAll`→`play()` then A17 `pause()`, plus paused poller skipping position while
Room holds `pos=106679`. Fixed in ADR-0117.

## Remediation (ADR-0117)

1. CT-reject → terminal + body snippet (no 3× retry).
2. Mix skip terminal; quieter `failed_terminal` logs.
3. BT restore seat `playWhenReady=false`; `applyBtPlayAfterSeat` owns play/WIU.
4. Paused position mirror after restore/seek.
5. `ftpmusic-bt` → `DiagnosticLog`.

## Open

- Navidrome root for 36 sticky ids — needs post-ship dump with body snippets.
- Logger double-emit (F3) — separate cleanup.
- Cast connect result breadcrumb — out of this loop.
