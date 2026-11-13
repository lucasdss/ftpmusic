# Artist Detail UI Behavior Report

**Feature:** Artist detail Albums-first + cross-surface spacing
**Date:** 2026-10-04
**Status:** Implemented (ADR-0069)
**ADR:** docs/adr/0069-artist-albums-first-ui.md

## Layout (top → bottom)

```
Hero (adp 130) + back + like/dislike + name + album/track counts  [fixed]
→ collapsing chrome (nestedScroll; scrolls away above tabs):
    optional MusicBrainz public ★ (textLabelM)
    biography teaser (max 3 lines; never in-place expand)
    DetailActionRow: Play All | ⋮   (full catalog; no Shuffle)
    optional Desired CTA
    optional Similar Artists LazyRow
→ TabRow: Albums (default 0) | Songs (1)  [pinned]
→ AlbumsTab grid XOR Songs LazyColumn
```

## Biography

- Source: Last.fm / Navidrome `getArtistInfo2` (≤2000 chars). MusicBrainz = public ★ only.
- Chrome shows a **compact teaser** (`ArtistBioUi.TEASER_MAX_LINES` = 3, ellipsis). Never
  `Int.MAX_VALUE` expand in chrome (that collapsed the album grid).
- “Read more” (length > 160) opens a scrollable **`ModalBottomSheet`** with full text +
  subtle “Artist info” source line. No dedicated `artist/{id}/bio` route.
- HTML stripped on Navidrome enrich (`TextHtml.sanitizeBiography`) and again at display
  for already-cached dirty rows.

## Tab contract

| Index | Label | Body | Default |
| :---: | :--- | :--- | :---: |
| 0 | Albums | 2-col grid (`gridGapH`×`gridGapV`) | Yes |
| 1 | Songs | Full artist track list (paged, sort bar on top) | No |

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
- Songs = full catalog; ordering is the user's browse sort (ADR-0152), not a
  server-side “Popular” ranking.

## Amendment (2026-11-11) — bio teaser + collapsing chrome

- Bio expand-in-chrome removed; sheet + teaser only.
- Rating / teaser / Play All / Desired / Similar live in collapsing header region;
  TabRow stays pinned. Not Library AppHeader ride.
- **2.1.8:** chrome `Column` measured with `wrapContentHeight(unbounded=true)` inside the
  clipped visible-height `Box` (NavHost / ADR-0107) so `onSizeChanged` never feeds
  constrained height into `updateHeaderHeight`.

## Amendment (2026-11-03, ADR-0152)

- **Songs sort bar** sits above the list: a compact trigger showing the current
  summary (`artist_songs_sort_button`) opens `ArtistSongsSortSheet`.
- Modes: Play count (**default**, most played first), Random, Album name, Album
  year, Track name. Every mode except Random exposes a direction chip
  (Most/Least played, A–Z/Z–A, Oldest/Newest).
- The preference is global (`artist_songs_sort`) and independent from Play All
  (`artist_play_order`).
- Play-count and track-name modes page through SQL keyset queries (50/page);
  album-name, album-year and random load the full artist membership once and
  page in memory. Random reselect reshuffles; load-more stays stable.
- Tap-through (`TrackRow` tap) starts a queue from the **loaded** `state.tracks`
  window only — it does not continue through the unloaded discography.
- Row sequence numbers are 1…n list ranks, so in play-count / random / album
  modes they no longer match album track numbers.
- Changing the sort scrolls to the top and replaces the list; a play-count
  change does not live-reorder the open list.
