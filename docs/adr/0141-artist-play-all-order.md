# 0141 — Artist Play All Order

Status: Accepted (modes revised by ADR-0145)
Date: 2026-10-14
Related: ADR-0069 (artist albums-first UI), ADR-0039 (dual-queue), ADR-0145

## Context

Artist Play All queued title A–Z for the first loaded page (50) only. Market
clients disagree on artist order (Symfonium defaults random; Apple often
release order / Shuffle Albums). Users want Shuffle-as-default Play All options
and no redundant Shuffle chrome on artist.

## Decision (as revised by ADR-0145)

1. SecureStorage key `artist_play_order`:
   - `shuffle_albums` (**default**) — random album blocks; tracks 1…N inside
   - `shuffle` — shuffle all songs
   - `album_year_asc` / `album_year_desc` — chronological
   - `album_name_asc` / `album_name_desc` — alphabetical albums
2. Remove artist `DetailActionRow` Shuffle; album detail keeps Shuffle.
3. Play All loads full artist-view catalog (ADR-0145 membership), joins
   `cached_albums.year`, orders via `ArtistPlayOrderer`, then
   `tryStartContext` (album modes) or `tryShuffleContext` (song shuffle only).
4. Within album (non–song-shuffle): disc → track → title → id.
5. Play Next All / Add All to Queue use the same ordered full catalog.
6. Scope: artist detail only.
7. Settings: strategy radios + direction chips for year/name (ADR-0145 UX).

## Consequences

- UI Songs tab pagination unchanged (browse chrome ≠ play order).
- Large artists may hitch on first Play All; prep flag disables the button.
- Player-level shuffle remains independent of this setting.
- Song title A–Z removed from UI; legacy `title_asc` maps to `album_name_asc`.
