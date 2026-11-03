# 0153 — Local Network permission on targetSdk 37+

Status: Accepted (readiness; not implemented while targetSdk = 36)  
Date: 2026-11-03  
Related: [PLAY_POLICY_2026_10_29_COMPLIANCE.md](../PLAY_POLICY_2026_10_29_COMPLIANCE.md),
[Android Local network permission](https://developer.android.com/privacy-and-security/local-network-permission),
Play restricted permissions guidance (Oct 29 2026 announcement)

## Context

Android 17 (API 37) blocks local-network traffic by default for apps that
target SDK 37+. Apps must declare and request `ACCESS_LOCAL_NETWORK` at runtime,
and Google Play requires a Console declaration justifying core functionality
when device pickers are insufficient. Enforcement: **August 25, 2027**.

FTP Music currently targets **36**. With `INTERNET`, local network access is
implicitly granted; adding `ACCESS_LOCAL_NETWORK` early is discouraged.

Core LAN surfaces:

1. **Typed Subsonic/Navidrome base URL** on a private LAN (including cleartext
   HTTP limited to private ranges).
2. **Google Cast / MediaTransfer** to speakers and TVs on the LAN.

NSD / Output Switcher pickers cannot replace (1). Play lists **Media casting**
as an approved declaration use case for (2).

## Decision

1. **Stay on `targetSdk` 36** until a deliberate API-37 bump. Do **not** add
   `ACCESS_LOCAL_NETWORK` to the manifest before that bump.
2. On the target-37 bump, in one change set:
   - Declare `android.permission.ACCESS_LOCAL_NETWORK`.
   - Request at runtime before first LAN connect attempt and before Cast device
     discovery that needs broad LAN access.
   - If the user denies: show a clear Settings/Server error that LAN servers and
     Cast discovery need Local Network; HTTPS/public servers may still work.
   - Prefer reusing an already-granted `NEARBY_DEVICES` group grant (e.g.
     `BLUETOOTH_CONNECT`) so users are not double-prompted when possible.
3. **Play Console declaration** (draft answers):
   - Features: Media casting; Other — connect to user-hosted music server on LAN.
   - Why pickers are insufficient: the server URL is user-entered (hostname/IP);
     there is no fixed discoverable device identity for every Navidrome install.
4. Keep the source-manifest regression guard forbidding premature
   `ACCESS_LOCAL_NETWORK` until this ADR’s implementation checklist is executed
   (update the guard allow-list in the same PR as the target-37 bump).

## Consequences

- No user-visible change on 2.1.0 / target 36.
- API-37 work is scoped and declarable; avoids shipping a restricted permission
  before Play’s declaration form is available (~June 2027 prompts).
- Cast-only picker redesign is rejected: it would break typed LAN servers.
