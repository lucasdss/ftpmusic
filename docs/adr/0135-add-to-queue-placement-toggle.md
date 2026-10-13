# 0135 — Add to Queue Placement Toggle

Status: Accepted
Date: 2026-10-13
Extends: ADR 0039 (dual-queue), ADR 0134 (align-before-mutate)

## Context

Play Next / Add to Queue match Apple/Spotify dual-band gold (PRIORITY after
playhead). Some users want YouTube Music’s “append after the whole playlist.”
Labels are shared; “end of queue” is not.

## Decision

1. Setting `add_to_queue_placement` (SecureStorage):
   - `manual_end` (default) — Add to Queue → end of PRIORITY (Apple/Spotify)
   - `session_end` — Add to Queue → LATE band after full CONTEXT (YTM-like)
2. Play Next always → PRIORITY front (unchanged).
3. Dual merge:
   `[CONTEXT 0..anchor] + [PRIORITY] + [CONTEXT after] + [LATE]`
4. Persist `queue_items.is_late`; LATE also `is_priority=1` (user-added).
5. Clear Next in Queue → clear PRIORITY **and** LATE.
6. Toggle flip does not reshuffle existing rows — future adds only.
7. Queue sheet: LATE section **Queued later** below Next from when non-empty.

## Consequences

- Home / detail surfaces keep both actions; placement is global.
- Cast uses Dual insert index + `beforeEntryId` for LATE.
- Autoplay stays in CONTEXT stamps; LATE sits after full context (incl. autoplay).
