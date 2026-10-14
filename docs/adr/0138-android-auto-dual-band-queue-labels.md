# ADR 0138 — Android Auto Dual-Band Queue Labels

Date: 2026-10-14
Status: Accepted
Related: ADR-0039 (dual queue), ADR-0135 (LATE), ADR-0137 (queue commands)
Supersedes ADR-0131 Out for “dual-band Auto labels”.
Behavior: `docs/ANDROID_AUTO_DUAL_BAND_QUEUE_LABELS_BEHAVIOR_REPORT.md`

## Context

Phone Queue sheet shows PRIORITY / Next from / Autoplay / Queued later.
Auto’s stock Queue is a flat player timeline — no section headers. ADR-0131
deferred dual-band labels; drivers still need Up Next structure in the car.

## Decision

1. **Browse Queue node** — Root folder `auto_queue` after Recent (10th root
   child). Always present; empty → empty children (no fake rows).
2. **Band folders** — Non-empty only: `Next in Queue`, `Next from`,
   `Autoplay`, `Queued later` (phone lexicon). Counts in subtitle.
3. **Up Next only** — Rows with merge index `> currentIndex`; history/current
   excluded. Grouping matches `QueueSheetGrouping.groupUpcoming`.
4. **Stable media ids** — `auto_queue_track:{entryId}` (never bare Subsonic
   ids). Tap → `onSetMediaItems` returns Dual merged timeline at that index
   (no album-expand; Dual adopt no-ops when ids match).
5. **No Play Next/Add on queue-node tracks** — tap jumps; re-queue from library.
6. **Revision notify** — `MediaService` collects `QueueRevisionTracker`,
   debounced `notifyChildrenChanged` for Queue + bands + root.
7. **Paging** — Revision-scoped snapshot cache so page N matches page 0.
8. **Out** — Stock Auto Queue subtitle stamps; AAOS Car App Library.

## Consequences

- HU browse shows phone dual-band structure without flattening honesty lies.
- Queue-track play does not wipe PRIORITY via album-expand.
- ADR-0131 Out list updated; market appendix marks labels shipped.
