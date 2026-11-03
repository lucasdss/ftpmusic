# Plan — Artist Songs Play-Count Sorting + Album Track Order

Status: Proposed — validated in **§14** (docs-only). Apply §14 corrections
before coding; §§1–13 remain the historical draft.
Date: 2026-11-03
Related: ADR-0141, ADR-0142, ADR-0145, ADR-0152 (to be created)

## Goal

- Artist detail **Songs** list defaults to local play count, most played first.
- Artist Songs sort options: play count, random, album name, album release year (when available), track name, with ascending/descending.
- Artist Songs sort is one **global** persisted preference (not per artist).
- The new Songs sort controls the **Songs list and tap-through queue only**.
- Artist **Play All / Play Next All / Add All** keep the existing independent `KEY_ARTIST_PLAY_ORDER` behavior (ADR-0141, default shuffle albums).
- Album detail tracks default to **disc → track number ascending**.
- Settings → Appearance can switch album tracks to **play count**, with most or least played first.
- No Room schema migration. `AppDatabase` stays version **68**.

## Decisions confirmed with user

| Question | Decision |
|---|---|
| Does artist Songs sort control Play All? | No — keep both independent |
| Artist sort persistence scope | One global preference |
| Album play-count direction | Most played first / Least played first |
| Album setting location | Settings → Appearance |

## 1. Sorting models

New package: `com.lucasdss.ftpmusic.app.data.sort`.

### Artist Songs

One enum encodes mode + direction, following the existing `ArtistPlayOrder` pattern.

```kotlin
enum class ArtistSongsSort(val key: String) {
    PLAY_COUNT_DESC("play_count_desc"),   // default
    PLAY_COUNT_ASC("play_count_asc"),
    RANDOM("random"),
    ALBUM_NAME_ASC("album_name_asc"),
    ALBUM_NAME_DESC("album_name_desc"),
    ALBUM_YEAR_ASC("album_year_asc"),
    ALBUM_YEAR_DESC("album_year_desc"),
    TRACK_NAME_ASC("track_name_asc"),
    TRACK_NAME_DESC("track_name_desc");
}
```

Behavior:

| Mode | Ordering | Direction | Pagination |
|---|---|---|---|
| Play count | `playCount`, then `title.lowercase()`, then `id` | asc/desc, default desc | SQL keyset |
| Random | `shuffled(Random(seed))` | none | full catalog |
| Album name | album blocks ordered by album name; within block `disc → track → title → id` | asc/desc flips album blocks only | full catalog |
| Album year | album blocks by `cached_albums.year`; null years last in **both** directions; tie by album name | asc/desc flips album blocks only | full catalog |
| Track name | `title.lowercase()`, then `id` | asc/desc | SQL keyset |

- Unknown/bogus stored key → `PLAY_COUNT_DESC`.
- Switching mode chooses its natural direction: play count → desc; album name/year/track name → asc.
- Random ignores direction. Reselecting Random generates a new seed and reshuffles. Leaving/reopening the artist also reshuffles.
- Album name/year are **album-block** sorts, matching `ArtistPlayOrderer` semantics. Track name and play count are flat sorts.

### Album tracks

```kotlin
enum class AlbumTrackSort(val key: String) {
    DISC_TRACK("disc_track"),              // default
    PLAY_COUNT_DESC("play_count_desc"),
    PLAY_COUNT_ASC("play_count_asc");
}
```

- `DISC_TRACK`: current comparator unchanged:
  `discNumber ?: MAX → trackNumber ?: MAX → title.lowercase() → id`
- `PLAY_COUNT_*`: `playCount` first, then the same disc/track/title/id tie-breakers.
- If every track has 0 plays, play-count sorting falls through to normal track order.

## 2. Persistence

Add to `SecureStorage.kt` next to `KEY_ARTIST_PLAY_ORDER`:

```kotlin
const val KEY_ARTIST_SONGS_SORT = "artist_songs_sort"
const val KEY_ALBUM_TRACK_SORT = "album_track_sort"
```

- `ArtistDetailViewModel` reads/writes `KEY_ARTIST_SONGS_SORT` directly, like it already reads `KEY_ARTIST_PLAY_ORDER`.
- `SettingsUiState` gains `albumTrackSort: AlbumTrackSort`; loaded in `SettingsViewModel.refresh()`, persisted by `setAlbumTrackSort(...)`.
- No new Room columns, no migration.

