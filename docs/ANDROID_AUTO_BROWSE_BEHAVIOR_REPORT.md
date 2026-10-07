# Android Auto Browse — Behavior Report

Caveman. ADR-0091. Phase A critical path.

## Ship

| Surface | Behavior |
|---------|----------|
| Library root | FTP Music → 5 folders |
| Recently played | Room `last_played_at` paged |
| Favorites | Starred tracks paged |
| Playlists | Local playlists → entries → tracks |
| Albums / Artists | `getAlbumsPaged` / `getArtistsPaged` |
| Play album/playlist/artist | `onAddMediaItems` expand |
| Play track | Existing album expand |

## Edge

| Case | Result |
|------|--------|
| Empty Room | Empty children lists |
| No PlaylistDao | `ERROR_NOT_SUPPORTED` (tests without DI) |
| Offline | Browse still works; stream URI + CacheDataSource (no file://) |
| Empty playlist | Folder browsable, not Play-all |
| Process death | Resumption unchanged (ADR-0087) |

## Out

Internet Radio. Wear. Auto search UI. Dual-band labels on Auto.

## Coverage

`AutoBrowseCatalogTest`, `MediaSessionCallback` browse tests.
