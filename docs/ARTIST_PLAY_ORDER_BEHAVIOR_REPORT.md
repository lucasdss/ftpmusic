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
- Songs tab stays title-paginated for browse; playback order independent.
- **No** Song title A–Z option (legacy key → albums A–Z).

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
