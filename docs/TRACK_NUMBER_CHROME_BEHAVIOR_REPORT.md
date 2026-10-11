# Track number chrome — behavior report

Date: 2026-10-11  
Related: ADR-0104 (list chrome), ADR-0116 (surface rules)

## Rules

| Surface | Toggle | Value when shown | Order |
|---|---|---|---|
| Album | `showAlbumTrackNumbers` (default ON) | Metadata `trackNumber` tag | Disc then track ASC; nulls last |
| Playlist | `showListSequence` (default ON) | Sequence `index + 1` | Playlist position |
| Daily Mix | `showListSequence` | Sequence `index + 1` | Mix `position` |
| Artist songs | `showListSequence` | Sequence `index + 1` | Title ASC (DAO already) |

## Leading slot

- Toggle ON + idle: number in narrow `widthIn(16..28.dp)` cell.
- Playing: EQ replaces number in same slot.
- Toggle OFF: no leading slot (`leadingContent = null`); EQ only when active.

## Sync tags

Library sync persists Subsonic `track` → `track_number` and `discNumber` → `disc_number` on:

- `cached_album_tracks` (album track sync)
- `tracks` via `parseSongMapToTrack`, starred upsert, playlist import
- `populateAllTrackGenres` uses `cat.disc_number` (not hardcoded `1`)
- `upsertTracksPreserveCache` merges `discNumber`

## Settings

Appearance → list chrome:

1. Track numbers on albums  
2. Numbers on playlists and mixes (also artist songs)
