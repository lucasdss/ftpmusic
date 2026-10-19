# Diagnostics 2026-10-19 — BT Resume + Download Retry Storm

**Status:** Findings accepted; **fix shipped** (Gap B: `enqueuePlayQueue` passes `isDownload=false`; default remains `(priority == 1)`).  
**Date:** 2026-10-19  
**Analyst:** Agentic code×log cross-review (repo `main` at analysis time = `1.16.1` / versionCode 25)  
**Source dump:** User Share diagnostics (Pixel 8 Pro, sdk=37, app **1.16.0 (24)**)  
**Related ADRs / reports:**
- [ADR-0072](adr/0072-bluetooth-resume-any-or-selected.md) — ANY vs SELECTED
- [ADR-0087](adr/0087-media3-playback-resumption-and-bt-eager-restore.md) — Media3 resumption
- [ADR-0088](adr/0088-bt-resume-correctness-and-android17-wiu.md) — A17 WIU era
- [ADR-0101](adr/0101-bt-a17-silent-wake-and-offlan-cache-seat.md) — silent wake / offline seat
- [ADR-0117](adr/0117-bt-seat-diag-and-reject-classification.md) — CT reject terminal + `retry_user`
- [ADR-0119](adr/0119-bt-connect-autoplay-market-hardening.md) — market hardening
- [ADR-0133](adr/0133-bt-tap-resume-user-initiated-wiu.md) — tap ≠ background A2DP
- [ADR-0148](adr/0148-bt-autoplay-market-parity.md) — seat-only on API ≥37 (ships in 1.16.1)
- [ADR-0022](adr/0022-offline-enforcement-and-download-safety.md) — play-queue remainder at pri 1
- [ADR-0034](adr/0034-custom-daily-mix-architecture.md) — rejected pri 1 for auto-cache (pins downloads)
- [BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md](BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md)
- [BT_TAP_RESUME_WIU_LOOP_BEHAVIOR_REPORT.md](BT_TAP_RESUME_WIU_LOOP_BEHAVIOR_REPORT.md)
- [PLAY_RELEASE_1_16_0.md](PLAY_RELEASE_1_16_0.md) / [PLAY_RELEASE_1_16_1.md](PLAY_RELEASE_1_16_1.md)

---

## 0. Executive verdict (read this first)

| Area | Verdict | Severity if wrong |
|------|---------|-------------------|
| **Bluetooth A2DP resume flow** | **Correct** for Android 17 / market policy on the build that produced the dump. Sequence `warm_a2dp → allow → Started → wiu` is intentional seat-without-background-play. Missing `autoplay` / `tap_play` after background A2DP is **expected**, not a failure. | N/A (no BT bug in dump) |
| **Market parity vs Spotify/YTM/Apple/Synfonium** | **Aligned in intent** (seat MediaSession, stay paused, wait for HU / media Play). Device still on **1.16.0** posts app Resume notif (`wiu`). **main / 1.16.1** already removes happy-path Resume (`seat_only`, ADR-0148). | UX friction only on 1.16.0 |
| **Download / prefetch** | **Bug.** Two tracks permanently rejected as non-audio JSON are re-enqueued as `retry_user` on **every** track transition because play-queue remainder uses `priority=1`, which `DownloadManager` treats as user Download. | **High** (network/battery/log storm; false download pins) |
| **Playback session health** | **Healthy.** Scrobbles, transitions, `saveQueueState` continue across BT reconnects. BT does not brick the queue. | N/A |
| **Library / sync header** | Informational only (`albums=2296 artists=310 tracks=19952`, `offline=false reachable=true`). No anomaly tied to BT. | N/A |

**Recommended gate:** Revalidate this document before any code change. If accepted, implement **only** the `isDownload` / priority decoupling (section 5). Do **not** change A17 seat / background-play gates to “force autoplay.”

---

## 1. Dump metadata (verbatim facts)

