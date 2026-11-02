# BT Connect + Auto-Play — Market Behavior Report

Date: 2026-10-11  
Status: research + hardening shipped (ADR-0119); updated by ADR-0148 (API ≥37 seat-only, no app Resume notif)  
Related: [ADR-0119](adr/0119-bt-connect-autoplay-market-hardening.md),
[ADR-0088](adr/0088-bt-resume-correctness-and-android17-wiu.md),
[ADR-0101](adr/0101-bt-a17-silent-wake-and-offlan-cache-seat.md),
[ADR-0087](adr/0087-media3-playback-resumption-and-bt-eager-restore.md),
[ADR-0072](adr/0072-bluetooth-resume-any-or-selected.md),
[BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md](BT_DEEP_SLEEP_MEDIA_RESUME_BEHAVIOR_REPORT.md)

## Verdict (Caveman)

FTP Music stack = Poweramp-style A2DP opt-in resume + Google Media3
resumption. Deep-sleep fail mostly OS policy (FGS deny, API≥37 WIU/BFSL),
not missing ExoPlayer API. Silent autoplay after sleep with zero gesture
**not** guaranteed by Android for third-party apps.

## Market patterns

| Pattern | Who | Deep-sleep role |
|---|---|---|
| MediaButton + `onPlaybackResumption` | Google Media3; Spotify-class | Primary cold wake; BT Play → FGS exemption |
| Opt-in resume on BT connect | Poweramp “Resume on Bluetooth” | A2DP broadcast → start player; off by default |
| HU AVRCP MEDIA_PLAY on connect | Many car HUs / ROMs | Works even if app resume-off; routes to last session |
| Stay last active media session | Spotify / YTM | System media keys prefer last player |
| Unrestricted battery | Poweramp guidance | Restores FGS-from-background exemption |
| Android Auto browse tree | Spotify / YTM | Separate surface; not classic A2DP autoplay |

## OS constraints (must respect)

1. **API ≥31 FGS background start** — denied → `ForegroundServiceStartNotAllowedException`
   unless exemption (notif tap, media button, battery unrestricted, etc.).
2. **API ≥37 WIU / AudioHardening** — A2DP-started FGS often BFSL without WIU;
   background `play()` may silence. App gate (ADR-0148): seat-only; HU /
   media-notif Play; Resume `1002` only for null-mac / FGS-deny.
3. **API ≥15 BOOT_COMPLETED** — cannot start `mediaPlayback` FGS from boot receiver.
4. **BAL** — no Activity launch from A2DP MAC alone.
5. **A2DP broadcast** — best-effort accelerator; not a Play-guaranteed autoplay API.

## Must-have permissions

| Permission | Role |
|---|---|
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | MediaSession FGS |
| `POST_NOTIFICATIONS` (runtime 33+) | Resume notif when silent play / FGS blocked |
| `WAKE_LOCK` | ExoPlayer network wake |
| `BLUETOOTH` (≤30) + `BLUETOOTH_CONNECT` (31+) | SELECTED allowlist MAC |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Stream / offline gate |

**Do not add for this feature:** `USE_FULL_SCREEN_INTENT`, Companion Device /
`connectedDevice` FGS, `SYSTEM_ALERT_WINDOW`, boot→mediaPlayback FGS.

## Must-use APIs

- `MediaSessionService` + `foregroundServiceType=mediaPlayback`
- `MediaButtonReceiver` + `MediaSession.Callback.onPlaybackResumption`
- Manifest A2DP `CONNECTION_STATE_CHANGED` → `startForegroundService`
- Early `startForeground` within start deadline
- High-pri notif `PendingIntent.getForegroundService` (WIU)
- Room-persisted queue + local metadata on resumption path
- Warm-only: `AudioManager.AudioDeviceCallback` (process alive)

## FTP Music call graph

```
A2DP CONNECTED → BtConnectionReceiver → BtResumePolicy
  → BtAutoplayStarter.startForegroundService(ACTION_BT_AUTOPLAY)
  → MediaService seat (playWhenReady=false)
  → SDK <37: play() + cancel Resume 1002
  → SDK ≥37: seat_only pause + cancel Resume 1002 (no app Resume)
  → offline no-cache / empty Room → cancel 1002 + info notif 1003 (open app)
SELECTED + null MAC → Resume notif 1002 (actionable)
FGS deny → Resume notif 1002
MEDIA_BUTTON → MediaButtonReceiver → onPlaybackResumption → play (WIU)
Warm service → AudioDeviceCallback A2DP add → same policy path
```

