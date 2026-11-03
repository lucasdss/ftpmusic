# 0152 — Artist Songs Browse Sort + Album Track Order

Status: Accepted
Date: 2026-11-03
Related: ADR-0141 (artist Play All order), ADR-0142 (artist view membership),
ADR-0145 (track membership split), ADR-0126 (scroll anchors)

## Context

Two independent list-order surfaces lacked a user preference:

1. **Artist detail → Songs** was hard-wired to title A–Z with keyset pagination
   (`getTracksForArtistView` / `…After`). The list order also defined the
   tap-through playback window, while **Play All** used a separate
   `artist_play_order` preference (ADR-0141 / ADR-0145). Users wanted the songs
   they actually listen to first, not alphabetical.
2. **Album detail** had a fixed disc → track ascending comparator. Local play
   counts already exist (`tracks.play_count`, `TrackDao.getTrackPlayCounts`),
   but there was no way to surface "most played on this album".

Constraints:

- No Room schema change; `AppDatabase` stays version 68.
- `getAlbumTracks` SQL must stay disc → track: Android Auto browse and the
  assistant `expandToAlbum` path consume it and must not inherit a UI pref.
- Play All ordering must not change when the user changes the browse sort.

## Decision

### 1. Artist Songs browse sort is its own global preference

`SecureStorage.KEY_ARTIST_SONGS_SORT` (`artist_songs_sort`) stores one of:

| Key | Mode | Pagination |
| --- | --- | --- |
| `play_count_desc` (**default**) | play count, most played first | SQL keyset |
| `play_count_asc` | play count, least played first | SQL keyset |
| `random` | full-catalog shuffle | in-memory |
| `album_name_asc` / `_desc` | album blocks, tracks in album order | in-memory |
| `album_year_asc` / `_desc` | album blocks by release year | in-memory |
| `track_name_asc` / `_desc` | title A–Z / Z–A | SQL keyset |

Unknown / unset keys fall back to `play_count_desc` (`ArtistSongsSort.fromKey`).

The preference is **global** (not per artist) and drives only the Songs list
plus the tap-through queue window built from `state.tracks`. Play All / Play
Next All / Add All keep `artist_play_order`.

### 2. Flat modes paginate in SQL with composite cursors

`TrackDao` gained first-page + cursor queries for play-count DESC/ASC and
title DESC (title ASC reuses the existing pair). Every artist-view query —
existing title paging, the new play-count/title-desc paging, the full load and
the count — uses the shared `ARTIST_VIEW_MEMBERSHIP_SQL` union so membership
can never drift between paging and Play All.

- Play-count DESC: `ORDER BY play_count DESC, title ASC, id ASC`, cursor
  `(play_count < c) OR (play_count = c AND title > t) OR (… AND id > i)`.
- Play-count ASC: identical except the play-count comparison flips to `>`.
- Title DESC: `ORDER BY title DESC, id ASC`, cursor `title < t OR (title = t AND id > i)`.
- Titles compare with SQLite BINARY collation; no disc/track columns are part
  of the flat `ORDER BY` or cursors.
- `hasMoreTracks` comes from the **raw** page size, never the deduplicated set.

`ArtistViewMembership.loadSongsKeysetPage` maps a sort to the right query and
returns an empty list for non-keyset sorts, so a mis-wired caller cannot
silently fall back to title A–Z.

### 3. Album-block and random modes load the membership once

`ArtistViewMembership.loadAllTracks` → `ArtistSongsSorter.order`:

- Album-name / album-year group blocks with `AlbumTrackOrdering.groupByAlbum`
  (the same helper `ArtistPlayOrderer` uses), order blocks by
  `track.album.lowercase()` or by `cached_albums.year`, and keep disc → track
  inside each album. Unknown years sort last in both directions.
- Orphan tracks (blank `albumId`) each become a singleton block keyed by the
  empty album name — identical to Play All, no special-case "sort orphans by
  title".
- `random` uses a seed stored on the ViewModel: reselecting Random draws a new
  seed (reshuffle), load-more within a generation keeps the same order.

Album years are fetched with `getAlbumYearRows`, chunked at 500 ids
(`SQLITE_IN_CHUNK`).

### 4. ViewModel paging contract (`ArtistDetailViewModel`)

- `loadArtist` cancels any in-flight Songs job, bumps `songsGeneration`, resets
  the cursor/source, and freezes the exact membership `artistName` used for
  page 1 (avoids `""` vs `"Unknown"` drift on later pages).
- `setSongsSort` persists the pref, resets the list, and reloads page 1 for the
  new generation; stale pages are dropped by generation check after every
  suspend point.