```
ftpmusic diagnostics
version=1.16.0 (24)
sdk=37 model=Pixel 8 Pro
offline=false reachable=true
lastFullSyncMs=1792426951503 lastDeltaSyncMs=1792426406217
albums=2296 artists=310 tracks=19952
capturedAt=2026-10-19T18:05:30.409473Z
btResumeEvents=20
```

**Interpretation:**

- App under test = **Play / internal 1.16.0**, versionCode **24**.
- Analysis-time repo `compose/app/build.gradle.kts` = **1.16.1 / 25** (already includes ADR-0148 seat-only).
- Device is **one release behind** the BT market-parity cut documented in `PLAY_RELEASE_1_16_1.md`.
- `btResumeEvents=20` = ring buffer full (last-20 crumbs). Header summary then lists five connect cycles, each ending in `wiu`.

---

## 2. Bluetooth — log map and code mapping

### 2.1 Observed BT crumb pattern (every connect in dump)

Header section (condensed):

```
warm_a2dp | macPresent=true
allow | mode=ANY sdk=37
Started | mode=ANY sdk=37
wiu | sdk=37
```

Detailed log (same events, richer):

```
D | ftpmusic-bt | warm_a2dp macPresent=true
D | ftpmusic-bt | policy=allow mode=ANY sdk=37
D | ftpmusic-bt | fgsBlocked=false result=Started sdk=37
D | ftpmusic-bt | result=Started mode=ANY sdk=37
D | ftpmusic-bt | result=wiu sdk=37
```

Timestamps of BT cycles in dump (UTC):

| Time | Notes |
|------|--------|
| 10:32:20 | After `saveQueueState` at 10:26 (idx=29). Position barely moved → likely paused / near-paused before reconnect. Immediate `saveQueueState` after seat. |
| 10:34:30 | Second warm A2DP within ~2 min (debounce / re-pair / device bounce). |
| 10:44:12 | Third warm A2DP. |
| 13:43:07 | After multi-hour gap (11:04 → 13:43). Service still alive enough for warm monitor **or** process restarted with warm path. Still `Started` + `wiu`. |
| 14:19:13 | Another warm A2DP; `saveQueueState` idx=38 shortly after. |

No `NotificationFallback`, `fgsBlocked=true`, `notif_disabled`, `selected_null_mac`, `offline_empty`, `empty_queue`, `tap_play`, or `autoplay` appear in the provided dump.

### 2.2 Token dictionary (code-backed)

| Token | Emitter | Meaning |
|-------|---------|---------|
| `warm_a2dp` | `BtWarmAudioDeviceMonitor` | `AudioDeviceCallback.onAudioDevicesAdded` saw A2DP while MediaService process alive. Not the cold ACL-only path. |
| `macPresent=true` | same | A device address was readable for policy / allowlist. |
| `policy=allow mode=ANY` | `BtResumePolicy` + receiver/starter path | Settings: resume enabled, mode ANY; debounce OK; not casting. |
| `fgsBlocked=false result=Started` | `BtAutoplayStarter.startAutoplay` | `startForegroundService(ACTION_BT_AUTOPLAY)` succeeded (no `ForegroundServiceStartNotAllowedException`). |
| `result=wiu` | `MediaService.applyBtPlayAfterSeat` on **1.16.0** | API ≥37: seat queue, `pause()`, post/keep Resume-notif era behavior (ADR-0088/0133 era naming). |
| `result=seat_only` | same method on **1.16.1 / main** | ADR-0148 rename + behavior: seat + pause + **cancel** Resume `1002` on happy path. |
| `autoplay` | `applyBtPlayAfterSeat` when `shouldPlayAfterBtSeat` and not user-initiated | Background `play()` after seat — only when `sdkInt < 37`. |
| `tap_play` | same when `userInitiated=true` | Resume notif PendingIntent with `EXTRA_USER_INITIATED_RESUME` (ADR-0133). |

### 2.3 Call graph (as implemented)