## 3. Artist Songs architecture

### 3.1 Pagination strategy

Do **not** load the full catalog for the default list.

| Sort mode | Strategy |
|---|---|
| `PLAY_COUNT_DESC/ASC` (default) | Room keyset pagination, 50/page |
| `TRACK_NAME_ASC` | Existing `(title,id)` keyset |
| `TRACK_NAME_DESC` | New descending `(title,id)` keyset |
| `ALBUM_NAME_*`, `ALBUM_YEAR_*` | Load full artist-view catalog once, group/sort in memory, paginate 50-row slices |
| `RANDOM` | Load full catalog once, shuffle with per-generation seed, paginate slices |

Why:
- Play count is the default, so it must keep the cheap 50-row first paint.
- Album-name/year need album grouping and null-year handling; a correct keyset would add significantly more SQL and risk.
- Random cannot be stably paginated from SQL.
- Full-catalog loading already exists for Play All / Download All, so it is acceptable for explicit non-default modes.

Known tradeoff: play-count increments can move a row across a keyset boundary while the user scrolls. Mitigate by:
- keeping list order frozen for the current load,
- deduplicating appended pages by track id,
- using a generation guard so a sort change discards stale pages.

### 3.2 DAO changes

In `Daos.kt` / `TrackDao`:
- Extract the repeated artist-view membership `UNION` into a file-level `const` string and reuse it. Behavior-neutral.
- Keep existing:
  - `getTracksForArtistView` / `...After` — title asc
- Add:
  - `getTracksForArtistViewByTitleDesc` / `...AfterByTitleDesc`
  - `getTracksForArtistViewByPlayCountDesc` / `...AfterByPlayCountDesc`
  - `getTracksForArtistViewByPlayCountAsc` / `...AfterByPlayCountAsc`

Play-count desc order and cursor:

```sql
ORDER BY t.play_count DESC, t.title ASC, t.id ASC

AND (
  t.play_count < :cursorPlayCount
  OR (t.play_count = :cursorPlayCount AND t.title > :cursorTitle)
  OR (t.play_count = :cursorPlayCount AND t.title = :cursorTitle AND t.id > :cursorId)
)
```

Ascending is the mirrored `>` form. Title-desc uses:

```sql
(t.title < :cursorTitle OR (t.title = :cursorTitle AND t.id > :cursorId))
```

No new indices. Existing `tracks.play_count` index plus existing membership paths are sufficient for v1.

### 3.3 `ArtistViewMembership`

Add a paged entry point that hides the strategy:

```kotlin
suspend fun loadSongsPage(
    trackDao, artistId, artistName, sort, limit, cursor,
): List<TrackEntity>
```

- `PLAY_COUNT_*` / `TRACK_NAME_*` → corresponding DAO keyset call.
- Other modes → `loadAllTracks(...)`; caller handles sorting/slicing.

### 3.4 `ArtistDetailViewModel`

Add to `ArtistDetailState`:

```kotlin
val songsSort: ArtistSongsSort = ArtistSongsSort.PLAY_COUNT_DESC
val isLoadingSongs: Boolean = false
```

Private state:
- `songsGeneration: Int`
- `songsJob: Job?`
- `songsCursor: ArtistSongsCursor?`
- `songsSource: List<Track>?` for full-load modes
- `randomSeed: Int`
- `seenTrackIds` for dedupe

Flow:
1. `loadArtist()` reads `KEY_ARTIST_SONGS_SORT`; sets `songsSort` before first load.
2. Default / track-name modes load page 1 through the new DAO queries.
3. Album-name/year/random load full catalog via `ArtistViewMembership.loadAllTracks`.
   - Build `albumMetaById` with `CachedMetadataDao.getAlbumsByIds(...)` (chunked at 500 ids), fallback to `track.album`.
   - Sort with `withContext(Dispatchers.Default)`.
   - Show first 50, keep sorted source for load-more.
4. `setSongsSort(sort)`:
   - persist to `KEY_ARTIST_SONGS_SORT`,
   - increment generation,
   - cancel in-flight song job,
   - clear tracks/cursor/source, set `hasMoreTracks = true`,
   - reload page 1.
