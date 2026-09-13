# ADR 0001 — Remove Remote Library Management (yt-dlp)

- Status: Accepted
- Date: 2026-08-28 (v1.0.0, first Google Play release)
- Deciders: Release owner, App architect
- Context: [REMOTE_LIBRARY_REMOVAL_BEHAVIOR_REPORT.md](../REMOTE_LIBRARY_REMOVAL_BEHAVIOR_REPORT.md)

## Decision

Remove the Remote Library Management feature (search + download of remote media
through a yt-dlp-backed server, exposed as "Manage Remote Library" in Settings)
from the app before the first Google Play submission. The feature is deleted
entirely — no flag, no hidden route, no leftover bindings.

## Why (rationale)

1. **Play content policy.** The feature lets users search arbitrary web content
   (incl. YouTube) and download it via a companion yt-dlp server. YouTube ToS
   and Google Play's copyright/streaming-download policies make this a
   first-review rejection risk for an app whose primary purpose is streaming a
   user's own Subsonic/Navidrome library. Removing it de-risks the store
   listing.
2. **Surface area.** ~1,400 lines (UI + VM + Retrofit API + auth interceptor +
   DI + stored credentials) whose only consumer was the removed feature.
3. **Secrets hygiene.** The feature stored user-configured header credentials
   in EncryptedSharedPreferences; unreachable code must not keep live secrets.
4. **First-release scope.** v1.0.0 is the store debut; shipping one clean
   feature set beats carrying a policy-risk feature.

## Consequences

- Settings no longer shows the entry; navigation route `remoteLibrary` gone.
- `SecureStorage` drops 7 remote-source/legacy keys and 3 migration helpers;
  adds `removeRemoteSourceKeys()` purged once at `FtpmusicApp.onCreate`.
- No Room schema change (DB stays v46). Offline-cache downloads (Subsonic
  offline mode) are unaffected.
- Tests for removed code deleted; `SettingsScreenComposeTest` asserts the row
  is absent; `SecureStorageInstanceTest` covers the purge.
- ProGuard rule `-keep class com.lucasdss.ftpmusic.app.data.network.**` kept
  (still serves Subsonic/Last.fm/MusicBrainz models).

## Alternatives considered

- **Hide behind a flag** — rejected: Play reviewers can find disabled-but-shipped
  code paths; dead code still needs maintenance and still stores credentials.
- **Keep for "power users"** — rejected: policy risk outweighs utility for a
  first release; feature can be reintroduced post-launch if desired.