```
A2DP device add (warm)
  → BtWarmAudioDeviceMonitor → dispatch
  → BtResumePolicy.shouldResume (mode ANY / SELECTED + MAC)
  → BtAutoplayStarter.startAutoplay
       try startForegroundService(ACTION_BT_AUTOPLAY)
       catch FGS deny → postResumeNotification(1002) + NotificationFallback
  → MediaService onStartCommand
       seat Room queue (playWhenReady=false)
       applyBtPlayAfterSeat(sdk, userInitiated)
            if shouldPlayAfterBtSeat → play() + cancel 1002 + autoplay|tap_play
            else → pause() + (1.16.0: wiu/Resume) | (1.16.1: seat_only, cancel 1002)

Cold path (process dead):
  BtConnectionReceiver (A2DP CONNECTION_STATE_CHANGED)
  → same policy → same starter → same seat

Primary audible resume on API ≥37 (market):
  Head-unit AVRCP MEDIA_PLAY / System UI MediaButton
  → MediaButtonReceiver → MediaSession.Callback.onPlaybackResumption
  → FGS + WIU exemption → play()
```

Key gates in code (`PlaybackResumptionMapper`):

```kotlin
fun shouldBackgroundAutoplay(sdkInt: Int): Boolean = sdkInt < ANDROID_17_API // 37
fun shouldPlayAfterBtSeat(sdkInt: Int, userInitiated: Boolean): Boolean =
    userInitiated || shouldBackgroundAutoplay(sdkInt)
```

On sdk=37 with `userInitiated=false` (pure A2DP wake): **must not** call `play()`. Dump matches this gate.

### 2.4 Why this matches market apps

From [BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md](BT_CONNECT_AUTOPLAY_MARKET_BEHAVIOR_REPORT.md) and [ADR-0148](adr/0148-bt-autoplay-market-parity.md):

| Pattern | Market | FTP Music |
|---------|--------|-----------|
| Seat MediaSession on BT connect | Spotify / YTM / Apple / Synfonium | Yes (`ACTION_BT_AUTOPLAY` seat) |
| Stay paused until OS/HU Play | Same | Yes on API ≥37 |
| App-specific “Resume playback” notif on every connect | Market: **no** | 1.16.0: **yes** (`wiu`); 1.16.1: **no** (`seat_only`) |
| Opt-in “resume on Bluetooth” setting | Poweramp-style | Yes (`BtResumeStorage`) |
| Deep-sleep zero-gesture autoplay guaranteed | **No** (OS FGS/WIU) | **No** — documented non-goal |
| MediaButton / `onPlaybackResumption` | Media3 primary cold path | Implemented (ADR-0087) |

**OS constraints that force seat-only on API ≥37:**

1. Background FGS started from A2DP often lacks while-in-use (WIU).
2. Background `play()` can be silently muted (`AudioHardening` in system logcat — not always present in Share dump).
3. Market apps do not depend on app-side A2DP `play()`; they depend on HU AVRCP PLAY granting a media-button exemption.

**Therefore:** Interpreting “Started then wiu / no audio from A2DP alone” as a **product bug** would fight OS policy and diverge from market. Interpreting it as **correct seat** is the intended architecture.

### 2.5 Build gap: `wiu` vs `seat_only`

| Build | Token after seat on API ≥37 | Resume notif 1002 on happy path |
|-------|------------------------------|----------------------------------|
| 1.16.0 (24) — **this dump** | `wiu` | Yes (pre-0148) |
| 1.16.1 (25) — **main** | `seat_only` | No (cancel stale 1002) |

Reasoning: ADR-0148 explicitly renamed telemetry `wiu → seat_only` and removed happy-path Resume friction because market apps do not show an app Resume chip on every Bluetooth connect. The dump’s `wiu` token is **not** evidence that main is broken; it is evidence the **phone has not upgraded**.

Field revalidation after upgrade should show:

```
warm_a2dp → allow → Started → seat_only
```

…still with **no** `autoplay` on background A2DP.

### 2.6 Playback continuity around BT (why BT did not “break” play)

Examples from dump:

