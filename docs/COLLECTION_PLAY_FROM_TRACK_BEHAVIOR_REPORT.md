# Collection Play-From-Track Behavior Report

Caveman. Market = Spotify/Apple (ADR 0039, ADR 0061). Not YT wipe-session.

## Contract

| Action | API | PRIORITY | Modal |
|--------|-----|----------|-------|
| Row tap (collection) | `playAlbum(list, startIndex)` no source | Keep after current | No |
| Play / Play All | `tryStartContext` + source | Cleared on Clean/play-with-source | ASK if nonempty |
| Shuffle | `tryShuffleContext` + source | Same | ASK if nonempty |
| Search / Profile recent | `playSingleTrack` | Keep | No |
| Queue sheet | `playFromIndex` | Unchanged | No |

Merge after row tap: `[CTX 0..clicked] + [PRIORITY] + [CTX after]`.

## Surfaces

| Surface | Row tap | Play / Shuffle |
|---------|---------|----------------|
| Album | `playTrack(i)` full album | `tryStart*` + modal |
| Playlist | `playTrack(i)` full playlist | `tryStart*` + modal |
| Artist | `playTrack(i)` loaded tracks | `tryStart*` + modal |
| Daily Mix | `playTrack(i)` full mix | VM `playAll`/`shuffle` → `tryStart*` + modal |
| Favorites tracks | `playTrack(i)` loaded liked/disliked list | — |
| Search / Profile | single track | — |
| Queue | seek only | — |

## Edge

- Empty / OOB index → no-op
- Cast → `playMergedQueue` / `ClearAndPlay(startIndex)`
- Pagination → loaded slice only
- Long-press Play Next / Add to Queue → PRIORITY ops, not CONTEXT replace