5. `loadMoreTracks()`:
   - keyset modes: fetch next page with stored cursor, dedupe by id, update cursor from the **raw** last row.
   - full-load modes: append next slice of `songsSource`.
   - every write checks `gen == songsGeneration`.
6. `toTrack()` maps `playCount` and `lastPlayedAt`.
7. `playTrack(index)` keeps using `state.tracks`, so tap-through follows the current browse sort.
8. `playAll()`, `playNextAll()`, `addAllToQueue()`, `downloadAll()`, `catalogTrackIds()` remain untouched and keep using `KEY_ARTIST_PLAY_ORDER`.
9. No live re-sorting when a play count increments. Reopen or change sort to refresh.

## 4. Album track architecture

`AlbumDetailViewModel`:
- Read `KEY_ALBUM_TRACK_SORT` at the start of `loadAlbum`; default `DISC_TRACK`.
- Cached path:
  1. map `CachedAlbumTrackEntity → Track`,
  2. batch-fetch counts with `trackDao.getTrackPlayCounts(ids)`,
  3. missing id → `0`,
  4. sort via `AlbumTrackSorter`.
- API path:
  1. map `AlbumWithTracks.tracks`,
  2. same batch play-count lookup,
  3. sort via `AlbumTrackSorter`.
- There is no play count in `CachedAlbumTrackEntity` and no schema change. The `tracks` table remains the source of truth.
- `getTrackPlayCounts` intentionally returns only `play_count > 0`; missing rows map to 0. Do **not** remove that filter.
- Chunk the id list at 500 if an album exceeds the SQLite bind limit.
- Do **not** re-sort in `applyTrackStatus` / `watchTracksByIds`. Play-count changes should not make the list jump while the user is looking at it. New order applies on next `loadAlbum`.
- Album `playAll` / `shuffle` naturally use the sorted `state.tracks`, so the album setting also controls queue order.

## 5. Shared sorting code

Create:
- `data/sort/AlbumMeta.kt` (simple `name`, `year`)
- `data/sort/ArtistSongsSorter.kt`
- `data/sort/AlbumTrackSorter.kt`
- `data/sort/AlbumTrackOrdering.kt`

`AlbumTrackOrdering` contains:
- `withinAlbumComparator` — disc/track/title/id, nulls last
- `groupByAlbum` — albumId grouping, orphan singleton groups

`ArtistPlayOrderer` delegates to this helper. Behavior-neutral; prevents three divergent within-album comparators.

Do not extend `ArtistPlayOrderer` with browse-only modes.

## 6. UI

### Artist Songs tab

`ArtistDetailScreen.TracksTab`:
- Add a compact sort trigger above the list:
  - label like `Most played first`, `Album name · A–Z`, `Random`
  - sort icon
  - `contentDescription = "Sort songs, current: …"`
- Opens a `ModalBottomSheet`:
  - five mode rows: Play count, Random, Album name, Album year, Track name
  - direction chips when the mode supports direction:
    - Play count: `Most played first` / `Least played first`
    - Album name: `A–Z` / `Z–A`
    - Album year: `Oldest first` / `Newest first`
    - Track name: `A–Z` / `Z–A`
  - direction chips hidden for Random
- On change: scroll list to top.
- Test tags:
  - `artist_songs_sort_button`
  - `artist_songs_sort_sheet`
  - `artist_songs_sort_${key}`
  - `artist_songs_sort_dir_asc|desc`
- Use existing dark palette: muted label, `BrandTeal` selected, existing `SegmentedChip` styling.
- Add `selectableGroup()` / radio semantics for accessibility.

### Settings → Appearance

In `AppearanceSettingsScreen`, after the existing list-chrome card:
- `Spacer`
- `SectionLabel("Album track order")`
- `SectionCard` with explanatory text and three `OverwriteBehaviorOption` rows:
  - Track number — "Disc then track number, ascending" (default)
  - Most played first
  - Least played first
- Test tags: `settings_album_track_sort_${key}`.
- Add `SettingsAnchors.ALBUM_TRACK_SORT` and `Modifier.settingsScrollAnchor` on the section.
- Update `SettingsIndex` appearance keywords: `"sort"`, `"album order"`, `"track order"`, `"play count"`, `"most played"`, `"least played"`, `"disc"`.
- Optionally map those keywords to the new anchor.

## 7. Tests

