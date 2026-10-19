# ADR 0149 — Settings IA: grouped nav hub + searchable leaves

Date: 2026-10-19
Status: Accepted
Related: ADR-0056 (settings stack), ADR-0019 (FGS / notifications), ADR-0103 (typography subpage),
ADR-0105 (cellular / sync), ADR-0072 (Bluetooth resume)

## Context

Root Settings was a single 2,275-line scroll of ~24 sections. Market apps (YTM, Spotify, Symfonium)
use a short grouped navigation list with dedicated sub-pages. Shared UI primitives already lived in
`ui/components/SettingsComponents.kt` (Phase 0).

## Decision

1. **Root hub** (`SettingsScreen`) is nav-only: search field + grouped `LazyColumn` of
   `SettingsNavRow`s driven by `SettingsIndex` / `SettingNode` (titleRes, route, icon, keywords,
   summaryProvider).
2. **Routes:** new leaves under `settings/{server,playback,bluetooth,cast,storage,network,
   appearance,notifications,home,lastfm,advanced,about}`. Keep existing `downloads`, `profile`,
   `customMixes`, `settings/typography` path strings.
3. **Network & data:** one hub row → `settings/network` (cellular policy, Wi‑Fi-only sync, interval,
   TTL, Auto-Download Playlists, Resync/Rebuild, nav to `customMixes`). Storage Used stays on
   `settings/storage`.
4. **Notifications:** `SystemStatusRow` for OS permission + keep `KEY_PLAYBACK_NOTIFICATIONS`
   rich/minimal product toggle (not equivalent to OS permission; FGS still posts minimal when off).
5. **Battery / default music app:** `SystemStatusRow` wrappers; deep-link behavior unchanged.
6. **Advanced / developer:** Simulate Offline + Library Metrics under About; Custom HTTP headers on
   `settings/advanced`.
7. **ViewModel:** activity-scoped `SettingsViewModel` retained this PR (preserves
   `SettingsViewModelTest`). **D6 per-domain VM split deferred.**

## Consequences

- Compose tests relocate with leaves; hub asserts profile/typography tags and search.
- SecureStorage keys unchanged.
- Coverage: prefer package filter note / optional `jacocoSettingsCoverage` for
  `ui/settings` + `SettingsComponents` (≥80% aspirational; do not fail CI if global gate absent).
