# ADR 0053 — Continuous Play Autoplay Tail

Date: 2026-10-01
Status: Accepted
Related: ADR-0052 (Surprise Me vs CP), ADR-0039 (dual queue),
docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md

## Context

Journal Continuous Play appended silently into CONTEXT. Empty journal / offline
misses set `hasLoadedContinuation` early → no retry. Genre mixes lacked
`sourceId` → never journaled. Market players (Spotify Autoplay, YT Music Up Next
autoplay, Apple Playing Next ∞) expose a **labeled autoplay tail** + in-queue
toggle, not a hidden append.

## Decision

1. **Correctness:** `ContinuousPlayLoader` resolves candidates; set
   `hasLoadedContinuation` only after ≥1 append; batch append on Main; offline
   filter (`isDownloaded || cachedFilePath`); STATE_ENDED retry.
2. **Marking:** CP appends stamp MediaItem extras `is_autoplay=true` via
   `appendToContext(..., asAutoplay=true)`. Not a third dual-queue origin —
   still CONTEXT (ADR-0039).
3. **UI:** Queue / Continue Playing / Autoplay sections. In-queue Continuous
   Play toggle (+ Clear Autoplay) shares Settings flag.
4. **Journal:** Genre mixes pass `sourceId` (mix id) so they seed CP.
5. **Out of scope:** similar-songs API, Familiar/Discover chips, Cast CP.

## Consequences

- Autoplay visible and clearable without nuking Priority or Continue Playing.
- Process death: Room `queue_items.is_autoplay` (v56) restores Autoplay section;
  `hasLoadedContinuation` set when any autoplay row restored (no double CP append).
- In-process `AutoplayFlagMemory` remains JVM Bundle-stub only — not death SoT.