New:
- `data/sort/ArtistSongsSortTest.kt` — `fromKey`, direction helpers, defaults.
- `data/sort/ArtistSongsSorterTest.kt` — play-count desc/asc, zero-play placement, ties, album-name blocks, year nulls-last both directions, track-name asc/desc, random deterministic with seed, orphan albums, empty list.
- `data/sort/AlbumTrackSortTest.kt` — `fromKey` default, legacy/unknown keys.
- `data/sort/AlbumTrackSorterTest.kt` — default matches old comparator; play-count desc/asc; all-zero falls back to track order.
- Compose test for the artist sort sheet, if existing artist test infrastructure supports it.

Update:
- `ArtistDetailViewModelTest`
  - default unset pref → play-count desc query
  - saved pref → play-count asc / track-name / random
  - full-load mode shows first 50 and load-more appends
  - sort change resets page and drops stale load-more
  - random stable across load-more
  - `toTrack()` maps `playCount`
  - `playAll` still reads `KEY_ARTIST_PLAY_ORDER`
  - existing title-pagination tests set `KEY_ARTIST_SONGS_SORT = "track_name_asc"` and keep keyset assertions
- `AlbumDetailViewModelTest`
  - default disc/track unchanged
  - cached tracks get play counts from batch lookup
  - API tracks get play counts from batch lookup
  - missing ids → 0
  - play-count desc/asc ordering
  - offline path
- `SettingsViewModelTest` — load/save `KEY_ALBUM_TRACK_SORT`.
- `DaosRoomTest` — new play-count order + cursor behavior with duplicate counts/titles; existing artist-view queries still pass after SQL-const refactor.
- Settings index/compose tests for new Appearance rows if they assert section contents.

Target: keep Kover/JaCoCo ≥80% for new code.

## 8. Docs

- New ADR `docs/adr/0152-artist-songs-sort-and-album-track-order.md`
  - two global prefs
  - default play-count desc for artist
  - album default disc/track
  - pagination strategy and tradeoffs
  - Play All independence
  - no Room migration
- New `docs/ARTIST_SONGS_SORT_BEHAVIOR_REPORT.md`
- Amend `docs/adr/0141-artist-play-all-order.md` consequence "UI Songs tab pagination unchanged" to note the new independent browse sort.
- Amend `docs/adr/0145-artist-track-membership-split.md` if needed for album-block browse semantics.
- Update `docs/ARTIST_DETAIL_UI_BEHAVIOR_REPORT.md` and `docs/SETTINGS_BEHAVIOR_REPORT.md`.

## 9. Edge cases

| Case | Behavior |
|---|---|
| Zero plays | Desc: last; asc: first |
| Equal play counts | Stable by title, then id |
| Null/blank albumId | Each track its own album block; sort by title/id |
| Null album name | Empty name sorts naturally in album-name mode; document tie behavior |
| Null release year | After dated albums in both directions |
| Random | Stable for current list generation; reselect Random to reshuffle |
| Sort change during load-more | Generation guard drops stale page |
| Duplicate rows across pages after count increment | Deduped by id; possible skipped row documented |
| Process death | Preference persists; list reloads and re-shuffles if Random |
| Offline | All sorting uses Room; no network |
| All album counts zero | Falls back to disc/track order |
| Album setting changed while album open | Applies on next album open; no jump |
| Large artist | Default stays SQL-paginated; album-block/random modes load full catalog |
| Case differences | In-memory uses `lowercase()`; SQL title order remains existing BINARY behavior; known minor edge |

## 10. Risks and mitigations

1. **Play-count keyset drift** — rare duplicate/skip if a count changes mid-scroll. Mitigate with dedupe + generation guard; document.
2. **Full-catalog modes memory** — only for album-name/year/random; sort on `Dispatchers.Default`; do not use for default.
3. **Album live jump** — explicitly avoided; no re-sort in `applyTrackStatus`.
4. **Test churn** — old artist pagination tests need `track_name` pref or updated mocks.
5. **SQL duplication** — extract membership `UNION` to one const; Room compile/tests validate.
6. **Settings not reactive across screens** — detail screens read `SecureStorage` directly at load; setting applies on next open.
7. **Comparators diverging** — shared `AlbumTrackOrdering` extraction happens first.
8. **Random reselect semantics** — explicit, tested, no persisted seed.

