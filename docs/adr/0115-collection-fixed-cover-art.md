# ADR 0115 — Collection Fixed Cover Art

Date: 2026-10-10  
Status: Accepted  
Related: ADR 0034 (custom daily mixes), ADR 0036 (scoped cover cache), ADR 0015 (playlist local-first)

## Context

Daily Mix cards took the first non-null track `cover_art_url`. Mixes whose tracks lacked art showed a MusicNote placeholder. Mix detail required ≥4 unique covers for a montage, else name text. Playlists relied on server `coverArt` or Home montage; local creates often had null art and no user override.

Users need: (1) always-filled collection art, (2) optional fixed cover per Custom Daily Mix and playlist from library album or device gallery.

## Decision

1. **Schema (Room v62).** Nullable `fixed_cover_kind` / `fixed_cover_value` on `custom_mixes` and `playlists`. Kind = `navidrome` | `local`.
2. **Local-only persistence.** Fixed covers never upload via Subsonic (`updatePlaylist` has no cover). Navidrome native `POST /api/playlist/{id}/image` exists but needs non-Subsonic auth — deferred.
3. **Shared resolver.** `CollectionCoverResolver` + `CollectionCoverArt` / `CollectionLettermark`. Priority: fixed → derived (track/album/server) → preferred/fallback → lettermark.
4. **Gallery store.** `CollectionCoverStore` copies into `filesDir/collection_covers/` with atomic write; delete on clear/entity delete. Cap raw import **8 MiB**; downsample longest edge **2048**. New-mix staged files use `mix_new_*`; `rekey` on save → `mix_{id}.*`.
5. **Sync hygiene.** Playlist import/refresh/create-remap update server `coverArt` only; preserve fixed columns.
6. **DAO hardening.** `getDailyMixCovers` / montage queries `COALESCE` track + album covers so derived tier fills more often.
7. **Cleanup parity.** Library remove-locally and detail remove-locally both call `PlaylistRepository.onPlaylistDeleted` before DB delete.

## Alternatives

- **Server-only covers.** Rejected: Subsonic cannot set playlist covers; Daily Mix recipes are client-only.
- **Always generate bitmap collage.** Rejected: Home scroll cost (ADR 0036 / HOME_SCROLL_PERF); detail keeps lightweight Compose montage.
- **iTunes-only last resort (no lettermark).** Rejected: offline / no-network must still show a proper tile.

## Consequences

- Migration 61→62 is additive nullable columns.
- Editors (Custom Daily Mixes, playlist detail) gain Library / Device / Clear.
- Tests cover resolver branches + repository set/clear; Room cover join tested in DaosRoomTest.
