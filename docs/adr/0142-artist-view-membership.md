# 0142 — Artist View Membership (Albums ↔ Tracks)

Status: Accepted (track whole-album rule superseded by ADR-0145)
Date: 2026-10-14
Extends: ADR-0141 (artist Play All order), ADR-0069 (artist albums-first)
See also: ADR-0145 (Songs / Play All filter foreign albums to this artist only)

## Context

Artist Albums tab merged three sources (album `artist_id`, album name,
track-level attribution via `cached_album_tracks`). Songs / Play All only used
`tracks.artist_id`, so visible albums could contribute zero Play All tracks.
Download All / Add to Playlist used the paginated Songs page (often 50).

## Decision

1. Single membership helper: `ArtistViewMembership` + Room queries
   `getAllTracksForArtistView` / count / paged variants.
2. A track belongs to the artist view if:
   - `tracks.artist_id` = artist, **or**
   - its `album_id` is in the same merged album set as the Albums tab
     (album artist id / name / any `cached_album_tracks` row for this artist).
3. Albums tab loads albums via `ArtistViewMembership.loadAlbums` (same three
   queries as before, one call site).
4. Songs tab pages with the view query; Play All / Play Next All / Add All to
   Queue / Download All / Add to Playlist always use the **full** view set.
5. `catalogTrackCount` = `countTracksForArtistView`.

## Consequences

- ~~OST / VA albums contribute all tracks to Play All~~ — superseded by ADR-0145
  (foreign albums → this artist’s tracks only; Albums tab still merges).
- Large catalogs: full-set actions may hitch; `isPreparingPlay` gates chrome.
