# BT / Source-error 182B log analysis — Behavior Report

Date: 2026-10-10  
Source: device diagnostics `version=1.8.0 (12)` Pixel 8 Pro `sdk=37`  
Captured: `2026-10-10T17:21:53.634161Z`  
Status: analyzed — primary fix shipped on main as **1.9.0 (versionCode 13)**  
Related: [POISON_CACHE_CAST_DIAGNOSTICS_BEHAVIOR_REPORT.md](POISON_CACHE_CAST_DIAGNOSTICS_BEHAVIOR_REPORT.md),
[ADR-0110](adr/0110-audio-cache-payload-validation.md),
[ADR-0111](adr/0111-cast-cp-batch-ack-and-source-circuit.md),
[ADR-0112](adr/0112-post-180-ship-blockers-19.md),
[ADR-0087](adr/0087-media3-playback-resumption-and-bt-eager-restore.md),
[ADR-0088](adr/0088-bt-resume-correctness-and-android17-wiu.md),
[ADR-0101](adr/0101-bt-a17-silent-wake-and-offlan-cache-seat.md),
[BT_RESUME_SYSTEM_MEDIA_BEHAVIOR_REPORT.md](BT_RESUME_SYSTEM_MEDIA_BEHAVIOR_REPORT.md),
[BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md](BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md)

## Verdict (Caveman)

Poison cache win. Subsonic error JSON ~182B written as audio on **1.8.0**.
Exo → Source error → SKIP_NEXT cascade. Worker race re-writes poison after
`removeCached`. Pinned downloads → `refused_pinned` → stuck bad spans.
One healthy track (~183s) then cascade again. Lands on playable idx=6.

BT “dies until open app” on **sdk=37** = second layer (WIU / FGS), not Activity
auto-open. **No `MainActivity` launch on BT MAC** — BAL blocked. Use service
seat + Resume notif + HU Play.

## Header snapshot

| Field | Value | Read |
|-------|-------|------|
| version | 1.8.0 (12) | Pre-ADR-0110 poison gate |
| sdk / model | 37 / Pixel 8 Pro | Android 17 WIU path |
| offline / reachable | false / true | Network OK at capture |
| lastFullSyncMs | 1791632144911 | Full sync done |
| lastDeltaSyncMs | 0 | Never delta — stale-library risk only |
| albums / artists / tracks | 2639 / 375 / 20898 | Large library |
| casting (throughout) | false | Local Exo, not Cast |

## Timeline (compressed)

| Time (UTC) | Event |
|------------|-------|
| 17:16:11.448 | `route=splash` |
| 17:16:11.456 | `transition id=m4Le2uu6… reason=3 idx=0 count=50` — queue restore |
| 17:16:11.47–.70 | Prefetch storm: `enqueue … priority=1 isDownload=true result=promoted` **×2** per track |
| 17:16:11.481 | `route=home` |
| 17:16:11.831 | `player error: Source error` track `m4Le2uu6…` → `autoSkip SKIP_NEXT` → `removeCached … ok` |
| 17:16:11.835 | transition `iYV9Kb…` idx=1 |
| 17:16:11.959 | Source error `iYV9Kb…` → SKIP_NEXT → `removeCached ok` |
| 17:16:11.961 | transition `7yo3G00…` idx=2 |
| 17:16:12.722 | **`writeCached trackId=iYV9Kb… bytes=182 isDownload=false`** + `worker completed` — poison accept path (1.8.0) **after** remove |
| 17:16–17:19 | `7yo3G00…` plays ~183s; `scrobble … secs=183` |
| 17:19:16.062 | natural AUTO → `73wRXlh…` idx=3 → Source error &lt;2ms → SKIP_NEXT |
| 17:19:16.100 | `60FvFoBD…` idx=4 → Source error → SKIP_NEXT → **`removeCached … refused_pinned`** |
| 17:19:16.163 | `BY8CqpJb…` idx=5 → Source error → SKIP_NEXT → **`refused_pinned`** |
| 17:19:16.223 | `3VnoBCh6…` idx=6 — plays |
| 17:21:03 | `saveQueueState … idx=6 pos=106674` (~107s into track) |
| 17:21:47 | `route=settings` → share diagnostics |

## Root causes

