# ADR 0048 — In-app Diagnostics Export

Date: 2026-09-28
Status: Accepted
Related: Play release 1.1.0

## Context

Release R8 strips `android.util.Log.d/v` via `assumenosideeffects`. Play
testers cannot attach logcat from a production AAB. Crashlytics/Sentry are out
of scope for this OSS client.

## Decision

1. Keep a process-local ring buffer (`DiagnosticLog`, ~500 lines) with
   `d/w/e` breadcrumbs on sync, scrobble, reachability, and playback.
2. Settings → About / Diagnostics: show `versionName (versionCode)`,
   **Share diagnostics** (`ACTION_SEND` text/plain), **Clear log**.
3. Snapshot header includes version, SDK, model, offline/reachable flags, and
   last sync timestamps — no passwords/tokens.
4. Optionally mirror to `Log` when `BuildConfig.DEBUG` only.

## Consequences

- Testers can export breadcrumbs without network upload or third-party SDKs.
- Buffer is lost on process death (acceptable for Play testing loops).
- No FileProvider required for v1 (share via `EXTRA_TEXT`).
