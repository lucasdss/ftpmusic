# Artist Play All Order — Behavior Report

Status: Implemented (ADR-0141)
Date: 2026-10-14

## Market scan

| Client | Artist Play | Chronological discography |
|--------|-------------|---------------------------|
| Symfonium | Artist Play ≈ random (no single “correct” sort) | Sort albums by year → Play |
| Substreamer | Play + Shuffle; top vs full catalogue | No dedicated chronological Play All |
| Spotify | Artist ≈ radio/curated | Album Play = track order |
| Apple Music | Often library albums release order | Oldest→newest reported |
| YouTube Music | Shuffle primary on artist | No reliable album-by-album Play All |

Takeaway: default Shuffle matches Symfonium/YTM; album-year options match Apple Music / Symfonium album-sorted Play.

## Before

- Play All: `ORDER BY title ASC`, **UI page only (50)**.
- Shuffle button: `List.shuffled()` on same partial list.
- No user preference for artist play order.
- `toTrack()` dropped album / disc / track number.

## After

- Artist header: **Play All only** (Shuffle button removed). Player shuffle toggle unchanged.
- Settings → **Artist Play All** (default **Shuffle**):
  - Shuffle — full catalog random
  - Oldest albums first — year ASC, null years last, then album name
  - Newest albums first — year DESC, null years last, then album name
  - Song title A–Z — previous behavior
- Sort key (chronological): `year → album name → disc → track → title → id`.
- Play All / Play Next All / Add All to Queue load **full** artist catalog from Room.
- Songs tab stays title-paginated for browse; playback order independent.

## Edge cases

| Case | Behavior |
|------|----------|
| Null album year | After dated albums; among nulls, album name A–Z |
| Null albumId / empty album | Year null; album name `""` or track.album; title within bucket |
| Empty catalog | Play All no-op |
| Large catalog | Full load on IO; Play All disabled while preparing |
| Process death | Preference in SecureStorage |
| Overwrite modal | SHUFFLE → tryShuffleContext; ordered modes → tryStartContext |
| Sheet / toast count | Uses `catalogTrackCount` (full artist-view count) |
| Download All / Add to Playlist | Full artist-view catalog (ADR-0142) |
| Artist-view membership | Same as Albums tab: track `artist_id` **or** track on merged albums (album id / name / track attribution) |