1. **10:26** `saveQueueState` idx=29 pos≈111799 → **10:32** BT seat → pos≈112180 (+~381 ms over ~6 min) → suggests mostly paused before reconnect, then seat preserved index.
2. **10:47** scrobble + transition idx=30 — audible play resumed somehow (user / HU / media notif — dump has no `tap_play` crumb, so likely MediaButton/HU or in-app, not Resume-notif path).
3. **11:04 → 13:43** long gap at idx=35, then BT reconnect, then **13:48** transition reason=2 (skip/seek-style) — session still valid.
4. Multiple `saveQueueState casting=false tracks=96 contextSize=96` — dual-queue size stable; casting not involved.

**Conclusion:** BT path seated / refreshed service successfully. Absence of autoplay crumbs is policy, not a broken FGS start (`fgsBlocked=false` every time).

### 2.7 Edge cases **not** observed (but relevant)

If future dumps look “broken,” classify against these:

| Symptom in dump | Likely cause |
|-----------------|--------------|
| `fgsBlocked=true` / `NotificationFallback` | Doze / battery restricted blocked background FGS |
| `notif_disabled` | POST_NOTIFICATIONS / app notifs off — Resume fallback dead |
| `selected_null_mac` | SELECTED mode + unreadable MAC — Resume only by design (ADR-0101) |
| `offline_empty` / info 1003 | Network blocked and no cached span to seat |
| Only `warm_a2dp|allow|Started` with **no** `wiu`/`seat_only` | Seat path aborted / crumb loss — investigate MediaService |
| `Started` then `wiu` then endless Resume taps with no `tap_play` | Pre-0133 bug (fixed); on 1.16.0+ tap should emit `tap_play` |

None of the failure rows above appear in this dump’s BT section.

### 2.8 Uno-reverse challenges (assumptions interrogated)

1. **“User wanted autoplay on connect.”** If true, product expectation conflicts with A17 + ADR-0148. Fix is Settings copy / UX honesty, not silent `play()`.
2. **“`wiu` means WIU capability granted.”** Naming is historical. Token means “seat + pause (+ Resume notif on 1.16.0)”, not “AudioFocus WIU granted.”
3. **“Warm path only fires when already playing.”** Warm fires on A2DP add while service alive regardless of `isPlaying`. Multiple reconnects while paused are normal.
4. **“Market apps autoplay.”** Often the **car** sends PLAY; the app merely holds the last session. Attribute audio start to HU, not to A2DP broadcast alone.

---

## 3. Download / prefetch — the actual defect

### 3.1 Observed storm (two track IDs)

Sticky failures:

- `1o6O4Xu0rr2eoPcTQkTPED`
- `4POTh19F962HBWLKbWONg0`

Repeating unit (seen at 10:24, 10:47, 10:50, 10:54, 10:58, 11:03, 13:48, 13:51, 13:54, 14:19, …):

```
D | ftpmusic-download | enqueue trackId=<id> priority=1 isDownload=true result=retry_user
W | ftpmusic-download | worker reject trackId=<id> contentType=application/json code=200
                       errCode=unreadable errMsg=unreadable
W | ftpmusic-download | worker terminal trackId=<id> priority=1 reason=non_audio_content_type
```

Same transitions also enqueue ~50+ other tracks as `already_download` and one ahead track as `cached_skip_upgrade` — proving the **prefetch-on-transition** path runs for the whole remainder of the 96-track queue.

### 3.2 Why CT reject itself is correct

`DownloadManager.downloadItem`:

1. HTTP success (`code=200`).
2. `Content-Type: application/json` fails `AudioCacheValidation.isAcceptableStreamContentType`.
3. ADR-0117: sticky server error body → `failTerminal` immediately (no 3× short retry).
4. `summarizeSubsonicError` peek fails → `errCode=unreadable errMsg=unreadable` (body not parseable as Subsonic error JSON, or peek threw). Classification still “non-audio”; terminal still correct.

**Do not** “fix” this by accepting JSON as audio. That would poison the cache (ADR-0110 / 0117).

