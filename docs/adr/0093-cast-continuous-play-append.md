# ADR 0093 — Cast Continuous Play Append (Step 2)

Date: 2026-10-07
Status: Accepted
Related: ADR-0053 (CP autoplay tail), CAST_CP_UX_HONESTY (Step 1)
Supersedes: Cast hard-off in ContinuousPlayGate + honest-disabled Autoplay switch

## Context

Step 1 disabled the Autoplay switch while casting because `ContinuousPlayGate`
blocked appends (`isCasting → false`) — showing ON was dishonest. Append path
`PlaybackManager.appendToContext` already Dual-mutates and
`emitCastAddsOrCommit` for Cast.

## Decision

1. **Gate** — allow Cast in `ContinuousPlayGate.shouldLoadContinuation` (same
   last-item / enabled / not-yet-loaded rules).
2. **UI** — re-enable queue Autoplay switch while casting; remove
   “Unavailable while casting” caption. Keep Cast flatten notice.
3. **Mutation** — unchanged: `appendToContext` → Dual + Cast Add actions.

## Consequences

- Apple-like Cast Autoplay: toggle works; journal CP appends reach receiver.
- Disconnect / windowed Cast queue edges covered by existing Cast queue tests.
- Full dual-band labels on Cast receiver still out of scope (flatten honesty).
