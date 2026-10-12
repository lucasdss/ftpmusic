# Desired Wishlist — Behavior Report

Date: 2026-10-12
Related: ADR 0129, ADR 0130, ADR 0020 (status note), ADR 0082 (amended)

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

`desired_items` (DB v64):

- `id` UUID PK
- `entity_type` ARTIST | ALBUM | TRACK
- `title`, `artist_name?`, `album_name?`
- `musicbrainz_id?`, `deezer_id?`
- `source` musicbrainz | lastfm | deezer | manual | library
- `desired_at`, `matched_local_id?`, `note?`

### Identity / upsert

1. `(entity_type, musicbrainz_id)` when MBID present
2. else `(entity_type, deezer_id)`
3. else normalized `(entity_type, title, artist_name)` display key

Idempotent add. Store both external ids when known. No cross-provider dupes.

### Fulfillment (v1)

Bandcamp-style: reconcile after metadata sync sets `matched_local_id` when
library match found (MBID then exact name). UI shows **In library** badge.
User removes manually — no auto-delete.

## Surfaces

### Add

- Search Discover rows not in library (artist / track / album) → Add Desired
- Favorites Desired mode: query ≥2 + online → **Add from catalog** subsection
- Stub / empty artist or album detail → Add Desired

### Manage (Favorites Desired)

- Filter search over full local Desired set (Room LIKE)
- Liked/Disliked filter = loaded window only (documented)
- Sections: Tracks → Artists → Albums
- Remove clears row
- Empty: “Nothing desired yet”

### Share

Toolbar Share on Desired mode → `ACTION_SEND` `text/plain` chooser.

Format:

```
ftpmusic desired list
count=N
capturedAt=<ISO>
---
[TRACK] Title — Artist (source=deezer mbid=… deezer=…)
[ALBUM] Title — Artist …
[ARTIST] Name …
```

No upload. Same Intent pattern as diagnostics (ADR 0048).

## Discover providers (ADR 0130)

After local paint, online + not local-only:

1. MusicBrainz artists/recordings
2. Last.fm artists (or tag tops)
3. Deezer artists/albums/tracks (token-bucket; body quota code 4 → backoff)
4. ListenBrainz: popularity boost for rows with MBID only (lazy cache; hide if missing)

## Edge cases

- Offline: Desired list + filter work; Add from catalog / Discover add skip with explained empty
- Process death: Room truth
- Double-tap add: upsert, one row
- Deezer without MBID: library match fuzzy name — false “not in library” possible
- Device loss: list local-only; share text is backup path

## Non-goals

- Discogs / Spotify Web API
- Server-synced wantlist
- Auto-remove on library acquire
- Purchase / marketplace alerts
