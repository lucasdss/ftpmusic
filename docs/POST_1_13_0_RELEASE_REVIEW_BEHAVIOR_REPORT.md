# Post-1.13.0 release review — behavior report

**Scope:** `v1.13.0` → 1.13.1 ship  
**Date:** 2026-10-14

## Findings → fixes

| ID | Bug | Fix |
|----|-----|-----|
| P1a | Exhausted skip row deleted → trackless poison re-pends forever | Keep row; `getExhaustedIds`; exclude on DELTA; FULL/`force` re-arms |
| P1b | Empty `song[]` treated as success (streak reset) | Fail when `songCount > 0`; `songCount == 0` success + clear stale tracks |
| P1c | Soft-stop advanced FULL/DELTA watermarks | `syncAlbumTracks` returns false; cadence + `metadata_version` gated; phase stays != error |
| P2 | FGS `onTimeout` left sync Job running | `LibrarySyncService.onTimeout` → `MetadataSyncWorker.stop()` |
| Doc | ADR-0139 Decision said cooldown→retry | Aligned with amend: cooldown → success |
| Test | Branch test still expected `upsertAll` | Expect `upsertTracksPreserveCache` |

## Residuals (documented)

- Parallel soft-stop streak race under concurrent batches
- Genre densify page safety cap
- Cast JumpTo throw UX on Auto Queue seek

## Gates

- Unit tests + JaCoCo report
- ktlintCheck + detekt
- lintVitalRelease
- bundle-release (keystore when present)
