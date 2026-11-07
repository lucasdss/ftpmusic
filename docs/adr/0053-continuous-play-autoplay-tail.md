# ADR 0053 — Continuous Play Autoplay Tail

Date: 2026-10-01
Status: Accepted
Related: ADR-0052 (Surprise Me vs CP), ADR-0039 (dual queue),
ADR-0093 (Cast CP), ADR-0094 (similar songs), ADR-0095 (Cast timeline),
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
   filter requires a non-blank `cachedFilePath` (ADR-0095 — `isDownloaded`
   alone does not imply a local file). **First** Autoplay burst waits for the
   last user-selected (non-autoplay) track (ADR-0156 market timing). The
   3-track low-water mark (CP-5) applies to **Autoplay radio re-arm** only.
   `STATE_ENDED` remains the last-chance retry, and a retry that arrives while
   a load is in flight is queued and replayed once (CP-14). Appending the tail
   is the resume mechanism; no blind `play()`/`prepare()` is issued at ENDED
   because that seeks within the current item rather than starting the appended
   one.
2. **Marking:** CP appends stamp MediaItem extras `is_autoplay=true` via
   `appendToContext(..., asAutoplay=true)`. Not a third dual-queue origin —
   still CONTEXT (ADR-0039).
3. **UI:** Queue / Continue Playing / Autoplay sections. In-queue Autoplay
   toggle is a **session** kill switch (ADR-0156); Settings master stays ON by
   default and is not rewritten by the Queue switch.
4. **Journal:** Genre mixes pass `sourceId` (mix id) so they seed CP.
5. **Out of scope:** similar-songs API, Familiar/Discover chips, Cast CP.

## Consequences

- Autoplay visible and clearable without nuking Priority or Continue Playing.
- Process death: Room `queue_items.is_autoplay` (v56) restores Autoplay section;
  `hasLoadedContinuation` set when any autoplay row restored (no double CP append).
- In-process `AutoplayFlagMemory` remains JVM Bundle-stub only — not death SoT.

## Amendment (2026-11-02)

Decision #5's "out of scope" list is historical, not current behavior.
ADR-0094 made server similarity the primary CP candidate source
(`getSimilarSongs2`, up to 20 per burst) with the queue journal as fallback;
ADR-0093 enabled CP appends while casting (`appendToContext` mutates Dual and
emits one Cast `AddAll`, ADR-0111); ADR-0095 defines Dual as the Cast timeline
source of truth. Server similarity is used only when online: local-only/offline
mode skips the API and relies on the journal plus the local fallback ladder.
Familiar/Discover chips remain out of scope.

## Amendment (2026-11-03) — CP-7 tail bound

The autoplay tail is capped at **50 autoplay-stamped CONTEXT rows**
(`CONTINUOUS_PLAY_AUTOPLAY_CAP`). `appendToContext(asAutoplay=true)` evicts the
oldest autoplay rows *before* appending, never the currently playing row
(`queueEntryId`, then merged index for legacy unstamped rows, then `mediaId`),
and persists only the capped Dual queue.
Non-autoplay CONTEXT/PRIORITY/LATE rows are untouched. `restoreQueue` applies
the same bound to legacy Room snapshots, protects/remaps the playing row by
entry id, and rewrites the trimmed queue (`src=restoreTrim`) so an old oversized
tail cannot survive a process restart. `clearAutoplayQueue()` stays an explicit
manual removal and may remove the current autoplay row.

While casting, an append that evicts sends one batched
`CastQueueAction.RemoveAll` (one `queueRemoveItems`) **before** the existing
`AddAll`, so it carries the pre-burst snapshot revision; a failed prune rolls the
local queue back rather than being silently accepted. This prunes receiver rows
that may still sit ahead of the playhead after a jump-back. A failed `AddAll`
can leave the receiver as a subset of the phone queue; the next full queue load
(not the prefix watchdog) repairs that subset divergence.