## 11. Implementation order

1. Shared `AlbumTrackOrdering` extraction + behavior-neutral tests.
2. Sort enums, `SecureStorage` keys, pure sorters + unit tests.
3. Album play-count lookups, sorter wiring, Appearance setting + tests.
4. Artist DAO play-count/title-desc keysets + `loadSongsPage` + Room tests.
5. `ArtistDetailViewModel` default sort, pagination/generation, full-load modes + tests.
6. Artist sort sheet UI + Compose/accessibility tests.
7. Settings index keywords/anchors, docs (ADR 0152 + behavior reports), final coverage pass.
8. Run `assembleDebug`, targeted tests, ktlint; then Phase-5 DeepSeek audit + Mistral challenge before delivery.

## 12. Cross-model review outcome

- **Accepted** Mistral's valid criticisms: no full-catalog load for the default; no live album re-sort; explicit random semantics; explicit Play All independence; generation guards and dedupe.
- **Rejected** Mistral's incorrect suggestions: adding `play_count` to `cached_album_tracks` (unnecessary schema duplication), removing `getTrackPlayCounts`'s `>0` filter (missing ids already map to 0), and reusing `KEY_ARTIST_PLAY_ORDER` for the new browse sort (would couple browse and Play All).
- **Synthesized** the pagination disagreement: SQL keyset for the default play-count and track-name modes; full-catalog only for album-block and random modes, where keyset pagination is not practical.

## 13. Files expected to change

New:
- `data/sort/AlbumMeta.kt`
- `data/sort/ArtistSongsSort.kt`
- `data/sort/ArtistSongsSorter.kt`
- `data/sort/AlbumTrackSort.kt`
- `data/sort/AlbumTrackSorter.kt`
- `data/sort/AlbumTrackOrdering.kt`
- `docs/adr/0152-artist-songs-sort-and-album-track-order.md`
- `docs/ARTIST_SONGS_SORT_BEHAVIOR_REPORT.md`
- `docs/PLAN_ARTIST_SONGS_SORT_AND_ALBUM_TRACK_ORDER.md` (this file)
- Tests listed in §7.

Modified:
- `data/security/SecureStorage.kt`
- `data/db/Daos.kt`
- `data/db/ArtistViewMembership.kt`
- `playback/ArtistPlayOrderer.kt`
- `ui/artist/ArtistDetailViewModel.kt`
- `ui/artist/ArtistDetailScreen.kt`
- `ui/album/AlbumDetailViewModel.kt`
- `ui/settings/SettingsViewModel.kt`
- `ui/settings/AppearanceSettingsScreen.kt`
- `ui/settings/SettingsIndex.kt`
- `ui/settings/SettingsAnchors.kt`
- `ui/NavHost.kt` (appearance `scrollToAnchor` — see §14)
- Existing tests/docs listed in §7–§8 (broader churn — see §14).

No Room entity or `AppDatabase` changes.

---

## 14. Validation amendment (2026-11-03, docs-only)

Independent source re-trace of §§1–13 against current `main`, plus cross-model
challenge. **No production code was changed in this pass.** Treat this section
as the authoritative pre-implementation checklist; do not code from §§1–13 alone.

### 14.1 Method and verdict

| Stage | Verdict |
|---|---|
| Static source validation | Mostly sound; several must-fix corrections (N1–N3, cursors, ADR, wiring) |
| Challenger audit | **NOT OK** as verbatim implementation spec |
| Orchestrator synthesis | Direction OK; §14 checklist required before coding |

**Framing:** there is no open “broken sort” bug. Artist Songs today are
intentionally title A–Z (ADR-0141). Album detail already defaults to
disc → track. This plan is a **product default / feature change** (Songs → most
played; optional album play-count mode), not a defect fix.

### 14.2 Verdict table (plan sections)