- `loadMoreTracks` chases keyset pages until at least one **new** row is
  appended or the catalog ends (N1: play counts can move rows across the cursor
  between pages). The cursor always advances from the raw page. If the chase
  cap is reached with only duplicates, `hasMoreTracks` is cleared so the UI
  cannot spin on the same cursor.
- `TrackEntity.toTrack()` now maps `playCount` and `lastPlayedAt`.

### 5. Album detail track order

`SecureStorage.KEY_ALBUM_TRACK_SORT` (`album_track_sort`):

| Key | Order |
| --- | --- |
| `disc_track` (**default**) | disc → track → title → id (historical comparator) |
| `play_count_desc` | play count desc, then disc → track → title → id |
| `play_count_asc` | play count asc, then the same tie-breakers |

Applied in `AlbumDetailViewModel.prepareAlbumTracks` on the **3-arg**
`loadAlbum`, for both the cached first paint and the API result. Play counts
come from `getTrackPlayCounts` in 500-id chunks; ids missing from the response
(`play_count > 0` filter) map to 0. The lookup has its own try/catch so a
failure degrades to disc/track instead of aborting the album load.

No special "all zero counts" branch: zero counts fall through to the same
disc → track tie-breakers.

Ignored by design:

- `CachedMetadataDao.getAlbumTracks` (Auto browse / assistant `expandToAlbum`).
- Album **Shuffle** (`tryShuffleContext`) — always shuffles.
- Album Play All / Play Next All / Add to Queue / row taps use the sorted
  `state.tracks`, so the preference applies there. Download-all walks the same
  list and therefore enqueues in play-count order when enabled.

### 6. Settings + UI wiring

- `SettingsUiState.albumTrackSort` is loaded in `SettingsViewModel.init` and
  persisted by `setAlbumTrackSort`. The change applies on the next album open;
  no live re-sort.
- Appearance → "Album track order" section: three radio rows (Track number,
  Most played first, Least played first) with `testTag`s
  `settings_album_track_sort_<key>`.
- `SettingsAnchors.ALBUM_TRACK_SORT` + `SettingsIndex` keywords/anchors +
  `NavHost` `anchor` pass-through let Settings search deep-link to the section.
- Artist Songs tab shows a compact sort bar above the list that opens a bottom
  sheet: five modes (Play count, Random, Album name, Album year, Track name) and
  direction chips for every mode except Random. Copy lives in
  `strings_artist_songs_sort.xml`; the bar announces
  `artist_songs_sort_button_cd` for TalkBack.
- Changing the sort scrolls the list back to the top. A sort change does not
  live-reorder the current list when a play count changes mid-session; the new
  order appears on the next load.

## Consequences

- Existing installs flip the **Songs** tab from title A–Z to most played first.
  This is a deliberate product-default change, not a defect fix.
- Play All is unchanged and still independent: two different ordering systems
  now sit on the same screen.
- Song rows show a 1…n sequence rank, not the album track number, in
  play-count / random / title modes; album track-number labels become
  non-monotonic when the album play-count mode is on.
- Album-block and random modes load the full artist membership (and album years)
  before the first paint; large or VA-heavy catalogs pay that cost once per open.
- Play-count keyset drift mid-scroll is mitigated by raw-page cursors + dedupe;
  a track whose count changes across the cursor boundary is skipped until the
  next open, not duplicated.
- Android Auto / assistant album expansion intentionally disagree with the
  album track-order preference.

## Tests

- See `docs/ARTIST_SONGS_SORT_BEHAVIOR_REPORT.md` for the user-facing contract
  and the Auto/assistant/Shuffle carve-outs.
- `ArtistSongsSortTest`, `AlbumTrackSortTest` — key round-trip + defaults.
- `ArtistSongsSorterTest`, `AlbumTrackSorterTest`, `AlbumTrackOrderingTest` —
  ordering semantics, orphan blocks, null years, direction flips.
- `ArtistPlayOrdererTest` — unchanged fixtures prove the comparator extraction
  is behaviour-neutral for Play All.
- `ArtistDetailViewModelTest` — default play-count keyset, asc, random
  load-once + stable load-more, album-name/year, sort change resets page 1,
  Random reselect reshuffles, chase-cap termination, cross-artist race
  (generation guard blocks the previous artist's commit).
- `ArtistViewMembershipTest` — keyset dispatch per mode/direction, mismatched
  cursor rejected, album-year chunking.
- `AlbumDetailViewModelTest` — default never queries play counts, batch counts
  with missing ids → 0, asc/desc, lookup failure degrades to disc/track.
- `SettingsViewModelTest` — default/persisted album sort, setter persistence.
- `DaosRoomTest` — real-SQL play-count DESC/ASC and title-DESC keysets with
  duplicate counts/titles.
- `AppearanceSettingsScreenComposeTest` — album-order rows delegate to the VM.
