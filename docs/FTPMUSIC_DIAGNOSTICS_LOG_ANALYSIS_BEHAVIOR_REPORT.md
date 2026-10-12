# FTPMusic Diagnostics — Log Analysis Behavior Report

Date: 2026-10-11
Status: Analyzed, report-only. No code changed (user scope decision).
Source: `docs/logs/phone-logcat-full.txt` (23,258 lines, capture 09-26 09:57→14:23, no wipe).
Device build during capture: app `1.0.0` release, targetSdk 36. Pixel, Android 16-era system.
Prior analysis: `OUTSIDE_LAN_PLAY_HANG_BEHAVIOR_REPORT.md` + ADR 0043 — same capture, hang story. This report covers what that one missed.

## Scope

Re-analysis of capture with fresh eye. Signals outside prior report only. No code fixes in this pass.

## Verdict

| # | Signal | Count | Sev |
|---|---|---|---|
| F1 | Media notification update rate > 5/s → system sheds updates | 8 sheds | High |
| F2 | Double FGS start per play action (action intent + extras intent ~110ms apart) | 7 pairs | Medium |
| F3 | `MediaService` FGS runs 47m idle, no playback, then system stops it | 1 | Medium |
| F4 | `AppOps: attributionTag not declared in manifest` | 84 | Medium |
| F5 | Notification churn: `Cannot find enqueued record` id 1001 + 20938 | 12+ | Medium |
| F6 | Duplicate MediaRouter2 callback registration on background entry | 2 | Low |
| F7 | FGS type logger warnings | 21 | Low |

No crash. No ANR. No tombstone. App kill @13:28:01 = `remove task` (user swipe), not failure.

## Timeline (phone clock)

- **13:26:27–13:27:50** — Play/skip storm. 28 FGS starts, 30 MediaSession top pushes, 8 notification sheds. User churn because play hung off-LAN (prior report root cause).
- **13:28:01** — `Killing 16178 … remove task`. User swipes app.
- **13:28:03** — Cold start pid 18716 + `INITIALIZE_PLAYBACK` FGS, no user play action.
- **13:28–14:15** — Zero MediaSession pushes. No playback. FGS still alive.
- **13:37–13:39** — 14 more AppOps attributionTag errors (background media ops, no session churn).
- **14:15:45** — `Stopping service due to app idle: u0a625 -47m41s815ms .playback.MediaService`. System reaps idle FGS.
- **14:23:29** — Launcher relaunch. `INITIALIZE_PLAYBACK` FGS auto-starts again, no user play action.
- **14:23:49** — App backgrounded. MR2 duplicate-callback warnings.

## Findings

### F1 — Media notification update storm (High)

Evidence:

```
13:27:35.387 16178 W NotificationManager: Shedding notify (update) of NotificationKey[... id=1001], rate limit (5.0) exceeded: input 8.646951, output would be 6.0607266
13:26:39.627 1547 E NotificationService: Package enqueue rate is 5.223341. Shedding 0|com.lucasdss.ftpmusic.app|1001|null|10625
```

8 shed events, all id 1001 (media notification), 13:26:39–13:27:45. Input rate up to 8.6 updates/s during skip/error churn. Android caps app notification enqueues at ~5/s and drops overflow → media notification shows stale state (wrong track / wrong play-pause icon) during rapid skips. Root cause of "notification lies during skip storm" UX reports if any.

Fix direction: coalesce notification updates (dirty flag + ~250ms debounce window), never rebuild per player event. Not implemented this pass.

### F2 — Double FGS start per action (Medium)

Evidence — paired starts ~110ms apart:

```
13:26:36.561 FGS act=START_PLAYBACK
13:26:36.678 FGS (has extras)
13:26:37.346 FGS act=START_PLAYBACK
13:26:37.457 FGS (has extras)
```

7 such pairs. 28 total FGS starts in ~80s window: 16 `START_PLAYBACK`, 5 `INITIALIZE_PLAYBACK`, 7 extras-only. Every play tap fires two service start intents. Wasteful: two `onStartCommand` cycles, two `startForeground` paths, doubles F1 pressure. Fix direction: single intent carry action + extras; dedupe when service running (bind or `onStartCommand` reuse). Not implemented this pass.

