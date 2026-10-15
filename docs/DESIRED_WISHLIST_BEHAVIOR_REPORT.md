# Desired Wishlist — Behavior Report

Date: 2026-10-12
Related: ADR 0129, ADR 0130, ADR 0144, ADR 0020 (status note), ADR 0082 (amended)

## Intent

User marks artists / albums / tracks they want on their Navidrome library but
do not have yet. Manage + share from Favorites hub. Distinct from Like (server
star) and Dislike (local reject).

## Market patterns adopted

| App | Pattern |
|-----|---------|
| Discogs Wantlist | Dedicated manage surface; search-to-add; export/share from list |
| Bandcamp wishlist | Keep after acquisition with clear “have it” state |
| RateMe / Crates | Multi-source metadata; local SQLite; export |
| Spotify Liked | Rejected as model — library-only |

## Information architecture

Favorites tab chips: **Liked | Disliked | Desired**.

- Liked / Disliked: existing `FavoriteRepository` ledger (ADR 0020).
- Desired: separate `desired_items` table + `DesiredRepository` (not star fields).

## Storage

`desired_items` (DB v64; enrichment columns v68 / ADR-0144):

- `id` UUID PK
- `entity_type` ARTIST | ALBUM | TRACK
- `title`, `artist_name?`, `album_name?`
- `musicbrainz_id?`, `deezer_id?`
- `source` musicbrainz | lastfm | deezer | manual | library
- `desired_at`, `matched_local_id?`, `note?`
- `image_url?`, `description?` (MB disambiguation / short text at add)
- `extra_json?` (e.g. listenCount for backend)

### Identity / upsert (cross-key merge)

Resolve existing rows in order: MBID → Deezer id → normalized name key
(`mb:TYPE:…` / `dz:TYPE:…` / `nm:TYPE:title|artist`). On add, merge into the
oldest match: coalesce external ids / title / note; keep
`matched_local_id = COALESCE(existing)`; rewrite to the strongest
`identity_key`; delete weaker duplicate rows in one `@Transaction`.
Never blind `DELETE` + `REPLACE` that can clobber another row’s unique key.

Artist/Album detail and Discover use `findMatchingDesired` /
`removeMatching` so a Deezer-added `dz:…` row toggles correctly when the
detail surface only knows MBID or name. Discover badges intersect hit
`probeKeys` with observed row alias keys (`mb:` / `dz:` / `nm:` + stored)
so both mb↔dz directions light correctly. Concurrent adds serialize on a
repository Mutex.

### Fulfillment (v1)

Bandcamp-style: reconcile after metadata sync sets `matched_local_id` when
library match found (MBID → exact name → apostrophe-normalized name →
`search_aliases`). UI shows **In library · {source}**. User removes manually —
no auto-delete.

## Surfaces

### Add

- Search Discover rows not in library (artist / track / album) → Add Desired
- Favorites Desired mode: query ≥2 + online → **Add from catalog** subsection
- Stub / empty artist or album detail → Add Desired

### Manage (Favorites Desired)

- Filter + share chrome **only on Desired mode** (Liked/Disliked ignore
  `filterQuery` — hide the field so it is not a fake filter)
- Desired filter searches the full local set (Room LIKE via `search`)
- Catalog add debounced ≥300ms; offline → short tip, no network calls
- Catalog / Discover artist rows: thumb; origin always
  `In library · {source}` or `Not in library · {source}`; up to 3 lines of
  biography (library cache or Last.fm fanout for top 3 hits); Add/bookmark
  hidden when in library; VM also rejects add when `inLibrary`
- Artist catalog + saved Desired artist rows are clickable → artist detail
  (local id or soft stub with bio)
- Sections: Tracks → Artists → Albums
- Saved rows show thumb + origin + description when persisted
- Album catalog `inLibrary` requires non-blank artist (title-only match is
  rejected — DAO null-artist is a wildcard)
- Remove clears row
- Empty: “Nothing desired yet”

### Share

Toolbar Share on Desired mode → `ACTION_SEND` `text/plain` chooser.

Body is versioned JSON (`schema: "ftpmusic.desired.v2"`, ADR-0144):

- `count`, `capturedAt`, `items[]` with full persisted fields (ids, imageUrl,
  description, matchedLocalId, extra, …)
- `textSummary` — human line format for choosers that show raw text:

```
ftpmusic desired list
count=N
capturedAt=<ISO>
---
[TRACK] Title — Artist (source=deezer mbid=… deezer=…)
[ALBUM] Title — Artist …
[ARTIST] Name …
```

No upload. Same Intent pattern as diagnostics (ADR 0048). Lidarr metadata API
out of scope.

## Discover providers (ADR 0130)

After local paint, online + not local-only:

1. MusicBrainz artists/recordings
2. Last.fm artists (or tag tops)
3. Deezer artists/albums/tracks (token-bucket; body quota code 4 **and**
   HTTP 429 → backoff delay, then empty — not silent empty success)
4. ListenBrainz: popularity for first `DISCOVER_FANOUT` (3) MBID hits only;
   cancel/transport failure are **not** cached as definitive null for 30m

Cancel (`CancellationException`) is rethrown in Discover / Deezer search —
never treated as empty success. Clearing local results also clears
`discoverAlbums`. “No results” waits until `!isDiscoverLoading`.

## Edge cases

- Offline: Desired list + filter work; Add from catalog / Discover add skip with explained empty
- Process death: Room truth
- Double-tap / name→MBID / Deezer→MBID: transactional merge, one row
- Deezer without MBID: library match via normalized name / aliases; residual
  false “not in library” only when spelling differs beyond apostrophe fold
- Device loss: list local-only; share text is backup path
- Album offline with empty tracks: `showAddDesired` still offered

## Non-goals

- Discogs / Spotify Web API
- Server-synced wantlist
- Auto-remove on library acquire
- Purchase / marketplace alerts