Server-side reality (inference, not proven from body): stream endpoint returns an error document with HTTP 200 — missing file, auth, or Navidrome JSON error. Ops may fix those two IDs separately; client must still hard-stop retries for prefetch.

### 3.3 Why `retry_user` appears after terminal failure

ADR-0117 decision text:

> Exception: priority 1 / `isDownload` user Download tap resurrects failed → pending (`result=retry_user`)

Implementation (`DownloadManager.enqueue`):

```kotlin
suspend fun enqueue(trackId: String, url: String, priority: Int = 2) {
    val isDownload = priority == 1
    // ...
    if (existing.status == "failed") {
        if (isDownload) {
            // reset pending, retry_count=0, maybe markAsDownload
            // log result=retry_user
            return
        }
        // log result=failed_terminal
        return
    }
}
```

So **any** enqueue with `priority == 1` against a `failed` row is treated as an explicit user Download retry.

### 3.4 Why play-queue remainder uses priority 1

`PlaybackManager.enqueuePlayQueue` (ADR-0022):

- Next 3 tracks after current → **priority 0** (urgent continuity window).
- Remainder of queue → **priority 1** (constraint-gated: Wi-Fi / battery / mobile-data).
- Invoked from `MediaService` on **every media-item transition** (and related paths).

```kotlin
val priority = if (i < urgentEnd) 0 else 1
downloadManager.enqueue(alignedTracks[i].id, alignedUrls[i], priority = priority)
```

There is **no** separate `isDownload` argument. Derivation `isDownload = (priority == 1)` collapses two orthogonal concepts:

| Concept | Intended owner | Numeric encoding today |
|---------|----------------|------------------------|
| Constraint / urgency tier | ADR-0022 remainder = pri 1 | `priority` |
| Permanent user Download pin | ADR-0117 / Downloads screen | also `priority == 1` → `isDownload` |

ADR-0034 already rejected priority 1 for **mix auto-cache** because it pins tracks as downloads. Play-queue remainder accidentally reintroduced the same overload.

### 3.5 Why urgent window does not storm

If a bad track sits in the next-3 window, enqueue uses `priority=0` → `isDownload=false` → failed rows log `failed_terminal` and stay dead. The dump’s storms always show `priority=1 isDownload=true result=retry_user`, i.e. **remainder** membership, not urgent window.

As the playhead advances, the two bad IDs remain in the “rest of queue” set for a long time → every transition re-resurrects them.

### 3.6 Secondary hazard (even when download eventually “succeeds”)

On resurrect path:

```kotlin
if (!existing.isDownload) {
    cacheQueueDao.markAsDownload(existing.id)
}
```

A remainder prefetch that somehow later receives real audio would mark the row as a **user download**, contradicting ADR-0034 / Downloads semantics (auto-cache vs pinned download).

Also: storm burns radio, wakes workers, fills diagnostics, and can fight user-visible download UI with flapping pending/failed states.

### 3.7 Call-site inventory (for a future fix — not applied yet)

**Play-queue / auto-cache (must pass `isDownload=false` after fix):**

- `PlaybackManager.enqueuePlayQueue` — pri 0 and pri 1 remainder
- `MixCacheCoordinator` — typically pri 2 today
- `AlbumDetailViewModel` prefetch path using pri 2

**True user Download (must pass `isDownload=true`):**

- `LibraryViewModel`, `PlaylistDetailViewModel`, `PlaylistRepository`
- `ArtistDetailViewModel`, `AlbumDetailViewModel` download actions
- `MixDetailScreen` download actions

Today all of the user paths pass `priority = 1` and rely on the derivation. That is why the derivation existed (ADR-0117 amendment / ae38c15 M1) — but it is also why prefetch stole the exception.

### 3.8 Timeline coupling to playback (proof of per-transition re-enqueue)

| Transition time | Immediately followed by enqueue burst including `retry_user` for both bad IDs |
|-----------------|------------------------------------------------------------------------------|
| 10:47:01 idx 29→30 | Yes |
| 10:50:43 idx 30→31 | Yes |
| 10:54:50 idx 31→32 | Yes |
| 10:58:46 idx 32→33 | Yes |
| 10:58:52 idx 33→34 (reason=2) | Yes |
| 11:03:58 idx 34→35 | Yes |
| 13:48:20 idx 35→36 | Yes |
| … | continues |