### F3 — Idle FGS holds foreground 47 minutes (Medium)

Evidence:

```
13:28:03 FGS INITIALIZE_PLAYBACK (cold start, no play action)
  ... zero MediaSessionStack pushes for 47 minutes ...
14:15:45 W ActivityManager: Stopping service due to app idle: u0a625 -47m41s815ms .playback.MediaService
```

`INITIALIZE_PLAYBACK` on app cold start promotes `MediaService` to foreground and keeps it there with no playback for 47m41s until system reaps it as idle. Cost: battery, notification clutter, FGS-policy scrutiny risk on OEM builds. Repeats at 14:23:29 relaunch. Fix direction: init without FGS promotion; promote only on actual play, demote on pause/stop. Not implemented this pass.

### F4 — AppOps attributionTag missing (Medium)

Evidence:

```
E AppOps : attributionTag  not declared in manifest of com.lucasdss.ftpmusic.app
```

84 occurrences, 13:26–13:39, system-side. Media/notification AppOps calls carry no attribution tag the manifest declares. Android 14+ expects per-attribution tags (`android:attributionTags` style declarations / `AttributionSource` usage from media session + notification manager paths). Harmless today but blocks future per-attribution privacy dashboards and pollutes system log. Fix direction: declare tags for media session + media notification code paths. Not implemented this pass.

### F5 — Notification enqueue/cancel race (Medium)

Evidence:

```
I NotificationService: Cannot find enqueued record for key: 0|com.lucasdss.ftpmusic.app|1001|null|10625
I NotificationService: Cannot find enqueued record for key: 0|com.lucasdss.ftpmusic.app|20938|null|10625
```

12+ occurrences interleaved with sheds. App cancels/post/reposts faster than queue processes → system sees cancel for record already gone. Suggests cancel-then-notify pattern per update instead of notify(id, ...). Contributes to F1. Fix direction: update in place via same id, never cancel-then-post for media notif. Not implemented this pass.

### F6 — MediaRouter2 duplicate callbacks (Low)

Evidence:

```
14:23:49.933 18716 W MR2 : [local] registerTransferCallback: Ignoring the same callback
14:23:49.933 18716 W MR2 : [local] registerControllerCallback: Ignoring the same callback
```

On background entry app re-registers MR2 callbacks already registered. System dedupes, but registration lifecycle leak risk (register without matching unregister across config changes). Fix direction: audit register/unregister pairing in cast/media router glue. Not implemented this pass.

### F7 — FGS type logger warnings (Low)

```
W ForegroundServiceTypeLoggerModule: Logger should be tracking FGS types correctly for UID 10625 in package com.lucasdss.ftpmusic.app
```

21 occurrences, clustered on each `startForeground`. Type tracking off — typically FGS declared type vs `startForeground(type)` mismatch. Cosmetic; verify `mediaPlayback` type passed explicitly. Not implemented this pass.

## Known limitation

Release build strips all `ftpmusic-*` log tags (R8), so only 18 app-pid lines in buffer. All findings are system-side inference. Future forensics need either debuggable capture or app-side diagnostics ring buffer shipped in release (see `LOGS_CAPTURE_SHARE_BEHAVIOR_REPORT.md`).

## Relation to prior report

`OUTSIDE_LAN_PLAY_HANG_BEHAVIOR_REPORT.md` locked the 13:26–13:28 storm root cause (server unreachable off-LAN → skip churn → force close; ADR 0043 fix shipped). This report confirms the same timeline and adds hygiene findings F1–F7, all independent of the hang bug and all still unfixed as of capture. Fix priority: F1+F5 together, F2 next, F3 after, F4/F6/F7 opportunistic.

## Open questions

1. Is `INITIALIZE_PLAYBACK` FGS-on-cold-start intentional (fast-play optimization) or accident? ADR-worthy if changed.
2. Notification id 20938: which surface (downloads? error)? Not identifiable from system log alone.
3. Do F1/F2 reproduce on v1.10.0? Capture predates 9 releases. Fresh logcat recommended before fixing.
