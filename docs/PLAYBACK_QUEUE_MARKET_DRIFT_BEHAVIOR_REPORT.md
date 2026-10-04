# Playback + Queue Market Drift Behavior Report

Caveman. Market gold = Spotify / Apple dual-queue (ADR 0039). YT wipe-session rejected (ADR 0061).
Scope: phone + Cast. P0 fixes this cycle.

## Market matrix

| Behavior | Spotify / Apple | YT Music | FTPMusic |
|----------|-----------------|----------|----------|
| Dual merge after current | PRIORITY mid-list | Single list | Dual merge ADR 0039 |
| Play Next | Up Next front | Queue | PRIORITY front |
| Add to Queue | Up Next end | Queue end | PRIORITY end |
| Clear Queue UI | Clear user queue | Clear | `clearPriority` |
| Clear upcoming / trim | Keep current | Varies | `clearQueue` trim after current |
| Collection row tap | Keep Up Next | Often wipe | `playAlbum(startIndex)` keep PRIORITY |
| Collection Play header | Replace context; ask/clear Up Next | Wipe session | `tryStartContext` ASK |
| Autoplay / radio tail | Labeled radio/∞ | Up Next autoplay | CONTEXT + `is_autoplay` |
| Cast sections | Often flatten | Flatten | Dual phone; Cast ClearAndPlay / entryId |

## P0 drifts found → fixed

1. **Local persist from Player** — `MediaService.saveQueueState` + several `PlaybackManager` edit paths used Player timeline; Dual flags length-mismatched → origin/autoplay drop. **Fix:** always `buildQueueStateFromDual()` (ADR 0067).
2. **`playStream` Dual bypass** — `queueManager.playAll` orphan, no persist/Cast. **Fix:** `setSingleContextWithSource` + merge play + Dual persist + ClearAndPlay (keep PRIORITY like `playSingleTrack`).
3. **Cast `clearQueue` silent** — Dual prune + exo mirror; no Cast cmd; always `persistence.clear()`. **Fix:** `ClearAndPlay` remaining + Dual persist (or clear if empty); optimistic snapshot/rollback path.
4. **ADR 0061 tests thin** — row vs header contract asserted in Album VM tests (keep PRIORITY vs ASK).

## Edge Agent (caveman)

| Edge | Status |
|------|--------|
| Dual≠Player mid-drag | Known; commit on drag stop |
| CP vs QueueAutoLoader dual-append | P1 backlog; local AutoLoader vestigial |
| Process death flag maps | Room `is_priority` / `is_autoplay` / entryId |
| Cast ack fail | `onCastCommandAck` rollback + Dual resync |
| `clearQueue` keep current | Persist remaining Dual (was wipe Room) |

## Perf Agent (caveman)

| Issue | Note |
|-------|------|
| Player rebuild + Dual flags | Removed from persist hot path |
| Optimistic local | Still commit-immediate; Cast-only value |
| Gapless local edits | Kept (ADR 0042) |

## Coverage Agent

JaCoCo after playback.* + AlbumDetailViewModelTest:

| Class | Line | Branch |
|-------|------|--------|
| PlaybackManager (primary P0) | **98.3%** | **84.1%** |
| DualQueueManager | 97.4% | 82.8% |
| AlbumDetailViewModel (tests only) | 81.5% | 57.5% (pre-existing branch debt) |
| MediaService (3-line persist SoT flip) | 24.0% whole-file hist. debt | — |

Gate: PlaybackManager ≥80% line+branch. MediaService whole-file below bar historically; changed persist path = Dual builder covered by PM suites.

## P1 / P2 backlog (no code this cycle)

- Kill local `QueueAutoLoader`; Cast load from Dual only
- Merge `playback_state` into `queue_state` (ADR 0007 open)
- Prune Overwrite PUSH / settings triad if unused in prod
- Delete legacy `QueueScreen` (prod = PlayerBar sheet; ADR-0070 ports reorder/remove into sheet first)
- CP similarity API / Cast Continuous Play
