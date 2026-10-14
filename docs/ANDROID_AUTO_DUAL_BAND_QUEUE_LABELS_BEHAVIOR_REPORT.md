# Android Auto Dual-Band Queue Labels — Behavior Report

Date: 2026-10-14  
Status: Delivered  
Related: ADR-0138, ADR-0135, ADR-0137

## Verdict (Caveman)

Browse **Queue** after Recent. Band folders = phone sheet lexicon.  
Ids `auto_queue_track:{entryId}` — tap jumps, no album-expand.  
Empty bands hidden. Revision notify refreshes HU.  
Stock Auto Queue stamps descoped.

## Contracts

- Root always shows Queue folder.
- Bands: Next in Queue / Next from / Autoplay / Queued later (non-empty only).
- Up Next only (`index > current`).
- Grouping == `QueueSheetGrouping.groupUpcoming`.
- Tap queue track → seek Dual index; PRIORITY preserved.
- Stale queue-track tap fails (no empty timeline wipe).
- Cast queue-track tap → Dual JumpTo only (no CastPlayer.setMediaItems).
- No per-item Play Next/Add on queue-node rows.

## Tester (HU / DHU)

- [ ] Play Next from Favorites → Queue → Next in Queue shows track
- [ ] Tap queue track jumps without replacing album context / wiping PRIORITY
- [ ] session_end Add appears under Queued later
- [ ] Empty queue → Queue folder empty (no broken band folders)
- [ ] After queue edit, HU children refresh (revision notify)
- [ ] Cast: queue-track tap uses JumpTo (no Cast reload)
- [ ] Stale queue-track tap does not clear playing timeline
