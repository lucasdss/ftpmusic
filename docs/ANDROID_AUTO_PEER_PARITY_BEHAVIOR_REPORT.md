# Android Auto Peer Parity — Behavior Report

Caveman. ADR-0131 + ADR-0132 hardening. Scope 1A+2B (phone projection).

## Ship

| Surface | Behavior |
|---------|----------|
| Root tree | 9 folders driver-first |
| Recently played | Room `last_played_at` paged |
| Downloads | `is_downloaded=1` paged (`auto_offline`) |
| Favorites | Starred tracks paged |
| Daily Mixes | Recipes → today/yesterday tracks |
| Playlists / Albums / Artists | Album expand; artist Play-all iff albumCount>0 |
| Genres | Room genres → paged tracks |
| Live Radio | Network stations; bookmarks when offline/local-only |
| In-Auto search | `onSearch` / `onGetSearchResult` local-first; LRU+TTL cache |
| Voice play-from-search | Album expand; single-track fallback; numeric ids |
| Dual adopt | Auto/voice timeline → Dual CONTEXT (BT/resume) |
| Reactions | Like/dislike gated off radio (session + notif) |

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
| Offline / local-only radio | `radio_favorites` only |
| Empty mix / genre / playlist / artist | Browsable, not Play-all |
| Dangling playlist entries | Skip; fill page until EOF |
| Radio current item | Reactions skipped (session + shade) |
| Voice expand null album | Single stream MediaItem |
| Empty search query | Empty results |
| Process death | Resumption from Dual-adopted queue |

## Out

AAOS. Car App Library. Wear. Dual-band Auto labels. Discovery nodes.

## Correctness follow-up

ADR-0136 + `docs/ANDROID_AUTO_CORRECTNESS_REVIEW_BEHAVIOR_REPORT.md` —
radio id unity, playable playlist/mix paging, LocalOnly search gate,
radio resumption; market gaps deferred (doc appendix only).

## Coverage

`AutoBrowseCatalogTest`, `MediaSessionCallback` browse/search/reaction,
`AutoRadioStationsSourceTest`, Dual adopt unit tests.

