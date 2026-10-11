# ADR 0116 — Track number column surface rules

Date: 2026-10-11  
Status: Accepted  
Related: ADR-0104 (list chrome), `docs/TRACK_NUMBER_CHROME_BEHAVIOR_REPORT.md`

## Context

Mix detail showed album `trackNumber` while rows followed mix position, so numbers looked out of order. Playlist/artist used list index; album used index instead of metadata tags. Market apps (Apple Music, YouTube Music mobile; Spotify album pages) show metadata `#` on albums and avoid album tags on shuffled/mixed lists.

## Decision

1. **Album:** Always sort by `discNumber` then `trackNumber` (nulls last). Leading column shows metadata track tag, gated by `showAlbumTrackNumbers` (SecureStorage `list_show_album_track_numbers`, default true).
2. **Playlist / Daily Mix / Artist:** Leading column shows sequence `1..n`, gated by `showListSequence` (`list_show_sequence`, default true). Never show album track tags.
3. **Artist songs:** Keep title ASC order from DAO; sequence matches that order.
4. **Playing row:** EQ replaces the number in the same leading slot; when the column toggle is off, EQ still appears before the title via nullable `leadingContent`.
5. **Sync:** Persist `discNumber` + `trackNumber` from Subsonic on album/track upsert paths; album→tracks populate uses real `disc_number`.

## Consequences

- Two Appearance toggles extend `ListChromePrefs` without changing reaction/duration prefs.
- Album play indices match disc/track sorted UI order.
- Mix/Playlist no longer confuse users with album metadata numbers.