### 1. Poison audio cache (smoking gun)

- Success log token: `writeCached trackId=… bytes=182 isDownload=false`  
  (= accept path in `CacheService.writeCachedTrackFromFile`).  
  Reject token would be: `writeCached reject … reason=non_audio`.
- ~182B = Subsonic/Navidrome HTTP 200 error JSON treated as audio on 1.8.0
  (`length() > 0` only).
- Race: `removeCached(iYV9Kb…)` at .960 → worker `writeCached … 182` at .722s next
  second re-poisons same id.

**Main (1.9.0):** `AudioCacheValidation.MIN_CACHED_AUDIO_BYTES = 4KiB` + Subsonic
sniff + magic; poison heal; pinned poison → `heal_poison_pinned` (ADR-0110/0112).

### 2. AutoSkip cascade (no circuit on 1.8.0)

- Per-track `errorCount` resets on `onMediaItemTransition`.
- Reachable → `SKIP_NEXT` + `removeCached` every bad track.
- Cascade idx 0→1 then later 3→4→5 in &lt;100ms each.

**Main:** `SourceErrorCircuit` ≥3 errors / 15s → `CIRCUIT_STOP` (no further
removeCached thrash) — ADR-0111.

### 3. Prefetch / promote storm

- Every local transition → `PlaybackManager.enqueuePlayQueue` over **all**
  remaining indices.
- Twin `promoted` lines ≈ restore + transition (or dual observers).
- Diagnostic volume; not root of Source error. 1.9.0 short-circuits:
  `already_download`, `cached_skip_upgrade`.

### 4. BT / “play dies until open app” (sdk=37)

Capture itself is **app-foreground** (`splash`→`home`→`settings`). Proves
poison/Source-skip; does **not** alone prove BT-only network failure.

User symptom + sdk=37 maps to existing design:

1. A2DP → `BtConnectionReceiver` → `BtResumePolicy` → `BtAutoplayStarter` →
   `MediaService` `ACTION_BT_AUTOPLAY` → eager Room seat (ADR-0087).
2. API ≥37: often **seat + high-pri Resume notification**, not silent continuous
   play without WIU (ADR-0088 / 0101).
3. Opening app rebinds session / FGS / stream URLs — matches “works after open”
   when poison + background limits stack.

## Edge-Case Agent (Caveman)

| Edge | Observed / expected |
|------|---------------------|
| Poison write race after removeCached | Yes — `iYV9Kb…` 182B after skip |
| Pinned poison cannot wipe | `refused_pinned` on 1.8.0; 1.9.0 heals |
| Process death + A2DP | Seat via resumption; FGS deny → Resume notif |
| API 37 no WIU | Autoplay unreliable; HU Play / notif tap |
| SELECTED + null MAC | Resume notif only (ADR-0101) |
| Casting | Skip BT autoplay (not in this capture) |
| Network drop mid-stream | Not proven here (`reachable=true`) |
| lastDeltaSyncMs=0 | Stale catalog risk; orthogonal to Source skip |

## Performance Agent (Caveman)

| Issue | Impact |
|-------|--------|
| Full-queue enqueue every transition | Log + Room churn; CPU spike on skip storm |
| Twin promote per track | Dup work until dedup short-circuit |
| Skip storm × N tracks | Exo prepare thrash; battery; AVRCP flicker |
| 1.9.0 circuit stop | Caps systemic thrash after 3 Source errors / 15s |

## Coverage Agent (Caveman)

Ship gate already covered on main (≥80% units for poison path):

- `AudioCacheValidationTest` — 182B JSON / min size / magic
- `CacheServiceTest` — write reject, `removeCached` refuse / heal pin-poison
- `DownloadManagerTest` — `already_download`, promote dedup
- `PlaybackErrorRecoveryTest` / `SourceErrorCircuitTest` — SKIP_NEXT / CIRCUIT_STOP
- BT: `BtConnectionReceiver*` / policy / starter tests (see BT_* behavior reports)

Gap: no instrumented full `MediaService.onPlayerError` coroutine on device; pure
helpers + unit mocks cover decision tree.

## Design Impact Agent (Caveman)

No Compose UI change in this analysis. UX heuristics for Settings already in
BT resume section: clear enabled toggle, any vs selected, device picker,
battery/notification copy. **Do not** add “auto-open app on BT” control —
false promise under BAL.

