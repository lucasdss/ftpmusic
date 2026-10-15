# 0145 — Artist Track Membership Split + Album-Aware Play All

Status: Accepted
Date: 2026-10-15
Supersedes (partial): ADR-0142 track whole-album rule
Revises: ADR-0141 play-order modes / default

## Context

ADR-0142 made Songs / Play All include **every** track on albums that appeared
via track attribution (VA/OST). That matched Albums-tab consistency but queued
co-artists (e.g. Caetano Veloso Play All ≈ 358 mixed tracks). Users want:

1. Albums tab still lists VA/OST albums.
2. Songs / Play All only play **this artist** on foreign albums; full album when
   the album belongs to this artist.
3. Play All order: shuffle **albums** (default, Apple parity), shuffle all songs,
   albums by year (oldest/newest), albums by name (A–Z / Z–A). Always track
   numbers ascending inside an album except full song shuffle.
4. No song-title A–Z mode.

## Decision

1. **Split surfaces:** `ArtistViewMembership.loadAlbums` unchanged. Track queries
   join attribution on `cached_album_tracks.id = tracks.id` (not album_id).
2. **Play order enum** (`shuffle_albums` default, `shuffle`, `album_year_asc|desc`,
   `album_name_asc|desc`). Legacy `title_asc` → `album_name_asc`.
3. **Orderer** groups by album; within-album sort disc → track → title → id;
   then shuffle or sort album blocks.
4. **Settings UX:** four strategy radios; year/name show `SegmentedChip`
   direction (Oldest/Newest, A–Z/Z–A).
5. **Start path:** `tryShuffleContext` only for song shuffle; album modes use
   `tryStartContext` so player shuffle cannot break album integrity.

## Consequences

- Catalog counts drop for artists heavy on compilations.
- Songs tab and Play All stay aligned; Albums tab can show albums whose Play All
  contribution is a subset of tracks.
- Existing installs with unset preference flip from song shuffle to album shuffle.
