# ADR 0100 — 1.7.0 Post-Ship Remediation

Date: 2026-10-08
Status: Accepted
Related: ADR-0088, ADR-0094, ADR-0095, ADR-0096, ADR-0097, ADR-0092, ADR-0091

## Context

Ten commits landed after Play pack 1.6.0 (versionCode 10) without a version bump.
Deep review found wiring/UX honesty holes and stale PUSH unit tests after ADR-0094.

## Decision

1. **BT null MAC** — `handleConnectBroadcast` passes nullable `safeAddress` into
   `dispatchBtConnect` so ANY-mode resume works without `BLUETOOTH_CONNECT`.
2. **Cover Coil eviction** — `CoverArtImage` onError deletes non-image `file://`
   payloads via `CoverArtFiles.deleteIfNotImage` (parity with `ArtistAvatar`).
3. **AppHeader Cast** — `CastButton(enabled = interactive)` blocks collapsed ghost taps.
4. **Downloads stale** — row uses `downloadStatus = "none"` when file missing.
5. **AutoBrowse albums** — `playable` only when `songCount > 0` (playlist parity).
6. **PUSH tests** — stored `"push"` maps to ASK (ADR-0094); cover `pushContext` directly.
7. **Ship** — versionCode 11 / versionName 1.7.0 for Play internal testing.

## Consequences

- ANY-mode BT resume works on devices denying connect permission.
- Corrupt cover cache self-heals on next Coil error.
- Collapsed header cannot open Cast picker.
- Downloads UI does not claim downloaded when file gone.
- Empty Auto albums are browsable but not Play-all.
- Unit suite aligned with Settings overwrite triad (Ask/Clean only).
