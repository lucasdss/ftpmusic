# Today Changes Deep Review — Behavior Report

Date: 2026-10-04  
Diff: `18b459b...HEAD` (+ review hardening fixes)

## Commits reviewed

| SHA | Subject |
|-----|---------|
| `85d6b50` | fix(playback): bind mediaId to stream URI; atomic restore start |
| `a0f37b5` | test(playback): lift PlaybackManager branch coverage past 80% |
| `d756806` | fix(playback): persist autoplay; async cast restore |
| `03fcb7a` | fix(playback): Dual persist SoT; Cast clear; playStream queue |
| `b194cc8` | fix(sync): await WM job; fail-closed FULL; honest watermarks |
| `b3131b5` | feat(ui): albums-first artist detail; unify spacing |
| `53953c7` | feat(ui): player surfaces UX parity for NP, mini, queue |
| `73b0372` | feat(car-bt): resume on allowlisted Bluetooth; claim system media |
| `f7f0ea4` | feat(bt): generic A2DP resume — any or selected devices |

## Edge-Case Agent

| Finding | Verdict |
|---------|---------|
| Cast remote=false double restore/seek after ADR-0066 | **BUG → fixed**: sole path `switchToLocalPlayback` / `applySwitchToLocalRestore`; session-kill now `savePositionOnly` before swap |
| Album mid-page fail keep-cache then `phase=complete` | **BUG → fixed**: `AlbumListIncompleteException` → catch → `phase=error`, no watermarks |
| BT enable before CONNECT grant | **BUG → fixed**: API 31+ enable only in permission callback |
| SELECTED + empty allowlist no-op | Spec OK (ADR-0072). Keep. |
| Cast wins over BT resume | Spec OK (ADR-0072). Keep. |
| Debounce stamped before FGS start | Acceptable; 5s window; notif path OK |

## Performance Agent

| Finding | Verdict |
|---------|---------|
| Artist tracks `itemsIndexed` no key | **Fixed** (`track.id`); similar artists keyed by mbid/name |
| Sync N+1 Room / BT receiver main | Deferred (hardening backlog) |

## Spec / Standards

| Finding | Verdict |
|---------|---------|
| QueueScreen hardcoded "Next from" | **Fixed** → `strings_player.xml` |
| PlayerBar lyrics EN + grey soup | **Fixed** → stringResource + `NavUnselected` |
| ADR-0054 hex tokens elsewhere | Deferred (not behavioral) |

## Coverage Agent

Gate: touched non-Compose logic ≥80% line/branch.

- New: `syncNow skips watermarks when album list incomplete mid-page`
- Updated: `syncAlbums FULL/DELTA` mid-page fail asserts `AlbumListIncompleteException`
- Targeted suites: MetadataSyncWorker*, MediaService*, Bt*, PlayerSurfaces*, SettingsViewModel* — **green**
- `assembleDebug` — **green**; `ktlintCheck` — **green**
- `detekt` — pre-existing `FunctionOnlyReturningConstant` on `DefaultMusicRoleHelper` /
  `CastSessionResumePolicy` / `TypographyPolicy` (not introduced by this hardening pass)

## Hardening this pass

1. `MediaService` session-kill: save pos + drop delayed restore/seek
2. `MetadataSyncWorker` incomplete album list aborts sync
3. Player copy localization (QueueScreen + lyrics overlay)
4. Settings BT CONNECT-gated enable
5. ArtistDetail LazyColumn keys

## Intentionally not fixing

- SELECTED + empty allowlist (ADR-0072)
- BT resume while casting (ADR-0072)
- Broad hex→token sweep, sync N+1, receiver `goAsync`
