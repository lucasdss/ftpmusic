# Artist Songs Sort + Album Track Order — Behavior Report

**Feature:** artist Songs browse sort + album detail track order
**Date:** 2026-11-03
**Status:** Implemented
**ADR:** docs/adr/0152-artist-songs-sort-and-album-track-order.md
**Plan:** docs/PLAN_ARTIST_SONGS_SORT_AND_ALBUM_TRACK_ORDER.md (§14 authoritative)

## Surfaces

| Surface | Preference | Default | Applies to | Ignores |
|---|---|---|---|---|
| Artist detail → Songs list | `artist_songs_sort` | `play_count_desc` | list order + tap-through queue window (`state.tracks`) | Play All / Play Next All / Add All (they use `artist_play_order`) |
| Album detail → track list | `album_track_sort` | `disc_track` | list, Play All, Play Next All, Add to Queue, row tap, download-all walk | `getAlbumTracks` SQL (Auto / assistant `expandToAlbum`), album Shuffle |

Two different Random surfaces:

- **Songs → Random**: a browse-list shuffle. Reselecting the row draws a new
  seed; load-more inside the same generation keeps the same order.
- **Play All → "Shuffle all songs"**: playback shuffle via
  `tryShuffleContext`, driven by `artist_play_order`.

## Artist Songs

### Modes

| Mode | Directions | Pagination |
|---|---|---|
| Play count | Most played (default) / Least played | SQL keyset, 50/page |
| Random | — | full membership loaded once, seeded shuffle |
| Album name | A–Z / Z–A | full membership grouped into album blocks |
| Album year | Oldest / Newest | full membership + `cached_albums.year` |
| Track name | A–Z / Z–A | SQL keyset, 50/page |

- The preference is **global** (one setting for all artists), stored in
  SecureStorage and read on every `loadArtist`.
- Unknown/legacy values fall back to most played first.
- Album-block modes use `AlbumTrackOrdering.groupByAlbum` — the same helper as
  `ArtistPlayOrderer` — so within-album order is disc → track → title → id and
  matches Play All. Album name uses `track.album` (not `cached_albums.name`).
  Unknown years sort last in both directions. Orphans (blank album id) are
  stable singleton blocks in first-seen order.
- Sort changes persist immediately, clear the list, reset to a fresh generation,
  and scroll the list to the top. A play-count change during the session does
  not live-reorder the list; the new order appears on the next load.
- Load-more follows the same sort: keyset modes page in SQL using the raw
  page's last row as the cursor; album-block/random modes slice the cached
  ordered source. If a keyset page dedupes to zero new rows, the ViewModel
  chases up to 20 pages; if it still finds nothing, `hasMoreTracks` is cleared
  so the UI cannot spin.
- Tap-through (`TrackRow` tap) starts the queue from the **loaded** page window
  only. Row sequence numbers are 1…n list ranks, not album track numbers.
- Play-count keyset drift across a cursor boundary (a count changing mid-scroll)
  can skip a track until the next open; it never duplicates rows.

### UI

- Compact sort bar above the list: tag `artist_songs_sort_button`, announces
  `artist_songs_sort_button_cd` ("Sort songs, current: …") for TalkBack.
- Bottom sheet: tag `artist_songs_sort_sheet`; mode rows are tagged
  `artist_songs_sort_<natural key>` (e.g. `artist_songs_sort_album_name_asc`
  even when the selected direction is Z–A — direction lives in the chips);
  direction chips are `artist_songs_sort_dir_asc` / `…_desc`.
- Copy lives in `res/values/strings_artist_songs_sort.xml`.

## Album track order

- Loaded in `SettingsViewModel.init`/`refresh()`; setter persists immediately.
  Applies the **next** time an album is opened (no live re-sort).
- Play counts come from `TrackDao.getTrackPlayCounts` in 500-id chunks. Ids
  missing from the response (`play_count > 0` filter) count as 0.
- Lookup failure is isolated: the album still loads in disc → track order and
  `state.error` stays null.
- `disc_track` skips the play-count query entirely.
- With play-count order, track-number labels in the rows become
  non-monotonic; the artist list's sequence chrome stays 1…n.

### Android Auto / assistant

- `CachedMetadataDao.getAlbumTracks` is unchanged: disc → track.
- Auto browse and assistant `expandToAlbum` therefore ignore
  `album_track_sort`. This is intentional and documented, not drift.
- Album Shuffle (`tryShuffleContext`) always shuffles, regardless of the
  preference.

## Persistence

| Key | Values | Default |
|---|---|---|
| `artist_songs_sort` | `play_count_desc`, `play_count_asc`, `random`, `album_name_asc\\|desc`, `album_year_asc\\|desc`, `track_name_asc\\|desc` | `play_count_desc` |
| `album_track_sort` | `disc_track`, `play_count_desc`, `play_count_asc` | `disc_track` |
| `artist_play_order` | unchanged (ADR-0141/0145) | `shuffle_albums` |

No Room schema change — `AppDatabase` stays version 68.

## Settings entry

Appearance → **Album track order** (anchor `album_track_sort`), radios tagged
`settings_album_track_sort_disc_track`, `…_play_count_desc`, `…_play_count_asc`.
Search keywords: `album order`, `track order`, `play count`, `most played`,
`least played`, `disc` — all deep-link to the section.

## Verification

- `:app:compileDebugKotlin`, `:app:assembleDebug`, `:app:ktlintCheck` pass.
- `ArtistDetailViewModelTest` (82 tests): default play-count keyset, asc keyset,
  random load-once + stable load-more + reselect reshuffle, album name/year,
  sort-change reset, chase-cap termination, and a cross-artist race test
  (load ar-1 → switch to ar-2 mid-load → ar-1's page/count commit is dropped).
  Mutation-checked: removing the generation guard at the cached-branch commit
  makes that test fail.
- `AlbumDetailViewModelTest`: default never queries counts, desc/asc with batch
  counts and missing ids → 0, lookup failure degrades.
- `ArtistSongsSortTest`, `AlbumTrackSortTest`, `ArtistSongsSorterTest`,
  `AlbumTrackSorterTest`, `AlbumTrackOrderingTest`: enums, comparators, orphan
  blocks, null years, direction flips, seed determinism.
- `DaosRoomTest`: real-SQL play-count DESC/ASC and title-DESC keysets with
  duplicate counts/titles; existing artist-view membership tests still pass.
- `ArtistViewMembershipTest`: keyset dispatch per mode/direction, cursor-type
  mismatch rejected, non-keyset sorts never hit the DAO, 500-id album-year
  chunking (1001 ids → 3 queries) and empty short-circuit.
- `SettingsViewModelTest`, `AppearanceSettingsScreenComposeTest`,
  `SettingsIndexTest`, `SettingsSearchTest` pass.
- `ArtistPlayOrdererTest` unchanged and passing → Play All behavior is
  unchanged by the `AlbumTrackOrdering` extraction.
