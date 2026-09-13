# ADR 0021 — Home Section Visibility and the Playlists Row

Date: 2026-08-23
Status: Accepted
Related: ADR 0020, ADR 0017 (lazy daily mix
generation), `docs/adr/0015-playlist-local-first-architecture.md`

## Context

The updated design adds four optional Home sections between Daily Mixes and
Tuned In: **Playlists** (synced only), **Favorite Artists**, **Favorite
Albums**, **Favorite Radio**. Each is gated by a Settings toggle
("Home & Favorites" section) AND by the presence of data — a section renders
only when both hold. The Playlists row shows a 4-cover montage per playlist
with a synced check and "See all" navigation to the Library → Playlists tab.

## Decision

1. **Toggle AND data gating.** A section renders only when its toggle is ON
   and at least one favorite/synced item exists. Toggles live in
   `SettingsViewModel` (SecureStorage keys `home_show_playlists`,
   `home_show_fav_artists`, `home_show_fav_albums`, `home_show_fav_radio`,
   default ON) and are re-read by `LibraryViewModel.refreshHomePrefs()` on
   every Home resume so Settings changes apply live.
2. **Section order** (design-fixed): Daily Mixes → Playlists → Favorite
   Artists → Favorite Albums → Favorite Radio → Tuned In → Recently Added.
3. **Playlists row = synced only.** Unsynced drafts stay in Library.
   `PlaylistView.isSynced` (lastSyncedAt != null && no pending changes) is the
   existing signal. "See all" navigates to `library?tab=playlists` — the
   Library route gains an optional `tab` argument; `LibraryContent` seeds its
   `rememberSaveable` tab from it.
4. **Montage source.** `LibraryViewModel.loadPlaylistMontages()` builds the
   4-cover map on IO: `playlist_entries` → track ids → distinct
   `cached_albums.cover_art` (one query per playlist). Fallbacks: single
   playlist coverArt → music icon.
5. **Row interactions.** Playlist/fav-album cards navigate to Library/Album
   detail; radio pills start station playback via `playStream`; fav-artist
   avatars are display-only (design renders them as non-interactive).

## Consequences

- Settings toggles have a single source of truth (SettingsViewModel) with a
  read-mirror in LibraryViewModel; no cross-ViewModel state sharing.
- The Library tab deep-link is additive (bottom-bar navigation still uses the
  default tab), preserving existing `rememberSaveable` behavior.
- Montage queries run on IO and are cached in state; playlist tab loads
  refresh them after every playlist list load.
