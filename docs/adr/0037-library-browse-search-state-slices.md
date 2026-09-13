# 0037 — Library Browse/Search Split + Tab State Slices

Status: Accepted
Date: 2026-09-11

## Context

Library Albums/Artists search wrote DB hits into `LibraryState.albums` /
`artists`, so clearing the query left the search set as the browse list.
`LibraryScreen` also collected the entire `LibraryState`, so Home/fav/mix
writes recomposed the album grid while scrolling.

Room `getAllAlbums()` remains unbounded; Paging 3 is premature while
`loadAlphaAlbums` still dumps the full catalog.

## Decision

1. **Browse vs results** — `searchAlbums` / `searchArtists` write
   `albumSearchResults` / `artistSearchResults`. Browse lists stay intact.
   Empty/short query calls `clearAlbumSearch` / `clearArtistSearch` (no reload).
   UI shows `searchResults ?: browse`.
2. **Tab slices** — `albumTabUi`, `artistTabUi`, `libraryShellUi` are
   `map` + `distinctUntilChanged` + `stateIn(WhileSubscribed)` projections.
   Albums/Artists collect only their slice; Playlists/Radio/sheets still use
   full `state` where needed.
3. **Coil** — `CoverArtImage` builds sized `ImageRequest` + memory key;
   Library album grid uses `CoverArtImage(decodeSize = 160.dp)`.
4. **Room paging** — deferred. Recommended later DAO shape:
   `ORDER BY name LIMIT :limit OFFSET :offset` for albums (artists already
   have paged helpers elsewhere). No Paging 3 this pass.

## Consequences

- Clear search restores browse without another DB/API load.
- Unrelated Home/mix ticks do not invalidate Albums/Artists tab UIs.
- See `docs/LIBRARY_SCROLL_PERF_BEHAVIOR_REPORT.md` P1 Act.
