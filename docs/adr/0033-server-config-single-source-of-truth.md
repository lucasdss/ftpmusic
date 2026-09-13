# 0033 — Server Config Single Source of Truth

Status: Accepted
Date: 2026-09-10

## Context

Server config (url / username / password) drives every network call, stream URL,
cover-art URL and worker guard. It lived in process-global `@Volatile` vars
(`DynamicBaseUrl`, `SubsonicCredentials`) written by 3 independent restore paths:

- `NetworkModule.provideRetrofit` at DI construction.
- `NavHost` splash `LaunchedEffect` (skipped when the task/nav state restored).
- `MediaService.onCreate` -> `restoreCredentials()` (called before
  `super.onCreate()`, so Hilt-injected `secureStorage` was uninitialized; the
  lateinit throw was swallowed -> silent no-op).

`SecureStorage.get` collapsed every failure to `null`, so a transient keystore
read failure silently produced an empty config. Compose read the globals inside
`remember(coverArtId, size)`, so a null result stuck for the composition's life.
Symptom: blank covers on remote-only surfaces; local-first surfaces fine; a
fresh nav graph fixed it.

## Decision

1. `ServerConfigStore` (Hilt `@Singleton`) is the only writer of server config.
   It publishes to:
   - `DynamicBaseUrl` / `SubsonicCredentials` (legacy sync readers: OkHttp
     interceptors, workers, playback).
   - `ServerConfigState` — a Compose snapshot state for UI.
   - `state: StateFlow<ServerConfig>` for non-Compose observers.
2. `Application.onCreate` restores config before workers/UI. Splash and
   MediaService call `initialize(force = true)` as self-heal hooks (after
   `super.onCreate()` for the service).
3. `SecureStorage` exposes typed `SecretRead` (`Ok` / `Missing` / `Failed`).
   A `Failed` read NEVER clears working in-memory config; the store stays
   uninitialized so the next caller retries. `Missing` legitimately clears
   (fresh install / logout).
4. Writes are atomic + durable: `SecretStore.putAll()` writes all keys in one
   `commit()` transaction.
5. Compose URL resolution reads `ServerConfigState`, so config arriving after
   first composition recomposes and rebuilds URLs.
6. Cover-art resolution is local-first on all surfaces; `CoverArtImage` retries
   once via the external fallback service on load error.
7. Cache files are written atomically (temp + validate + rename) and validated
   before trust (content type + magic bytes; full decode off-main).

## Consequences

- One restore authority; no last-writer-wins races between 3 paths.
- Transient storage failures are survivable and observable (diagnostic logs).
- UI recovers without process/nav restart when config arrives late.
- `di/**` is excluded from the JaCoCo report by project convention; the store is
  covered by unit tests in `StoredServerConfigurationTest`.
- Follow-up: prefer-iTunes pref is still read per-item from `SecureStorage`
  (pre-existing); hoist to a process-level observable when touched next.