| Section | Verdict | Notes |
|---|---|---|
| Goal / decisions | VERIFIED_WITH_CORRECTION | Tap-through = **loaded slice** only, not full discography. Split from Play All intentional. |
| §1 Artist Songs enums | VERIFIED_WITH_CORRECTION | Album-block modes must match `ArtistPlayOrderer` (`track.album` + year rows), not a divergent `cached_albums.name` path. |
| §1 Album tracks | VERIFIED | Current comparator exact; default unchanged. All-zero “fallback” is emergent from tie-breakers — drop special branch. |
| §2 Persistence | NEEDS_CORRECTION | Album pref loads in `SettingsViewModel` **init**/initial state, not `refresh()`. Artist screen **writes** songs pref (new vs today’s read-only Play All). |
| §3.1 Pagination strategy | PARTIALLY_VERIFIED | Strategy OK; “cheap 50-row / play_count index sufficient” overstated — membership UNION already dominates cost. |
| §3.2 DAO / cursors | NEEDS_CORRECTION | Spell full `ORDER BY` + cursors; ASC flips **only** `play_count`; title-desc needs `ORDER BY title DESC, id ASC`. |
| §3.3 `loadSongsPage` | NEEDS_CORRECTION | Prefer keyset DAO for flat modes; album/random full-load in ViewModel (reuse `ArtistPlayOrderer` for blocks). Avoid dual-purpose “sometimes full catalog” page API footgun. |
| §3.4 ViewModel | NEEDS_CORRECTION | `toTrack` must map `playCount`/`lastPlayedAt`; generation cancel on `loadArtist` too; `hasMore` from raw page; N1 dedupe loop; store membership name used on first page. |
| §4 Album VM | NEEDS_CORRECTION | Pref in 3-arg `loadAlbum`; isolate play-count lookup try/catch; skip when `DISC_TRACK`; never put play-count into shared `getAlbumTracks` SQL (Auto / `expandToAlbum`). |
| §5 Shared ordering | VERIFIED_WITH_CORRECTION | Extract within-album comparator; do **not** change orphan/null-disc semantics (nulls last; do not coerce null disc → 1). |
| §6 Artist UI | NEEDS_CORRECTION | Random reselect must reshuffle (not only “on change”); string resources; N1 interacts with `LaunchedEffect(tracks.size)`. |
| §6 Appearance | NEEDS_CORRECTION | Needs `scrollToAnchor` + `NavHost` pass-through; `keywordAnchors`; wrap rows for test tags. |
| §7 Tests | NEEDS_CORRECTION | Churn larger than described; no ArtistDetail Compose infra; pin pagination tests to `track_name_asc`; assert SQL BINARY order for keyset modes. |
| §8 Docs | NEEDS_CORRECTION | Amend **0145** (and cite 0142), not only 0141; fix “No Popular ranking” line in artist UI report. |
| §9 Edge cases | NEEDS_CORRECTION | Orphan “sort by title/id” wrong vs today; Shuffle excluded from album setting; VA membership unchanged. |
| §10–§11 Risks / order | VERIFIED_WITH_CORRECTION | Add N1/N2/N3 into step 5; NavHost into step 7. |
| §12 Prior Mistral | VERIFIED | Rejected schema/`>0`/shared-key suggestions remain correct. |
| §13 Files | NEEDS_CORRECTION | Add `NavHost.kt` and broader test updates. |

### 14.3 Must-fix corrections before coding

1. **SQL is source of truth for play-count and track-name modes.** Do not define those modes via in-memory `title.lowercase()` in `ArtistSongsSorter` except as an explicit test oracle with documented BINARY divergence. Flat SQL must **not** add disc/track to `ORDER BY` or the cursor.

2. **Full cursor SQL (no “mirrored >” ambiguity).**

Play-count DESC:

```sql
ORDER BY t.play_count DESC, t.title ASC, t.id ASC
-- cursor:
-- play_count < :c
-- OR (play_count = :c AND title > :t)
-- OR (play_count = :c AND title = :t AND id > :i)
```

Play-count ASC (only the play_count comparison flips):

```sql
ORDER BY t.play_count ASC, t.title ASC, t.id ASC
-- play_count > :c
-- OR (play_count = :c AND title > :t)
-- OR (play_count = :c AND title = :t AND id > :i)
```

Title DESC:

```sql
ORDER BY t.title DESC, t.id ASC
-- title < :t OR (title = :t AND id > :i)
```

Advance cursor from the **raw page’s last row**. `hasMoreTracks = rawPage.size >= pageSize` (not deduped size).

3. **Album-name / album-year browse:** load full membership once, then
   `ArtistPlayOrderer.order(...)` with `getAlbumYearRows` (chunk **500**,
   `SQLITE_IN_CHUNK`) and name key `track.album`. Do **not** prefer
   `cached_albums.name` if that diverges from Play All.