---

## Auto-open Activity on specific BT device?

**Answer: No — not a dependable product feature.**

| Approach | Why blocked / weak |
|----------|-------------------|
| `startActivity(MainActivity)` from A2DP receiver | Background Activity Launch (BAL) |
| Companion Device Manager | Association ≠ BAL exemption for car MAC |
| Full-screen intent on connect | OEM-fragile; car HU ignores phone FSI |
| Force default music role | Soft routing only; no autoplay guarantee |

**Correct path (already in app):** service-first media session.

```
A2DP CONNECTED
  → BtResumePolicy (enabled, any|selected, debounce, !casting)
  → BtAutoplayStarter → MediaService ACTION_BT_AUTOPLAY
  → early FGS + sync seatQueueForBtSync (Room)
  → API ≤36: play(); API ≥37: seat + Resume notif (WIU)
  → FGS denied: high-pri Resume notif (PendingIntent → FGS)
  → HU / media key Play → onPlaybackResumption if needed
```

### Settings checklist (Pixel 8 Pro / Android 17)

1. **ftpMusic → Settings → Bluetooth resume:** ON.
2. Mode **Selected** + add car/headset MAC (or **Any** if you accept headphones).
3. Grant **Nearby devices / BLUETOOTH_CONNECT** (SELECTED MAC match).
4. Grant **Notifications** (`POST_NOTIFICATIONS`) — Resume notif is WIU path.
5. App battery: **Unrestricted** (Settings → Apps → ftpMusic → Battery).
6. Optional: set ftpMusic as default music / media app (OS default-apps).
7. On connect: if silent, tap **Resume** notif or press Play on HU — do not expect
   full UI to auto-appear.

**Not implemented / will not ship:** BT MAC → auto-launch `MainActivity`.

---

## Post-1.9.0 verification checklist

Install/build **1.9.0 (versionCode 13)** from current main, then:

### A. Poison gate (required)

Share diagnostics after a prefetch/play session. Confirm:

- [ ] Header `version=1.9.0` (or newer)
- [ ] **No** success line `writeCached … bytes=<4096` without `reject`
- [ ] Poison attempts log `writeCached reject … reason=non_audio` (or worker retry, never `completed` on poison)
- [ ] Source-error storms either heal (`healPoison` / `heal_poison_pinned`) or hit `CIRCUIT_STOP` instead of wiping whole queue
- [ ] `refused_pinned` only for **healthy** pinned downloads; poison pins clear via heal

### B. BT screen-off (required if “dies until open app” persists)

Reproduce: queue seated, screen off, A2DP reconnect, do **not** open UI.

Capture must include `ftpmusic-bt` lines. Confirm:

- [ ] A2DP → policy allow → `ACTION_BT_AUTOPLAY` / seat logs
- [ ] API 37: Resume notif posted when silent autoplay withheld
- [ ] HU Play or notif tap resumes without opening Settings/Home
- [ ] Mid-song die **absent** when cache playable (if still present → new ADR:
      stream URL / FGS network while backgrounded — out of scope until proven)

### C. Codebase confirmation (main @ analysis time)

| Check | Result |
|-------|--------|
| `versionName` / `versionCode` | `1.9.0` / `13` in `compose/app/build.gradle.kts` |
| Min audio bytes | `AudioCacheValidation.MIN_CACHED_AUDIO_BYTES = 4096` |
| Write gate | `writeCachedTrackFromFile` rejects non-`PLAYABLE` |
| Circuit | `SourceErrorCircuit` threshold 3 / window 15s |
| BT Activity launch | None in `BtConnectionReceiver` / `BtAutoplayStarter` |

---

## Remediation for this capture owner

1. Install **1.9.0** (or rebuild main).  
2. Play once / cold start so cache init heals leftover poison spans.  
3. Apply Settings checklist above for Pixel 8 Pro BT.  
4. Re-share diagnostics if mid-song BT failure remains **after** A+B pass.

## Out of scope

- Implement BT → `MainActivity` auto-open  
- Prefetch “next N only” refactor (perf nicety)  
- New ADR for background stream refresh until post-1.9.0 BT capture proves it  