This is not a WorkManager periodic retry alone; it is **synchronous product logic** on transition → `enqueuePlayQueue`.

---

## 4. What is healthy in the dump (to avoid false fixes)

1. **Most enqueues are `already_download`** — local-first cache working; idempotent completed+on-disk guard works for success path.
2. **`cached_skip_upgrade` on next track** — completed auto-cache ahead of playhead; priority upgrade without re-download.
3. **Scrobble lines** with sensible `secs` / `heardMs` — listening accounting alive.
4. **`transition … reason=1|2`** — Media3 transitions occurring; queue length stable at 96.
5. **BT FGS always Started** — no Doze FGS denial in this capture window.
6. **Reachability / offline flags** — online; not an offline-seat edge case.

---

## 5. Proposed fix (for revalidation — **not implemented**)

### 5.1 Decision proposal

Decouple permanent-download intent from numeric priority:

1. Change signature to  
   `enqueue(trackId, url, priority: Int = 2, isDownload: Boolean = false)`.
2. **Stop** `val isDownload = priority == 1`.
3. `enqueuePlayQueue`: urgent pri 0 + remainder pri 1, **both** `isDownload = false`.
4. UI / repository Download taps: `priority = 1, isDownload = true` (priority may stay 1 for constraint tiering, or stay as today).
5. Resurrect / `markAsDownload` / promote-to-download **only** when `isDownload == true`.
6. Keep CT reject + `failTerminal` unchanged.

### 5.2 Alternatives considered (and why weaker)

| Alternative | Pros | Cons |
|-------------|------|------|
| Move remainder to priority **2** | Minimal signature churn | Changes constraint gating vs ADR-0022; still conflates meaning if something else uses pri 1; does not fix false `isDownload` if any other pri-1 auto path exists |
| Never resurrect failed (even for user Download) | Stops storm | Regresses ADR-0117 / ae38c15 M1 — Download tap becomes silent no-op after sticky CT error |
| Accept JSON 200 as audio | Stops reject | Poisons cache; wrong |
| Cooldown / backoff on terminal CT | Reduces storm rate | Leaves semantic bug; remainder still “user download” |
| Force A17 autoplay | Matches naive user expectation | Conflicts OS + market; muted play; ADR-0148 explicitly rejected |

**Preferred:** explicit `isDownload` (row 1).

### 5.3 Tests required before ship

- Failed row + `enqueue(..., priority=1, isDownload=false)` → `failed_terminal`, status stays `failed`.
- Failed row + `enqueue(..., priority=1, isDownload=true)` → `retry_user`, status `pending`.
- New remainder insert does not set `isDownload` on entity.
- Existing DownloadManager / Library / Playlist tests updated for new parameter.
- BT unit tests remain green (`seat_only` on API 37); **no** BT production change required for this dump’s correctness.

### 5.4 Docs / ADR follow-up (after code lands)

- Amend ADR-0117: “user Download” = `isDownload=true`, **not** `priority==1`.
- Amend ADR-0022 note: remainder pri 1 is constraint tier only.
- Optional short ADR for the split if amendment feels too large.

### 5.5 Field verification checklist (post-fix + upgrade)

1. Install **1.16.1 (25)** (or build containing both ADR-0148 and `isDownload` split).
2. Share diagnostics after A2DP connect: expect `seat_only`, not `wiu`.
3. Play through a queue containing known CT-bad IDs: expect **one** terminal reject, then `failed_terminal` on later transitions — **zero** `retry_user` unless user taps Download.
4. Explicit Download on a previously failed track: expect `retry_user` once per tap.
5. Confirm Downloads screen does not suddenly pin remainder-prefetch tracks.

---

## 6. Double-Diamond / Vibe notes (process)