4. **Orphans:** keep stable first-seen / empty-name-key behavior. Do not put
   “orphan → title/id” into the shared helper (would change Play All).

5. **Album setting vs Shuffle:** appearance play-count controls
   `playAll` / `playNextAll` / `addToQueue` / row-tap list order only.
   `shuffle()` remains `tryShuffleContext`. Document that download-all walking
   `state.tracks` will enqueue in play-count order when that mode is on.

6. **`getAlbumTracks` stays disc/track** for Auto browse and assistant
   `expandToAlbum`. Apply play-count only in `AlbumDetailViewModel` after
   mapping + `getTrackPlayCounts`.

7. **N1 — load-more stall:** if a page dedupes to zero new rows,
   `LaunchedEffect(tracks.size)` never re-fires. Loop inside `loadMoreTracks`
   until ≥1 new row or end of catalog, and/or key the effect on a page/cursor
   counter.

8. **N2 — isolate play-count lookup** in `loadAlbum` (own try/catch → 0);
   skip when sort is `DISC_TRACK`; read pref in the **3-arg** overload; use
   `track.copy(playCount = …)` on cached mappings.

9. **N3 — `loadArtist` generation:** cancel `songsJob` and bump generation on
   every `loadArtist` (not only `setSongsSort`) so artist A’s full-catalog load
   cannot commit after navigating to B.

10. **N4 — tap-through queue = loaded pages only** (`state.tracks`). Goal text
    must not imply the rest of the discography continues in sort order.

11. **Random:** re-selecting the already-selected Random row reshuffles (new
    seed). Load-more within that generation stays stable.

12. **Drop special all-zero comparator branch** — equal/zero counts already fall
    through disc→track→title→id tie-breakers.

13. **Settings:** load `albumTrackSort` in init/initial state; Appearance needs
    `scrollToAnchor` + `NavHost` argument pass-through + index keywords/anchors;
    wrap options for `testTag` (component has no modifier).

14. **`toTrack()`** maps `playCount` and `lastPlayedAt`. Store the exact
    membership `artistName` used on first page (avoid `""` vs `"Unknown"` drift
    on load-more).

15. **Chunk size 500** for album-id batches (match `Daos.kt` `SQLITE_IN_CHUNK`).
    Prefer a DAO helper that chunks internally if the const stays private.

16. **String resources** for sheet/Appearance copy (avoid English-only literals
    in production UI). Soften “cheap first paint” language in ADR/behavior report.

17. **Room `@Query` UNION const extract:** no local precedent; do as a small
    compile-first step. If KSP rejects templates, keep duplicated SQL.

### 14.4 New discoveries

| ID | Severity | Finding |
|---|---|---|
| N1 | High | Full-dedupe page stalls load-more via `LaunchedEffect(tracks.size)`. |
| N2 | Med–High | Unguarded `getTrackPlayCounts` can abort `loadAlbum` and skip API. |
| N3 | Medium | `loadArtist` must cancel/bump song generation (cross-artist race). |
| N4 | Low–Med | Tap-through / Cast from row-tap = loaded slice only. |
| N5 | Low | Prefer `getAlbumYearRows` + `track.album` over full `getAlbumsByIds`. |
| N6 | Low | Sequence chrome becomes 1..n rank under play-count; album track-number labels become non-monotonic in play-count mode — document in behavior report. |
| N7 | Docs | Amend ADR-0141 L32 and ADR-0145 L38; cite ADR-0142. |
| — | Latent | Membership name `""` vs state `"Unknown"` on later pages. |
| — | Test | Default flip breaks many artist ViewModel mocks; no ArtistDetail Compose test target today. |

### 14.5 ADR / docs impact

- Create ADR-0152 as planned; cite ADR-0142 membership.
- Amend ADR-0141 consequence “UI Songs tab pagination unchanged.”
- Amend ADR-0145 “Songs tab and Play All stay aligned” → browse sort and Play All
  are **independent** after this feature; new Songs default is play-count desc.
- Update `ARTIST_DETAIL_UI_BEHAVIOR_REPORT.md` (“No Popular ranking…”).
- Update `SETTINGS_BEHAVIOR_REPORT.md` for Appearance album order.
- Behavior report must state: Auto/`expandToAlbum` ignore album play-count pref;
  album Shuffle ignores it; two different Random surfaces (Songs Random vs Play All).

