# Android Auto Play Next / Add Queue — Behavior Report

Date: 2026-10-14  
Status: Delivered  
Related: ADR-0137, ADR-0134, ADR-0135, ADR-0118

## Verdict (Caveman)

Per-item `PLAY_NEXT` / `ADD_TO_QUEUE` on browse music tracks.  
Mutations → `PlaybackManager` (align + placement + Cast).  
Empty timeline → prepare+play. Radio/folder/queue-node gated.  
OEM may hide item buttons — commands still registered.

## Contracts

- Supported on playable Room tracks in browse/search (`uri != null`, not radio).
- Play Next → PRIORITY front (after align).
- Add → PRIORITY tail or LATE per Settings placement.
- Missing Room / LocalOnly miss → `RESULT_ERROR_BAD_VALUE` / invalid state.
- Empty player before command → audible start after enqueue.

## Tester (HU / DHU)

- [ ] Favorites track → Play Next inserts Next in Queue
- [ ] Add to Queue + Settings `session_end` → Queued later
- [ ] Empty player → Play Next starts playback
- [ ] Radio station: no queue item buttons
- [ ] Offline missing track: command fails cleanly
- [ ] Cast: insert uses Dual Cast path (no Dual≠Exo drift)
