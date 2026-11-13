# Android Auto Home Hub — Behavior Report

Date: 2026-11-12
ADR: `docs/adr/0160-android-auto-home-hub.md`

## Root order

Home → Queue → Recently played → Downloads → Favorites → Daily Mixes →
Playlists → Albums → Artists → Genres → Live Radio

## Home sections (omit empty)

| Section | Media id | Source |
|---------|----------|--------|
| Continue listening | `auto_home_recent` | `TrackDao.getRecentlyPlayedPaged` |
| Surprise Me | `auto_home_surprise` | Random Room tracks (playable-only, not browsable); LocalOnly → cached only |
| Daily Mixes | `auto_home_mixes` | `CustomMixDao` |
| Favorite playlists | `auto_home_fav_playlists` | Playlists with `lastSyncedAt != null` |
| Favorite artists | `auto_home_fav_artists` | `CachedMetadataDao.getStarredArtists` |
| Favorite radio | `auto_home_fav_radio` | `RadioFavoriteDao` |

## Tester checklist

- [ ] Home appears first on Auto browse
- [ ] Empty library → Home may be empty folder with no children
- [ ] Surprise Me starts random queue
- [ ] Favorite playlist / artist / radio open and play