### 14.6 Residual risks

1. Play-count keyset drift mid-scroll (skip/dup) — dedupe + generation mitigate;
   missing row returns on reopen.
2. Full-catalog album-block/random hitch on huge / VA catalogs.
3. UX surprise: Songs default flips from title A–Z → most played (product, not bug).
4. Album track-number labels vs play-count order; artist sequence stays 1..n.
5. Room const-SQL extract may fail compile — fallback to duplicated queries.
6. Preference disagreement with Android Auto / assistant album expansion — acceptable
   only if documented.

### 14.7 Implementation checklist deltas

Keep §11 order, with these inserts:

- **Step 5 (Artist VM):** N1 dedupe loop + raw `hasMore`/cursor; N3 `loadArtist`
  cancel/gen; membership name freeze; `toTrack` playCount; Random reselect;
  pin existing pagination tests to `KEY_ARTIST_SONGS_SORT=track_name_asc`.
- **Step 3 (Album):** N2 isolated lookup; Shuffle unchanged; assert Auto/
  `getAlbumTracks` still disc/track when pref is play-count.
- **Step 7 (UI/docs):** `NavHost` + Appearance anchor; string resources; ADR
  0141/0145/0152 + behavior reports; Room cursor tests against SQL order
  (duplicate counts/titles); Play All fixtures unchanged after comparator
  extraction; second-artist drops in-flight load; offline cached album counts;
  VA foreign-album subset inside blocks.

### 14.8 Canonical cursors and product rules (summary)

```
Browse Songs  ≠  Play All (KEY_ARTIST_PLAY_ORDER)
Tap-through   =  loaded state.tracks window only
Album pref    →  AlbumDetailViewModel sort only (not getAlbumTracks SQL)
Album Shuffle =  always shuffle, ignoring album track-sort pref
Orphans       =  stable empty-name blocks (Play All parity)
SQL flat modes = BINARY title; no disc/track in ORDER BY
```

### 14.9 Outcome

- §§1–13 remain the historical proposed plan.
- **§14 is authoritative for implementation.**
- Cross-model verdict on the unamended plan: **NOT OK**; closed by encoding the
  accepted corrections here.
- Still no production code changed.
- Ready to implement only when the working checklist includes every item in
  §14.3 and §14.7.

## 15. Implementation status (2026-11-03)

Shipped against §14. Summary of what landed:

- `data/sort/`: `ArtistSongsSort` / `ArtistSongsMode` / `ArtistSongsCursor`,
  `ArtistSongsSorter`, `AlbumTrackSort`, `AlbumTrackSorter`,
  `AlbumTrackOrdering` (shared with `ArtistPlayOrderer`, which now delegates).
- `Daos.kt`: `ARTIST_VIEW_MEMBERSHIP_SQL` const used by every artist-view
  query; play-count DESC/ASC + title-DESC first-page and cursor queries exactly
  as §14.3 item 2.
- `ArtistViewMembership`: `loadSongsKeysetPage` (throws away non-keyset sorts),
  `loadAlbumYears` (500-id chunks).
- `ArtistDetailViewModel`: generation guards on every commit path (including
  the catch block), frozen membership name, sort state, N1 chase loop with cap
  termination, `toTrack` maps `playCount`/`lastPlayedAt`, Random reseed.
- `AlbumDetailViewModel`: `prepareAlbumTracks` / `applyPlayCounts` with N2
  isolation and 500-id chunks; Shuffle and `getAlbumTracks` untouched.
- Settings: `KEY_ALBUM_TRACK_SORT` loaded in init/refresh, Appearance section +
  anchor + index keywords + NavHost pass-through + test tags.
- Artist UI: sort bar + sheet, string resources, list reset on sort change.

Verification: `:app:compileDebugKotlin`, `:app:assembleDebug`,
`:app:ktlintCheck` pass; targeted unit/Robolectric suites listed in
`docs/ARTIST_SONGS_SORT_BEHAVIOR_REPORT.md` pass. The N3 cross-artist race is
pinned by a mutation-checked test (guard removal fails it).

Docs: ADR-0152 created; ADR-0141 + ADR-0145 amended; artist detail, artist play
order and settings behavior reports updated.
