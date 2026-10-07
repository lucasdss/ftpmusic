# ADR 0091 — Android Auto Browse MVP

Date: 2026-10-07
Status: Accepted
Related: ADR-0071 (BT autoplay; skipped browse), ADR-0087 (resumption), ADR-0013 (local-first)
Supersedes browse skip in ADR-0071 for library tree only (BT policy unchanged).

## Context

Peers (Symfonium, Ultrasonic, Tempo) ship Android Auto browse. FTP had
`MediaLibrarySession` + play-from-search / resumption but no `onGetLibraryRoot` /
`onGetChildren`. Car users could resume BT playback but not browse the library.

## Decision

1. **Local-first browse** — `AutoBrowseCatalog` reads Room only (no network on click).
2. **Root nodes** — Recently played, Favorites, Playlists, Albums, Artists.
3. **Media id scheme** — `AutoBrowseIds` (`auto_root`, `auto_album:{id}`, …). Bare
   track ids remain Subsonic ids for existing album expand.
4. **Play folders** — album / playlist / artist nodes are playable; `onAddMediaItems`
   expands via `AutoBrowseCatalog.expandForPlayback`.
5. **Inject `PlaylistDao`** into `MediaSessionCallback` via `MediaModule`.
6. **Out of scope** — Internet Radio node, Wear layouts, in-Auto search UI.

## Consequences

- Auto head units see a 5-node library tree when metadata sync has populated Room.
- Empty library → empty children (no crash).
- Radio / deep search deferred to later ADRs.