## Hardening shipped (ADR-0119)

1. Settings **Unrestricted battery** deep-link CTA + status.
2. Notification warning when resume ON and notifs disabled; toast on deny.
3. `BtResumeEvents` last-20 outcomes in Share diagnostics header.
4. Warm `BtWarmAudioDeviceMonitor` while MediaService alive.

## Device checklist

1. Long deep sleep → A2DP → logcat `ftpmusic-bt` + diagnostics `btResumeEvents`.
2. API ≤36: autoplay after seat. API ≥37: seat-only; Play on HU / media notif.
3. Battery unrestricted improves FGS start after Doze.
4. Notifs off → warning in Settings; HU Play still viable if seat succeeded.
5. SELECTED without CONNECT → Resume notif (`selected_null_mac`), no silent skip.
6. Empty / offline-no-cache → info `1003` only (stale Resume `1002` cancelled).

---

## Amendment — 2026-11-02 deep review & validation

Status: append-only. The body above is left as shipped (2026-10-11) and updated by ADR-0148; this section records what was re-verified, what is stale, and what the code actually does. **No app code was changed for this review.**

Scope: `/Users/lucasdss/workspace/ftpmusic-oss`. The review started at `7812bae`; the previously-dirty working tree was committed during the review as `c44bb20` (2026-11-02 11:54Z), so all line numbers below are from `c44bb20`. The Bluetooth decision files (`BtAutoplayStarter.kt`, `BtConnectionReceiver.kt`, `BtResumePolicy.kt`, `PlaybackResumptionMapper.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `BluetoothSettingsScreen.kt`) are identical between the two commits; the `MediaService.kt` BT gates are textually unchanged and only moved, so the findings hold at both revisions. Build config: `compileSdk 36`, `targetSdk 36`, `minSdk 26` (`compose/app/build.gradle.kts:23,48-49`).

Method:
1. Traced the A2DP → policy → starter → `MediaService` → seat/play path, plus the Media3 media-button/resumption path, in source at `c44bb20`.
2. Cross-checked ADR-0072, 0087, 0088, 0101, 0117, 0119, 0133, 0148 (including their supersede notes).
3. Re-read the Android 15 and Android 17 behavior-change pages and the FGS background-start restriction page on 2026-11-02.
4. Ran the playback unit-test package: 110 classes / 1,455 tests, 0 failures / 0 errors. The Gradle task still exited 1 because result XML could not be written for two unrelated Robolectric classes (`MediaSessionCallbackLibraryRootRobolectricTest`, `PlaybackNotificationProviderRobolectricTest`); no assertion failed.
5. Cross-model validation: DeepSeek plan + Phase-5 audit (`PASS WITH FIXES`), then an adversarial Mistral challenge. Mistral sustained F2/F3, the rejected-claim list, and E1–E11; its lone F1 dissent was internally inconsistent (it computed the promotion boolean as `true` but then denied the branch) and was resolved by an independent GPT-4o logic adjudication of the same lines. F1 stands.

### Verdict

The market thesis still holds: opt-in A2DP resume + Media3 `onPlaybackResumption` + FGS-fallback notification is the right architecture, and ADR-0148's seat-only happy path is directionally market-aligned. The shipped implementation is **not fully correct for Android 17**, for three concrete reasons:

1. **F1** — an FGS start denial leaves `btAutoplayRequested` armed, so a later unrelated app open can start playback on API ≤36.
2. **F3** — an A2DP connect/reconnect while audio is already playing pauses it on API ≥37.
3. Several platform statements in the body are imprecise (E1–E9).

The Resume-notification tap itself is **not** broken (see F2): `onStartCommand` re-arms the flags and the tap plays. At worst it produces a transient seat-only pause when F1 has forced the early synchronous seat.

### 1. Claims that were re-validated

| Claim | Verdict | Evidence |
|---|---|---|
| Dual path: A2DP receiver + Media3 resumption | Verified | `BtConnectionReceiver.kt:49-166`, `MediaSessionCallback.kt:482-528`, `AndroidManifest.xml:122-137` |
| A2DP policy is opt-in, A2DP-only, debounced, casting-wins | Verified | `BtResumePolicy.kt:37-90`, `BtConnectionReceiver.kt:85-145`, `BtResumeStorage.kt:7-28` |
| FGS fallback posts Resume 1002 on denial | Verified, with caveat F1 | `BtAutoplayStarter.kt:66-82`, `:126-170` |
| ADR-0148 happy path seats paused, no 1002 | Verified only when playback was idle/paused | `MediaService.kt:2825-2865` |
| Empty Room / offline-no-cache → info 1003 | Verified, with caveat F4 | `MediaService.kt:2592-2610`, `:2667-2686`, `:2790-2811` |
| Manifest permissions and service type | Verified | `AndroidManifest.xml:5-14`, `:58-67` |
| MediaButtonReceiver / `onPlaybackResumption` | Verified | `AndroidManifest.xml:122-128`, `MediaSessionCallback.kt:482-528` |
| Battery CTA, notification warning/toast, diagnostics events | Verified | `BluetoothSettingsScreen.kt:84-90`, `:123-152`; `DiagnosticLog.kt:134`; `BtResumeEvents.kt:49-57` |

### 2. Errata (doc text corrections)

**E1 — line 37: "API ≥15 BOOT_COMPLETED".** In an API-numbered list this reads as API level 15 (Android 4.0.3), which is wrong. The restriction is **Android 15 / API 35+, for apps targeting 35+**; `mediaPlayback` is one of the restricted FGS types. This app targets 36, so the rule would apply, but the app declares no `BOOT_COMPLETED` receiver. Correct wording: "Android 15 (API 35) and targetSdk ≥ 35 — `BOOT_COMPLETED` receivers cannot start a `mediaPlayback` FGS; not used here."

**E2 — lines 15 and 34: the Android 17 WIU claim is over-broad.** The Android 17 page defines two different rules:
- Base rule (all apps running on Android 17, **regardless of targetSdk**): background audio playback / focus / volume changes require a visible activity or a foreground service that is not `SHORT_SERVICE`. FTP Music's `mediaPlayback` FGS satisfies this while it is running.
- WIU extra rule (**only apps targeting API 37**): the background FGS must have while-in-use capability.

The app targets 36. The WIU extra therefore does **not** bind this build today; the code's `sdkInt >= 37` seat-only gate (`PlaybackResumptionMapper.kt:31`) is a deliberate product/forward-compat choice, not a platform mandate. `play()` can still be silenced by the **base** rule when no FGS is running (or when the FGS is `SHORT_SERVICE`, which this one is not), so the gate remains sensible. Keep the gate; fix the rationale.

**E3 — lines 36, 60, 71: 1002 usage is conditional, not "must-use".** `postResumeNotification` is still shipped (`BtAutoplayStarter.kt:126-170`; `PendingIntent.getForegroundService` at `:149-153`; high priority at `:158-159`) but after ADR-0148 it is reached only from FGS denial (`BtAutoplayStarter.kt:80`) and `selected_null_mac` (`BtConnectionReceiver.kt:119`). The happy path seats paused and cancels 1002 (`MediaService.kt:2838-2839`, `:2854-2855`). Reword line 60 as "retained for FGS-deny + `selected_null_mac`", not as a happy-path requirement.

**E4 — line 32: the FGS background-start rule also has a targetSdk clause.** It applies to apps **targeting** API 31+ running on API 31+, not merely to devices at API 31+. The app targets 36, so it applies; the wording is still incomplete.

**E5 — line 38: spell out BAL.** BAL here means **Background Activity Launch**, not a Bluetooth audio profile. The code does not launch an Activity from the A2DP receiver; the only `MainActivity` launch is the informational-notification content intent, which is a user tap (`BtAutoplayStarter.kt:191-195`).

**E6 — lines 39 and 89: A2DP is not an FGS-start exemption, and not a BAL exemption.** The official background-start exemption list does not include Bluetooth A2DP connection broadcasts. Therefore the cold A2DP `startForegroundService` succeeds only when another exemption applies (most commonly battery optimization turned off, or the app is effectively foreground), otherwise it throws and takes the 1002 path. Line 89 should read "API ≤36 autoplay after seat **when the FGS start is permitted**"; otherwise FGS denial is expected, not exceptional. This also reinforces line 39's "best-effort accelerator" wording.

**E7 — line 82: scope of "toast on deny".** The toast is real, but it is the **POST_NOTIFICATIONS runtime-permission denial toast shown while enabling the feature** (`BluetoothSettingsScreen.kt:84-90`). There is no toast on an FGS start denial; that path is log + diagnostics + 1002. Clarify to avoid implying runtime FGS feedback.

**E8 — line 92: "SELECTED without CONNECT … no silent skip" needs a precondition.** The actionable 1002 is posted only when the allowlist is non-empty (`BtConnectionReceiver.kt:90-95`). SELECTED + empty allowlist is a deliberate no-op (`BtResumePolicy.kt:62`; skip recorded at `BtConnectionReceiver.kt:131-144`). Say "SELECTED with at least one allowlisted device".

**E9 — lines 46 and 91: the app-level notification check misses a muted channel.** `notificationsEnabled()` uses `NotificationManagerCompat.areNotificationsEnabled()` (`BtAutoplayStarter.kt:116-117`), which is app-level only. If the user mutes the `ftpmusic_bt_resume` / `ftpmusic_bt_info` channel (importance NONE), the check passes and `notify()` is a silent no-op (`:169`, `:205`), while telemetry records a post. Add this to the "notifs off" edge case.

**E10 — lines 4-9: supersede matrix missing.** ADR-0148 supersedes parts of ADR-0088, 0101, 0117, 0119 and 0133. A reader following the "Related" list cannot tell which document is authoritative. Add a one-line supersede matrix.

**E11 — line 93: info 1003 is never cancelled on a later successful play.** `cancelResumeNotification` cancels only 1002 (`BtAutoplayStarter.kt:120-124`); no code cancels `INFO_NOTIFICATION_ID` (only the constant at `:25`, the `PendingIntent` at `:195`, and `notify()` at `:205`). An "Nothing to resume" / "No offline tracks" notification can therefore outlive a later successful session until it is tapped or dismissed. Low severity, but the checklist's "info 1003 only" should say it is not lifecycle-coupled.

### 3. New behavioral findings (code vs. report)

#### F1 — Blocker: FGS denial leaves `btAutoplayRequested` armed

`startAutoplay` sets `btAutoplayRequested = true` and `foregroundRequested = true` before the start (`BtAutoplayStarter.kt:43-51`). The catch resets **only** `foregroundRequested` (`:67`); it does not call `clearBtAutoplayFlags()`. After `ForegroundServiceStartNotAllowedException` — or any other start failure, since the catch is a broad `Exception` — the static `btAutoplayRequested` stays true.

Consequence: a later unrelated `MainActivity` open sets `backgroundWarmStart = true` and starts `ACTION_INITIALIZE` (`MainActivity.kt:55-58`). `MediaService.onCreate` then sees the stale flag, promotes the FGS (`MediaService.kt:1949-1965`), takes the synchronous `seatQueueForBtSync()` branch (`:2091-2092`), and calls `applyBtPlayAfterSeat` (`:2712-2755`). On API ≤36, when a saved queue exists, that is a `play()` (`PlaybackResumptionMapper.kt:37-38`, `MediaService.kt:2825-2849`). The user can get music started merely by opening the app after a denied A2DP wake. The report's line 74 ("FGS deny → Resume notif 1002") describes the notification but not this latent state.

Secondary variant: if the tap/seat happens while Room is empty, the empty path clears the flags and posts 1003 (`MediaService.kt:2592-2610`), but the subsequent `onStartCommand` re-arms both flags (`:2531-2543`) and `handleBtAutoplayIfNeeded` returns with `mediaItemCount == 0` **without clearing them** (`:2883-2892`). The stale pair then survives until a restore with a non-empty queue, at which point a later app open can also `play()` because `userInitiatedResume` is still true. This is an F1 edge case, not a separate defect.

#### F2 — Should: Resume-notification tap does an unnecessary seat-only pass, but still plays

This corrects an earlier draft of this review that called the tap path broken. Android calls `Service.onCreate()` before `onStartCommand()`. In the scenario that matters — FGS was denied, the receiver process is still alive, the service was never created — the notification tap starts the service with `ACTION_BT_AUTOPLAY` + `EXTRA_USER_INITIATED_RESUME=true` (`BtAutoplayStarter.kt:143-153`). At `c44bb20`:

1. `onCreate` runs first (`MediaService.kt:1938`); F1 has left `btAutoplayRequested = true`, so it calls `seatQueueForBtSync()` (`:2091-2092`).
2. `applyRestoredQueue` reads `userInitiatedResume` at `:2749` **before** `onStartCommand` has parsed the extra; the value is false. It clears both flags at `:2750` and calls `applyBtPlayAfterSeat(userInitiated=false)` at `:2755`.
3. On API ≥37 that takes the seat-only branch: `pause()` and cancel 1002 (`:2850-2865`).
4. `onCreate` returns and `onStartCommand` runs (`:2529`). It **re-arms** `btAutoplayRequested` and `foregroundRequested` (`:2531-2533`), parses the extra and sets `userInitiatedResume = true` (`:2537-2543`), then dispatches `handleBtAutoplayIfNeeded()` (`:2549-2551`).
5. `handleBtAutoplayIfNeeded` now sees the re-armed flag, finds the seated player, reads `tapResume = true` (`:2894`), clears the flags and calls `applyBtPlayAfterSeat(userInitiated=true)`, which `play()`s (`:2838`).

Result: **playback starts**. The observable defect is a transient seat-only pause plus a `seat_only` telemetry record immediately before `tap_play`, not a persistent pause. The extra pass exists only because F1 forced the synchronous seat in `onCreate`; with F1 fixed, `onCreate` takes the async restore (`:2093-2094`) and the tap is applied once (`:2651`, `:2705`, `:2749`). The process-death variant also works because the statics reset and `onCreate` takes the async restore.

#### F3 — Blocker: an A2DP connect during active playback pauses it on API ≥37

`handleBtAutoplayIfNeeded` guards only `btAutoplayRequested` and casting (`MediaService.kt:2883-2897`); there is no `isPlaying` / `playWhenReady` check in the receiver, policy, warm monitor, or handler. On API ≥37 the non-tap branch unconditionally calls `player?.pause()` (`:2854`). Therefore any A2DP connect/reconnect while music is already playing — including a warm `AudioDeviceCallback` event (`BtWarmAudioDeviceMonitor.kt:38-55`) or a reconnect after the 5-second debounce — pauses playback. The report's "seat + paused" model assumes an idle session; it does not define the already-playing case. Market behavior is no-op. On API ≤36 the counterpart `play()` is a no-op if already playing (and intentionally resumes a user-paused queue per the opt-in feature).

#### F4 — Should: info 1003 is not cleared by a later successful session

See E11. This is a notification-lifecycle gap, not a playback failure.

#### F5 — Should: SELECTED + empty allowlist is silent

See E8. Intentional per ADR-0072, but the report's unconditional "no silent skip" is inaccurate.

#### F6 — Should: muted notification channels are invisible to the precondition check

See E9. App-level `areNotificationsEnabled()` is not sufficient on Android 8+ where per-channel importance can be NONE.

### 4. Claims checked and not sustained

These were raised during cross-model review and **rejected after tracing the code**. Recorded here so they are not re-litigated as open blockers:

- **"The 1002 tap can leave playback permanently paused on API ≥37."** Not sustained. `onStartCommand` re-arms the flags and dispatches `handleBtAutoplayIfNeeded`, which plays. The only residue is the transient seat-only pass described in F2. Assigning F2 "blocker" severity would overstate the shipped behavior and misdirect the fix.
- **"The manifest A2DP receiver and the warm `AudioDeviceCallback` double-dispatch the same connect and double-start/pause."** Not sustained. Both paths call `BtConnectionReceiver.dispatchBtConnect`, which shares the process-scoped `debounceMap` (`BtConnectionReceiver.kt:39`, `:97-101`, `BtResumePolicy.kt:68-88`). Deliveries are serialized on the main thread, so a single connect window collapses to one policy acceptance. A second connect after 5 s is a new user/device event, not a duplicate.
- **"Info 1003 persistence is a playback blocker."** Not sustained. It is a stale-notification issue (E11/F4); it does not affect seat or play.
- **"`goAsync()` leaks or outlives the receiver."** Not sustained. `handleConnectBroadcast` is synchronous and `pending.finish()` is in a `finally` (`BtConnectionReceiver.kt:24-34`). `goAsync()` is redundant here, not a lifetime bug.

### 5. Validated platform matrix

| Platform rule | Device condition | targetSdk condition | Effect | Applies to this build? |
|---|---|---|---|---|
| FGS background-start restriction | API 31+ | 31+ | `ForegroundServiceStartNotAllowedException` unless exempt | Yes; A2DP is **not** an exemption; battery-unrestricted/visible is the practical escape |
| `BOOT_COMPLETED` FGS restriction | Android 15 / API 35+ | 35+ | `mediaPlayback` FGS cannot start from boot receiver | Yes by targetSdk, but no boot receiver is declared |
| Android 17 base background-audio rule | API 37 | all apps | visible activity or non-`SHORT_SERVICE` FGS required | Satisfied while the `mediaPlayback` FGS runs |
| Android 17 WIU extra rule | API 37 | 37 | background FGS must have WIU | Not bound at targetSdk 36; code gate is conservative |
| A2DP broadcast as autoplay API | all | all | no FGS/BAL exemption; best-effort accelerator | Cold autoplay depends on another exemption; otherwise 1002 |

### 6. Test evidence and gaps

Passing (2026-11-02, playback package):
- `PlaybackResumptionMapperTest`: 12 tests, 0 failures — asserts API 36 vs 37 gate and tap override.
- `MediaServiceFgsReassertTest`: 13 tests, 0 failures — covers API-37 seat-only, offline-empty, and tap-play direct calls.
- `BtAutoplayStarterTest`: 3 JVM tests, 0 failures; `BtAutoplayStarterRobolectricTest`: 7 tests, 0 failures — start success and FGS fallback assert only `foregroundRequested == false` on failure (`BtAutoplayStarterTest.kt:76`); **no test asserts `btAutoplayRequested == false`**, so the F1 gap is encoded in the test.
- `BtConnectionReceiverTest`: 5 tests; `BtConnectionReceiverCompanionTest`: 6 tests; `BtResumePolicyTest`: 11 tests; all pass.
- Whole playback package: 110 classes / 1,455 tests, 0 failures / 0 errors.

Not covered:
- Failure-path reset of `btAutoplayRequested` (F1).
- `onCreate` vs `onStartCommand` extra-ordering (F2) — in particular, that the tap only plays after the second dispatch.
- A2DP connect while `player.isPlaying` (F3).
- Per-channel notification mute (F6).
- Process-death variants of the tap/seat path.
- `BroadcastReceiver.goAsync()` lifetime is redundant but harmless; no test needs it.

### 7. Spotify-parity verdict

The report's market claims are research reasoning, not measurements from Spotify/YTM builds. The parts that can be checked in this repo are the mechanisms: seat the media session, do not post an app-specific Resume notification on the happy path, and let the head unit / system media button drive `onPlaybackResumption`. Those are implemented. The parity claim is weakened by **F1** (a denied A2DP wake can later autoplay on app open) and **F3** (an A2DP connect can pause active playback); the phone-side 1002 tap itself still plays, but with a transient seat-only pass (F2). The head-unit Play path through MediaSession remains the strongest parity surface and is independent of the app's 1002 notification.

### 8. Recommended follow-ups (documentation-only in this change)

1. Patch F1 and F3 before claiming Android 17 parity in release notes; add regression tests for both (and a tap-ordering test for F2).
2. Apply errata E1–E11 to the body (or keep this amendment as the authoritative correction).
3. Add `fgs_denied_battery_optimized` / channel-mute telemetry to `BtResumeEvents` so the diagnostics header stops over-reporting notification delivery.
4. Revisit the `sdkInt >= 37` gate when `targetSdk` is raised to 37; at that point the WIU extra rule becomes binding and the current conservative gate becomes required.
5. Keep targetSdk 36 and re-run on-device validation on API 36 and API 37 hardware; the unit tests cannot prove FGS/WIU behavior.

**No code was modified by this review; all findings are static-analysis and test-run observations against `c44bb20`, re-validated against `7812bae` and the cited platform documentation.**

### Remediation — 2026-11-02 (android-orchestrator ship)

| Finding | Fix |
|---------|-----|
| F1 | `BtAutoplayStarter` catch calls `clearBtAutoplayFlags()`; Resume notif re-arms via `onStartCommand` + `EXTRA_USER_INITIATED_RESUME` |
| F3 | `handleBtAutoplayIfNeeded` / `applyBtPlayAfterSeat` record `skip_playing` and return when already playing (no pause) |
| G2 / tip / G3 / G6 | See [CAR_CONNECT_AUTOPLAY_SPOTIFY_PARITY_REVIEW.md](CAR_CONNECT_AUTOPLAY_SPOTIFY_PARITY_REVIEW.md) |

E1–E11 remain doc errata for a later body rewrite; not blockers for the F1/F3 ship.
