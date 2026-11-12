# Android Auto Connect Availability Alert — Behavior Report

Date: 2026-11-12
ADR: `docs/adr/0158-android-auto-connect-availability-alert.md`

## Substreamer research (1D)

Public Substreamer sources (GitHub `ghenry22/substreamer`, release notes,
issues through 2026-10) were inspected for a car-connect “app available”
alert.

| Probe | Result |
|-------|--------|
| Release notes / README | Android Auto browse/playback supported; no connect-availability notif documented |
| Issues (#50, #70, #90, #297, media controls) | Auto/CarPlay UX discussion; no “available on this car” phone alert |
| Source keywords (`gearhead`, `isAutoCompanion`, connect toast) | Not found in public docs |

**Conclusion:** Substreamer does not publicly document a first-connect
availability notification. FTP Music implements the peer-intent UX the plan
specified: phone informational notice on **first** Android Auto companion
controller connect per fingerprint, with a Settings escape. Delta vs
Substreamer is intentional and documented here.

## FTP Music behavior

| Field | Value |
|-------|-------|
| Trigger | `MediaSession.isAutoCompanionController` (or automotive) on `onConnect` |
| Not A2DP | Separate from `BtConnectionReceiver` / Bluetooth auto play |
| Frequency | Once per controller package fingerprint until prefs cleared |
| Surface | Phone notification (DEFAULT channel); Car App Library out of scope |
| Settings | Notifications → “Android Auto available alert” (default ON) |
| Copy | Title: “FTP Music on Android Auto”; body: available on this car |

## Tester checklist

- [ ] First Auto connect → phone shows availability notice once
- [ ] Reconnect same car → no second notice
- [ ] Toggle OFF in Settings → no notice
- [ ] Bluetooth A2DP alone (no Auto) → no Auto availability notice
