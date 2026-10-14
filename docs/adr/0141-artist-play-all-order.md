# 0141 — Artist Play All Order

Status: Accepted
Date: 2026-10-14
Related: ADR-0069 (artist albums-first UI), ADR-0039 (dual-queue)

## Context

Artist Play All queued title A–Z for the first loaded page (50) only. Market
clients disagree on artist order (Symfonium defaults random; Apple often
release order). Users want Shuffle-as-default Play All, optional album-year
order, and no redundant Shuffle chrome on artist.

## Decision

1. SecureStorage key `artist_play_order`:
   - `shuffle` (default)
   - `album_year_asc` — oldest albums first
   - `album_year_desc` — newest albums first
   - `title_asc` — song title A–Z
2. Remove artist `DetailActionRow` Shuffle; album detail keeps Shuffle.
3. Play All loads all tracks for `artist_id`, joins `cached_albums.year`,
   orders via `ArtistPlayOrderer`, then `tryStartContext` / `tryShuffleContext`.
4. Chronological: year → album name → disc → track → title → id.
   Null years after dated albums; among nulls, album name A–Z.
5. Play Next All / Add All to Queue use the same ordered full catalog.
6. Scope: artist detail only.

## Consequences

- UI Songs tab pagination unchanged (browse chrome ≠ play order).
- Large artists may hitch on first Play All; prep flag disables the button.
- Player-level shuffle remains independent of this setting.
