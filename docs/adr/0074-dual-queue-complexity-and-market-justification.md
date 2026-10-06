# ADR 0074 — Dual-Queue Complexity and Market Justification

Date: 2026-10-06
Status: Accepted
Related: ADR-0039, ADR-0053, ADR-0061, ADR-0067, ADR-0070, ADR-0073,
`docs/QUEUE_MANAGEMENT_MARKET_COMPARISON_BEHAVIOR_REPORT.md`,
`docs/PLAYBACK_QUEUE_MARKET_DRIFT_BEHAVIOR_REPORT.md`

## Context

Dual-queue (PRIORITY + CONTEXT + autoplay tail) costs: `DualQueueManager`,
Cast entryId sync, persist flags, three clear verbs, collection overwrite ASK,
and multi-k LOC of tests. Question: is tax justified vs YT-style single list?

Market research (2026-10): Spotify, Apple Music, Deezer, and Tidal ship dual or
dual-like control. YT dismiss-session is the outlier. Spotify is **explicit**
about bands; Apple places **AutoPlay control in the queue**.

## Decision

1. **Keep dual merge** (ADR-0039). Do not adopt YT wipe-session.
2. **Product gold** = Apple semantics (Play Next / Add / Clear manual /
   AutoPlay in-queue) + **Spotify-explicit** section lexicon
   (“Next in Queue” / “Next from”).
3. **UI SoT** remains `PlayerBar` `queue_sheet`. Wire Continuous Play toggle +
   clear-autoplay into prod sheet (end harness-only drift).
4. **Sequenced debt:**
   - PR #2: Kill local `QueueAutoLoader`; Cast load Dual-only. **Done.**
   - PR #3: Merge `playback_state` → `queue_state` (ADR-0007). **Done** (DB 57).
5. Complexity tax accepted because removing Dual breaks mid-album Play Next,
   Clear-manual-only, and process-death section restore.

## Consequences

- Sheet lexicon and Clear copy must educate dual bands (Spotify).
- Autoplay section always shows Apple-style toggle when sheet open.
- Follow-up PRs documented; not bundled with UI dual-explicit ship.
- Single-list simplification rejected unless product reverses this ADR.