**Discover (divergent):** Mapped BT vs download as separate systems; consulted existing market ADRs; ran Mistral Vibe CLI (`VIBE_CLI=python vibe -p …`) which independently recommended: (a) seat-only on A17 with Resume as escape hatch; (b) split overloaded priority vs user-initiated download flag; (c) keep terminal non-audio hard-fail.

**Define (convergent):** BT dump = success for seat policy; download = priority/`isDownload` collision bug.

**Develop / Deliver:** Deferred until this document is revalidated by the human. No code, no commit, no push from this findings pass.

---

## 7. Open questions for human revalidation

Answer these before authorizing implementation:

1. **BT symptom you care about:** Was audio expected to start **automatically** on headphone/car connect with zero Play gesture on Android 17? (If yes → UX/docs issue, not a silent-`play()` bugfix.)
2. **Device upgrade:** Can you install 1.16.1 and re-capture crumbs to confirm `seat_only` before we treat Resume-notif removal as validated on your hardware?
3. **Download fix scope:** Approve the explicit `isDownload` parameter approach (section 5.1), or prefer remainder→priority 2 despite ADR-0022 constraint semantics?
4. **Server tracks:** Should ops also investigate why those two Spotify-style IDs return `application/json` on `/rest/stream`? (Client fix still needed regardless.)

---

## 8. File / symbol index (for reviewers)

| Path | Role in this review |
|------|---------------------|
| `compose/.../playback/BtWarmAudioDeviceMonitor.kt` | `warm_a2dp` |
| `compose/.../playback/BtConnectionReceiver.kt` | Cold A2DP |
| `compose/.../playback/BtResumePolicy.kt` | allow / debounce |
| `compose/.../playback/BtAutoplayStarter.kt` | FGS Started / fallback |
| `compose/.../playback/BtResumeEvents.kt` | last-20 header crumbs |
| `compose/.../playback/MediaService.kt` | seat + `applyBtPlayAfterSeat`; transition → `enqueuePlayQueue` |
| `compose/.../playback/PlaybackResumptionMapper.kt` | A17 play gates |
| `compose/.../playback/PlaybackManager.kt` | `enqueuePlayQueue` pri 0/1 |
| `compose/.../data/cache/DownloadManager.kt` | `isDownload = priority == 1`; reject; `failTerminal`; `retry_user` |
| `docs/adr/0148-bt-autoplay-market-parity.md` | seat_only decision |
| `docs/adr/0117-bt-seat-diag-and-reject-classification.md` | CT terminal + retry_user exception |
| `docs/adr/0022-offline-enforcement-and-download-safety.md` | remainder at pri 1 |

---

## 9. One-line summary

**BT path in this dump behaves as designed for Android 17 seat-without-background-play (upgrade to 1.16.1 for market-parity `seat_only` UX); the actionable defect is play-queue remainder priority colliding with user-Download resurrection, causing infinite `retry_user` storms on two permanently non-audio tracks.**

---

## 10. Amendment — Agentic revalidation + shipped fix (2026-10-19)

### 10.1 Points verified correct

Primary report claims (derivation, `retry_user`/`failed_terminal`, CT reject, `enqueuePlayQueue` pri 0/1, A17 gates, ADR refs, CAP=20, call-site inventory) confirmed against `compose/` source.

### 10.2 Gaps addressed

| Gap | Resolution |
|-----|------------|
| **A** migration backfill | Comment on `MIGRATION_31_32`; never re-derive; leave existing pins |
| **B** default footgun | Shipped `isDownload: Boolean = (priority == 1)`; only `enqueuePlayQueue` passes `false` |
| **C** mix skips terminal | Unchanged; play-queue fixed via Gap B |
| **D** pin at insert | Tests assert remainder insert `!isDownload` |
| **E** blast radius | Fix stops new false pins; existing pins left |

### 10.3 Revised fix (shipped)

```kotlin
suspend fun enqueue(..., priority: Int = 2, isDownload: Boolean = (priority == 1))
// enqueuePlayQueue: isDownload = false for urgent + remainder
```

User Download call sites unchanged.
