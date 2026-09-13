# 0039 — Industry Dual-Queue Semantics (Spotify / Apple)

Status: Accepted
Date: 2026-09-13
Supersedes: merge-order portion of ADR 0012
Extends: ADR 0031 persist follow-up (#2 origin per entry)

## Context

ADR 0012 stored CONTEXT then PRIORITY-at-end. Spotify/Apple play **manual
Queue before context remainder**. Interleaved origins + leading `context_size`
broke labels and restore.

## Decision

1. Contiguous merge: `[CONTEXT 0..anchor] + [PRIORITY] + [CONTEXT after anchor]`.
2. Play Next → front of PRIORITY (or after current if already in PRIORITY).
3. Add to Queue → end of PRIORITY.
4. Clear (UI) → `clearPriority` only.
5. Persist `queue_items.is_priority`; `context_size` remains derived write-only
   count (ADR 0040: never read as origin SoT).
6. Legacy restore without flags: treat all CONTEXT; Dual assigns fresh entry IDs.

## Consequences

- Mid-album inserts match Spotify/Apple.
- YouTube single-list / dismiss-session not adopted.
- Unique queue-entry IDs: ADR 0040.

## See also

`docs/DUAL_QUEUE_INDUSTRY_ALIGN_BEHAVIOR_REPORT.md`
