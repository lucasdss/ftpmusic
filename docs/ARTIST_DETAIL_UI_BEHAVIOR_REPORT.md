# Artist Detail UI Behavior Report

**Feature:** Artist detail Albums-first + cross-surface spacing
**Date:** 2026-10-04
**Status:** Implemented (ADR-0069)
**ADR:** docs/adr/0069-artist-albums-first-ui.md

## Layout (top → bottom)

```
Hero (adp 130) + back + like/dislike + name + album/track counts
→ optional MusicBrainz public ★ (textLabelM)
→ DetailActionRow: Play All | Shuffle | ⋮   (full catalog)
→ optional Similar Artists LazyRow
→ TabRow: Albums (default 0) | Songs (1)
→ AlbumsTab grid XOR Songs LazyColumn
```

## Tab contract

| Index | Label | Body | Default |
| :---: | :--- | :--- | :---: |
| 0 | Albums | 2-col grid (`gridGapH`×`gridGapV`) | Yes |
| 1 | Songs | Full artist track list (paged) | No |

Tab state: local `remember`; process death → Albums. No VM persistence.

## Play semantics (Spotify-style)

- **Play All / Shuffle** always target loaded artist tracks, not “selected album”.
- Enabled when `state.tracks.isNotEmpty()` even if Albums tab empty/visible.
- Empty albums + non-empty tracks: Albums shows empty state; Play still works.
- Empty tracks: CTAs disabled; Songs empty copy.

## Spacing contract (@360dp ref)

| Region | Token / value |
| :--- | :--- |
| Detail action pad | `spacingXL` × `spacingL` (20×16) |
| Action height | `detailActionHeight()` = adp(42) |
| Action gap / icon–label | 10dp / 8dp |
| More hit | ≥ `minTouchTarget()` (48 base) |
| Track list H/V | `spacingXL` / `spacingM` |
| Album grid gaps | `gridGapH` 10 / `gridGapV` 14 |
| Art → title | `spacingBelowArt` 6 |
| Tab label | `textBodyM`; selected BrandTeal Bold |

## Edge cases (Edge-Case Agent)

1. Process death mid-Songs → reopen Albums — OK.
2. Tracks exist, albums empty → Play enabled; Albums empty UI.
3. Albums exist, tracks still paging → Play uses loaded page; Songs load-more.
4. Similar artists resolve miss → Toast; no nav.
5. Overwrite modal Ask mode unchanged.

## Performance (Performance Agent)

- Tab swap remounts one tab body; no extra network.
- Grid/list keep existing keys; no StableList change required this pass.

## Cross-surface parity

| Surface | Change |
| :--- | :--- |
| Album detail | Uses shared `DetailActionRow` (“Play”) |
| Library Artists/Playlists | Row vertical → `spacingM()`; album grid tokens |
| Home | LazyRow → `spacingM()`; Surprise Me tokens; fav artist title `textBodyM` |

## Follow-up

- Reaction thumbs remain ~40dp visible hit (`reactionHitSize`) — dense grids.
- No “Popular” ranking; Songs = full catalog.
