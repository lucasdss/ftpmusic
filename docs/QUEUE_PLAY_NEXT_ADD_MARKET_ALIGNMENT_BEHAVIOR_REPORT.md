# Queue Play Next / Add to Queue Market Alignment

Caveman. Cycle: 2026-10-13 market review + placement toggle (ADR-0135).

## Market gold

| Action | Apple / Spotify | YTM flat | FTP Music |
|--------|-----------------|----------|-----------|
| Play Next | Manual front after current | After current | PRIORITY front (+ align) |
| Add to Queue | Manual end before remainder | End of whole Up Next | Toggle: `manual_end` / `session_end` |
| Clear | Manual only | Often wipe | PRIORITY + LATE |

## Dual merge

```
[CONTEXT 0..anchor] + [PRIORITY] + [CONTEXT after] + [LATE]
```

- `manual_end` (default): Add → PRIORITY tail
- `session_end`: Add → LATE tail (after full context)
- Play Next: always PRIORITY (never LATE)

## UX copy

- Settings: **Add to Queue placement**
  - After your queued songs (Apple/Spotify)
  - At the end of what’s playing (YouTube Music)
- Sheet: **Queued later** band when LATE non-empty

## Gaps closed

1. Home album sheet Play Next wired
2. Placement toggle shipped

## Edge

| Edge | Status |
|------|--------|
| Empty PRIORITY + either mode | Same insert after playhead until LATE/PRIORITY diverge |
| Toggle flip | Future adds only |
| Clear | Clears PRIORITY + LATE |
| Mid-album Play Next | alignAnchorToPlayhead (ADR-0134) |
| Process death | `is_late` Room column |

## Coverage

DualQueueManager + PlaybackManager addToQueue both placements; Settings persist; clear LATE.
