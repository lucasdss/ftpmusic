# ADR 0131 — Android Auto Peer Parity

Date: 2026-10-13
Status: Accepted
Related: ADR-0091 (browse MVP), ADR-0095 (stream URI), ADR-0118 (reactions)
Supersedes ADR-0091 “Out” for Internet Radio + in-Auto search UI.
Adds Offline / Daily Mixes / Genres Auto nodes.

## Context

ADR-0091 shipped 5-node Auto browse. Peers (Symfonium, Ultrasonic, Tempo)
also expose search, downloads, radio, genres, and discovery mixes. Phone app
already has Downloads, Daily Mixes, Genres, and `getInternetRadioStations`
in Library — Auto tree lagged.

User lock: phone projection only (1A); maximal peer pack (2B).

## Decision

1. **Root expands to 9 nodes** — Recent, Downloads, Favorites, Daily Mixes,
   Playlists, Albums, Artists, Genres, Live Radio (driver-first order).
2. **Local-first** — Offline / Mixes / Genres / existing nodes stay Room-only.
   Radio fetches network; falls back to `RadioFavoriteDao` when offline/local-only.
3. **In-Auto search** — Implement Media3 `onSearch` + `onGetSearchResult` via
   `LocalSearchRepository` (optional `search3` when local empty + online).
   Voice `searchAndNavidromeExpand` stays album-expand; do not converge.
4. **Media ids** — Extend `AutoBrowseIds` (`auto_offline`, `auto_mixes`,
   `auto_mix:{id}`, `auto_genres`, `auto_genre:{urlencoded}`, `auto_radio`,
   `auto_radio:{id}`). Bare Subsonic track ids unchanged.
5. **Radio MediaItem** — Direct `streamUrl` URI; extras `type=radio`. Bypass
   album expand. Gate like/dislike for radio current items.
6. **Genre paging** — `TrackDao.getTracksByGenrePaged`; no phone Genre Detail
   network aggregation on Auto click.
7. **Mix expand** — Today → yesterday daily_mix row; `filterPlayableMixTrackIds`;
   no `generateAll` on browse click.
8. **Out of scope** — AAOS, Car App Library, Wear.
   Dual-band Auto labels shipped later: ADR-0138. Play Next/Add: ADR-0137.

## Consequences

- Auto HU sees peer-class browse + search when metadata sync populated Room.
- Radio empty offline without bookmarks (no crash).
- Reaction buttons hidden/no-op for live radio streams.
