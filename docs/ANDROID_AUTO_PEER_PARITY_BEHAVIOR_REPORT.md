# Android Auto Peer Parity — Behavior Report

Caveman. ADR-0131. Scope 1A+2B (phone projection). Supersedes ADR-0091 Out for search/radio.

## Ship

| Surface | Behavior |
|---------|----------|
| Root tree | 9 folders driver-first |
| Recently played | Room `last_played_at` paged |
| Downloads | `is_downloaded=1` paged (`auto_offline`) |
| Favorites | Starred tracks paged |
| Daily Mixes | Recipes → today/yesterday tracks |
| Playlists / Albums / Artists | Unchanged ADR-0091 |
| Genres | Room genres → paged tracks |
| Live Radio | Network stations; bookmarks offline |
| In-Auto search | `onSearch` / `onGetSearchResult` local-first |
| Voice play-from-search | Unchanged album-expand path |
| Reactions | Like/dislike gated off radio |

## Root order

1. Recently played
2. Downloads
3. Favorites
4. Daily Mixes
5. Playlists
6. Albums
7. Artists
8. Genres
9. Live Radio

## Edge

| Case | Result |
|------|--------|
| Empty Room | Empty children |
| Offline radio | `radio_favorites` only |
| Empty mix / genre / playlist | Browsable, not Play-all |
| Radio current item | Reactions skipped |
| Empty search query | Empty results |
| Process death | Resumption unchanged |

## Out

AAOS. Car App Library. Wear. Dual-band Auto labels.

## Coverage

`AutoBrowseCatalogTest`, `MediaSessionCallback` browse/search/reaction tests.
