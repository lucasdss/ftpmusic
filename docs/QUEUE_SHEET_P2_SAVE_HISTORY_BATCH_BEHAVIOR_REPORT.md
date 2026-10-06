# Queue Sheet P2 — Save + History + Batch Select Behavior Report

Caveman. Scope D. Surface = prod `PlayerBar` `queue_sheet` (ADR-0075).
YT dismiss-session rejected. Clear = `clearPriority` only outside selection.

## Ship

| Feature | Behavior |
|---------|----------|
| Save | Local-first `PlaylistRepository`; Share unchanged (ADR-0015) |
| History | `TrackDao.getRecentlyPlayed(20)`; exclude queue IDs; `playNext` |
| Batch | Select / long-press → checkboxes → Remove descending |

## Edge Agent

| Edge | Status |
|------|--------|
| Empty queue save | No-op |
| History empty | Section hidden |
| History ID already queued | Filtered out |
| Selection + swipe | Swipe off in selection mode |
| Selection + dismiss sheet | Mode cleared |
| Clear while selecting | Hidden; Cancel/Remove shown |
| Now Playing pin | Not selectable |
| History rows | No checkbox / no drag |

## Perf Agent

| Concern | Mitigation |
|---------|------------|
| History load while sheet closed | `onQueueSheetOpened` only |
| Queue art while closed | Unchanged (compose sheet when open) |
| Selection set | `Set<Int>` indices; batch remove descending |

## Design Agent

| Token | Use |
|-------|-----|
| Save / Select / checkbox / Remove | 48dp hits |
| History header | `NavUnselected` muted (not purple) |
| Selected row | `surfaceVariant` |
| Remove | `DestructiveRed` |
| Section borders | Existing concentric; no new card chrome |

## Coverage Agent

| Contract | Test |
|----------|------|
| Save create+add / empty no-op | `FavoriteTest` |
| Batch remove descending | `FavoriteTest` |
| Save tag / history section / select enter+remove | `PlayerSurfacesUxTest` |
| Gate | `assembleDebug` + targeted tests |

## OOS

Sleep timer / repeat in sheet; YT dismiss-session; Dual/Cast flatten; rework Share to local-first.
