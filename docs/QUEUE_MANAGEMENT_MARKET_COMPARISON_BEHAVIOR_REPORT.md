# Queue Management Market Comparison Behavior Report

Caveman. Cycle: Apple semantics + Spotify-explicit dual UI (ADR-0074).
Prod surface = `PlayerBar` `queue_sheet`. Harness = `QueueScreen`.

## Market matrix

| Capability | Spotify | Apple Music | YT Music | Tidal | Deezer | Amazon | FTPMusic (this cycle) |
|------------|---------|-------------|----------|-------|--------|--------|------------------------|
| Dual bands visible | Next in Queue + Next From | Playing Next + AutoPlay | Weak / flat Up Next | Play Queue + Next Up | Insert-before-context | Partial inserts | **Next in Queue + Next from + Autoplay** |
| Play Next | Requested / weak | Yes | Yes | Yes | Yes | Yes | Manager yes |
| Add to Queue | Yes (end) | Yes (Play Last) | Yes | Yes | Yes | Yes | Manager yes |
| Clear = manual only | Yes | Yes | Dismiss (harsher) | Clear Play Queue | Weak clear | Varies | `clearPriority` |
| Autoplay toggle in queue | Settings / queue tools | **In queue** | Automix elsewhere | Settings | In queue (recs) | Settings | **In sheet** (wired) |
| Clear autoplay | Via off / clear | Toggle / clear | N/A | Off setting | Toggle | Off setting | **Section Clear** |
| Reorder + swipe remove | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Batch select | Mobile restored | No | Limited | No | No | No | P2 |
| Save queue → playlist | Limited | History / add | Save | Save | Limited | No | Share only (P2 save) |
| History in queue | Partial | Recently played | No | Recently played | No | No | P2 |

## Dual-queue: who has it?

- **Spotify / Apple / Deezer / Tidal** = dual or dual-like. Not FTP-only.
- **YT** = single-list + dismiss → **rejected** (ADR-0039 / 0061).
- **Amazon** = insert positions; weaker section model.

## UX alignment score (sheet)

| Heuristic | Before | After this cycle |
|-----------|--------|------------------|
| Explicit dual bands | Partial (`Queue · n`) | Spotify **Next in Queue** |
| Clear education | Ambiguous dual Clears | Clear on PRIORITY only + copy |
| Autoplay control | Label only (prod) | Apple toggle + clear-autoplay |
| Hit areas / tokens | ADR-0073 OK | Kept + section hierarchy polish |
| Harness drift | CP only on QueueScreen | Prod sheet parity |

## Edge Agent

| Edge | Status |
|------|--------|
| Clear wipes Next from | Must not — Clear = priority only |
| CP off mid-tail | Toggle persists; clear-autoplay strips stamped rows |
| Empty PRIORITY | Hide Clear; show Next from / Autoplay |
| Cast flatten | Notice kept; dual labels phone-only honesty |
| Process death | `is_priority` / `is_autoplay` Room (ADR-0067) |

## Perf Agent

- Autoplay header always composed when sheet open (small). No list key change.
- Switch local state + callback; no extra poll Flow this cycle.
- Reorder / opaque rows unchanged (ADR-0073).

## Design Agent

- PRIORITY = BrandPurple band; CONTEXT = muted; Autoplay = BrandTeal + switch.
- Clear destructive only on PRIORITY / swipe.
- 48dp shuffle/share/switch hit targets.
- Concentric `cornerM` section chrome; sheet `cornerL`.

## Coverage Agent

Target: `PlayerSurfacesUxTest` + Dual/Playback suites for CP/clear-autoplay wiring.
Gate: assembleDebug + targeted Compose green; ≥80% on touched UI contracts via assertions.

## Follow-ups (not this ship)

1. Kill `QueueAutoLoader` (PR #2) — Cast/windowing; CP trust.
2. Merge `playback_state` → `queue_state` (PR #3) — schema debt, ADR-0007.
3. P2: batch select, save-as-playlist, history band, sleep timer in sheet.
