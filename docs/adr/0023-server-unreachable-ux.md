# ADR 0023 — Server-Unreachable UX & Stale-Proxy Config Handling

Date: 2026-08-25
Status: Accepted
Related: ADR 0033 (server config single source of truth)

## Context

The dead phone-local proxy (`127.0.0.1:9999`, previous implementation) survived as
the `DynamicBaseUrl` DEFAULT. Pre-splash API calls (ViewModel init) raced against it:
connection refused, silent `catch (_: Exception)` blocks swallowed the failures, and
the UI rendered stale cache as if healthy. Users saw endless loading with zero
feedback (on-device validation: the stored config was correct; the phantom proxy was
the code default, plus the hardcoded sentinel string in `MediaSessionCallback`).

## Decision

1. **Sentinel removal** — `DynamicBaseUrl.url` defaults to blank; `isConfigured()`
   gates all server work; the OkHttp `BaseUrlInterceptor` throws a typed
   `ServerNotConfiguredException` when unconfigured — requests NEVER reach a
   loopback or blank base. The hardcoded `"http://127.0.0.1:9999"` sentinel in
   `MediaSessionCallback` is replaced with `isConfigured()`.
2. **Visible failure over silent degradation** — Home and Library render a
   `ServerErrorBanner` when:
   - the configured URL is loopback (`isLoopbackUrl`: 127.0.0.1/localhost/0.0.0.0 —
     stale dev proxy) with an explicit "enter your real server URL" prompt, or
   - `ReachabilityStateHolder.isReachable` is false (the existing per-call
     API-layer tracking, previously only a subtle header badge).
   Banner has **Fix** → Settings→Server and a dismiss action.
3. **Warn, never block** — loopback URLs remain usable (adb-reverse dev setups are
   legitimate); the app warns instead of refusing.
4. **Offline suppression** — intentional offline mode never shows the banner.
5. **Bounded loading** — API fetches in `loadAlbums`/`loadArtists` are wrapped in
   `withTimeoutOrNull(30s)`; the full-screen spinner cannot stick beyond 30 s.

## Consequences

- A misconfigured server is now visible and actionable instead of a silent,
  apparently-infinite loading state.
- Blank/unconfigured config fails fast with a typed, testable error.
- The Settings server field warns inline while typing a loopback address.

- Test surface added: `DynamicBaseUrlTest`, `BaseUrlInterceptorTest`,
  `LibraryConfigWarningTest`, `ServerErrorBannerComposeTest`.
