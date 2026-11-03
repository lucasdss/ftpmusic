# Artist Play All Order — Behavior Report

Status: Implemented (ADR-0141 / ADR-0145)
Date: 2026-10-15

## Market scan

| Client | Artist Play | Album-aware / chronological |
|--------|-------------|-----------------------------|
| Apple Music (Mac) | Shuffle → **Albums** keeps track order inside albums | Sort Year → Oldest / Newest |
| Symfonium | Artist Play ≈ song shuffle; weighted Shuffle option | Sort albums by year then Play |
| YouTube Music | Shuffle primary on artist | Album play = track order |
| Spotify | Artist ≈ radio/curated | Album Play = track order |

Takeaway: default **Shuffle albums** matches Apple; song shuffle remains optional; year/name directions match Apple Sort.

## After (ADR-0145)

- Artist header: **Play All only** (Shuffle button removed). Player shuffle unchanged.
- Settings → **Artist Play All** (default **Shuffle albums**):
  - Shuffle albums — random album blocks; tracks 1…N inside
  - Shuffle all songs — full catalogue random
  - Albums by year — Oldest first / Newest first (chips)
  - Albums by name — A–Z / Z–A (chips)
- Within album (non–song-shuffle): `disc → track → title → id`.
- Play All / Play Next All / Add All to Queue load **full** artist catalog from Room.
- Songs tab browse order is now its own preference (ADR-0152, default most
  played first); Play All order stays independent.
- **No** Song title A–Z *Play All* option (legacy key → albums A–Z). The Songs
  **browse** list does have track-name A–Z since ADR-0152.

## Membership (ADR-0145)

| Surface | Rule |
|---------|------|
| Albums tab | Merged: album id / name / track attribution |
| Songs / Play All / Download All / count | Own albums → all tracks; foreign albums → this artist only |

## Edge cases

| Case | Behavior |
|------|----------|
| Null album year | After dated albums; among nulls, album name A–Z |
| Null albumId / empty album | Own synthetic group; track order alone |
| Empty catalog | Play All no-op |
| Large catalog | Full load on IO; Play All disabled while preparing |
| Process death | Preference in SecureStorage |
| Overwrite modal | Song shuffle → tryShuffleContext; album modes → tryStartContext |
| Sheet / toast count | Uses `catalogTrackCount` (full artist-view count) |
| Download All / Add to Playlist | Full artist-view catalog (ADR-0145) |

## Browse sort vs Play All (ADR-0152, 2026-11-03)

| Surface | Preference | Default | Modes |
|---------|-----------|---------|-------|
| Artist **Songs** list + tap-through window | `artist_songs_sort` | play count desc | play count, random, album name, album year, track name (± direction) |
| **Play All** / Play Next All / Add All | `artist_play_order` | shuffle albums | shuffle albums, shuffle songs, album year, album name |

- Changing the Songs sort never mutates `artist_play_order`, and vice versa.
- Membership is shared; only the order differs.
- Album-block / random Songs modes reuse `AlbumTrackOrdering`, the same helper
  `ArtistPlayOrderer` uses, so within-album order is identical across surfaces.
- Two distinct Random surfaces: Songs Random = browse shuffle of the list;
  Play All "Shuffle all songs" = playback shuffle.
