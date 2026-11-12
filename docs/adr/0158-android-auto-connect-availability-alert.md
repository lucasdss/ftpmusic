# ADR 0158 — Android Auto Connect Availability Alert

Date: 2026-11-12
Status: Accepted
Related: ADR-0146 (car media declaration), ADR-0071 / ADR-0154 (BT autoplay)
Behavior: `docs/ANDROID_AUTO_CONNECT_ALERT_BEHAVIOR_REPORT.md`

## Context

Drivers connecting a phone to a new Android Auto head unit may not know FTP
Music is browsable on the car. Peer intent (Substreamer-class): surface
availability when Auto attaches. Public Substreamer docs do not specify an
alert; we still ship the first-connect phone notice.

## Decision

1. Detect Auto via Media3 `MediaSession.isAutoCompanionController` /
   `isAutomotiveController` in `MediaSessionCallback.onConnect`.
2. Phone informational notification once per controller package fingerprint
   stored in SecureStorage; Settings toggle (default ON).
3. Do not conflate with Bluetooth A2DP autoplay.

## Consequences

- First Auto session educates the user; repeats stay quiet.
- Car App Library / in-car toasts remain out of scope (ADR-0146).
