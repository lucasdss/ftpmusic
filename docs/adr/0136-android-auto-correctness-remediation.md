# ADR 0136 — Android Auto Correctness Remediation

Date: 2026-10-14
Status: Accepted
Related: ADR-0131 (peer parity), ADR-0132 (hardening), ADR-0087 (resumption)

## Context

Post-1.13.0 deep review of the Auto stack found correctness holes that are
not market-feature gaps: radio mediaId prefix skew, playlist/mix page
offset vs dangling over-fetch, LocalOnly still calling `search3`, and
process-death radio identity loss on resumption.

Peer-parity browse (9 nodes + search) stays shipped. Play Next/Add
(ADR-0137) and dual-band Queue browse (ADR-0138) shipped in a later loop.
Discovery / AAOS remain deferred.

## Decision

1. **Radio id unity** — `decideErrorSkipAction`, `findNextCachedIndex`, and
   `resolveOfflineStartIndex` use `AutoBrowseIds.isRadioMediaId` (both
   `auto_radio:` and `radio:`).
2. **Playable paging** — playlist/mix Auto children page by playable
   track index after filtering Room-present ids (not raw entry offset).
3. **LocalOnly search** — in-Auto `runSearch` and voice
   `searchAndNavidromeExpand` skip `search3` when
   `LocalOnlyPolicy.isLocalOnly` (wired via `OfflineModeManager`).
4. **Radio resumption** — `PlaybackResumptionMapper` keeps raw stream URL
   and `type=radio` for radio media ids (no Subsonic `migrateStreamUrl`).

## Consequences

- Auto live-radio errors no longer SKIP_NEXT as if they were music tracks.
- Auto playlist/mix pages do not duplicate/skip after dangling fills.
- Offline/local-only Auto/voice search stays Room-only.
- Cold resume of Dual-adopted radio keeps stream identity.
- Market gap map lives in
  `docs/ANDROID_AUTO_CORRECTNESS_REVIEW_BEHAVIOR_REPORT.md` (doc-only).
