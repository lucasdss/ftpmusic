# ADR 0018 — Play Store Release Pipeline & Remote-Import API Base Path

Date: 2026-08-19
Status: Accepted

> **Status note (2026-10-08):** verified on 1.7.0 (versionCode 11) — unit suite
> (`make test-report`) + `make quality` + `lintVitalRelease` +
> `make bundle-release` / `verify-native-symbols` required before upload.
> Release `ndk.debugSymbolLevel = SYMBOL_TABLE` embeds native symbol metadata
> when AGP can extract it; pre-stripped dependency `.so` (AndroidX
> graphics-path) may leave Play’s soft “upload debug symbols” warning until
> unstripped natives exist (gate PASSes with WARN). Prior: 1.6.0 / 10; 1.5.0 /
> 9 (2026-10-04); 1.4.2 / 8; 1.4.1 / 7; 1.4.0 / 6; 1.3.0 / 5.

## Context

The app had never produced a Play-ready artifact: `lintVitalRelease` failed on 5
`NewApi` errors (which were also runtime crashes on API 26–30), there was no release
signing configuration, `targetSdk 35` missed the Aug 31, 2026 requirement for new
apps (target 36), the remote-import feature referenced "YouTube"/"/api/yt/" in code
and UI copy (Play rejection risk + a hardcoded API path), and live credentials
committed in tests.

## Decision

1. **Release signing** — Play App Signing with an upload key. `keystore.properties`
   (gitignored, `compose/`) feeds a `release` `signingConfig`; when absent the build
   still runs but produces unsigned artifacts (CI-safe). `keystore.properties.example`
   documents the `upload` alias + keytool command. Version: 2 / 1.1.0.
2. **targetSdk 36** with explicit `enableEdgeToEdge()`; the 5 NewApi crash sites get
   API guards (exception matched by class *name* to avoid class-resolution crashes
   on old APIs; `Tile` label/subtitle gated to API 29+; `startActivityAndCollapse`
   overload gated to API 34).
3. **Remote-import API base path** — the hardcoded `/api/yt/` prefix is removed from
   the app source. `RemoteImportApi` endpoints are path-relative (`/search`,
   `/download`, `/jobs`); the configured **Server URL** supplies scheme+host and the
   new required **API base path** setting (placeholder `/your/api/`) supplies the
   path prefix. URL assembly is a pure function (`buildRemoteSourceRequestUrl`).
   Feature renamed: `RemoteImportApi`/`RemoteSourceAuthInterceptor`, `KEY_REMOTE_SOURCE_*`
   storage keys (with one-time legacy reads of the old `yt_*` keys); all user-facing
   copy neutralized ("remote library", no "YouTube"/"download" wording).
4. **Security** — OkHttp logging debug-only (and stripped from release via R8);
   the remote-source `hostnameVerifier` bypass removed; `PlaybackProxy` binds
   loopback by default, LAN binding only for the explicit `castFromPhone` opt-in;
   `dataExtractionRules` + fixed `fullBackupContent` (encrypted credential prefs
   excluded); `POST_NOTIFICATIONS` requested at runtime from Settings; live
   credentials removed from tests (env-injected, skipped when absent).
5. **ProGuard** — `proguard-rules.pro`: netty keep rules (the embedded proxy uses
   plain-JAR Netty with reflection), `assumenosideeffects` Log stripping, Gson
   attribute keeps.
6. **Native debug symbols** — release `ndk { debugSymbolLevel = "SYMBOL_TABLE" }`
   so `bundleRelease` packages extractable native symbols for Play crash/ANR
   analysis ([native crash support](https://developer.android.com/studio/build/shrink-code#native-crash-support)).
   Every internal-testing cut must pass `make verify-native-symbols` (via
   `make bundle-release` or after a manual `bundleRelease`). Policy: FAIL if
   unstripped `.so` lack AAB/zip symbols; PASS+WARN if all natives are
   pre-stripped (current graphics-path case).

## Consequences

- `bundleRelease` now produces a signed, lint-clean AAB (verified).
- `make bundle-release` is the Play-pack path: build AAB then run
  `scripts/check-native-debug-symbols.sh`.
- Users must configure the API base path when setting up the remote library —
  requests otherwise hit the server root (logged; the UI validates it as required).
- Existing dev devices with `yt_*` settings migrate on first load.
- The Tier-3 cast LAN proxy remains unauthenticated on the local network (opt-in) —
  documented as accepted risk in the release checklist.
- Play Console account tasks (Data Safety, privacy policy URL, store listing,
  testing track) remain manual — see the checklist.
