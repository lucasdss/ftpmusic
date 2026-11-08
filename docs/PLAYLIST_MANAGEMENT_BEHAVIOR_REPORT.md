# Playlist management — market + behavior report

Date: 2026-11-08  
Status: Phases 0–4 implemented (2.1.5) — local deep review + DeepSeek Phase-5  
Related: ADR-0015, ADR-0025, ADR-0150, ADR-0114 (FAB), PLAY_RELEASE_2_1_5  
Plan: Playlist UX Market Research (cross-checked)

## Verdict

FtpMusic is competitive for a **local-first Navidrome client** (offline edit,
import-from-server, cover packs, Daily Mixes) but lags Spotify / Apple Music /
YouTube Music on **universal Save-to-playlist**, detail **Add/Sort/Edit** power,
and honest download/sync chrome. Symfonium remains ahead on sync-mode labels and
smart playlists — we differentiate via Daily Mixes, not a rule builder.

## Market matrix (summary)

| Capability | Spotify / Apple / YTM | Symfonium | FtpMusic |
|---|---|---|---|
| Create / import | Create hub | Import + sync modes | FAB create + Import from Server |
| Save song → playlist | Everywhere | Yes | Uneven (Album rich; NP/Search/Home weak) |
| Detail Add / Sort / Edit | Top cluster | Edit + sync | Play/Shuffle + Add Songs; edit in ⋮ |
| Drag reorder | Edit mode | Yes | Move Up/Down only |
| Multi-select | Edit / long-press | Yes | No |
| Offline edit | Cloud-limited | Offline-first modes | Local-first pending flush (**strength**) |
| Smart playlists | AI / editorial | Strong | Daily Mixes only |
| Collab | Yes | N/A | Out of scope |

## Surfaces today

- Library list: `LibraryScreen` Playlists tab (FAB outside riding clip since 2.1.4)
- Detail: `PlaylistDetailScreen` + `PlaylistDetailViewModel`
- Add songs: `AddSongsSheet`
- Album picker: create + import; Artist: existing lists only
- Queue: save as playlist / share public

## Corrected findings (DeepSeek + code proof)

| ID | Finding | Severity |
|----|---------|----------|
| F1 | Action-sheet **Add all to Playlist** seeds empty → VM no-op | **P0** |
| F2 | Per-track Add clears `menuTrackIndex` before picker seed | **P0 fragile** |
| F3 | `copyPlaylist` server-first vs create local-first | **P0 path** |
| F4 | `syncToServer` clears `isConflicted` before flush confirmed | **P0 trust** |
| F5 | “Downloaded” chip = auto-download setting | **P1 honesty** |
| F6 | Home Add to Playlist navigates album only | **P1 reach** |
| F7 | NP / Search lack Save; Artist thinner than Album | **P1 reach** |
| F8 | No sort / in-playlist search; list ⋮ = Remove Locally | **P1/P2** |

## API / architecture contract

1. Reads = Room only (ADR-0015).
2. Mutations = Room → `PendingPlaylistChange` → worker. Never `createPlaylist` as replace-editor.
3. Reorder / persist-sort / syncToServer = `sync_tracks` → `updatePlaylist` remove-all+add; chunk large N.
4. Drag (future) = edit-mode; one commit on drop; mutex vs `refreshFromServer`.
5. Save picker targets = user playlists only — never Daily Mix ids.
6. Cover long-press (ADR-0150) preserved.
7. Library FAB stays outside `appHeaderRidingContent` (2.1.4).
8. Duplicate add: pending `add_tracks` payload = **actually-added** ids only.
9. No Room schema without migration test; no `fallbackToDestructiveMigration`.

## Phases

| Phase | Outcome |
|-------|---------|
| **0** | This report |
| **1** | F1–F4 trust fixes + selection helper + tests |
| **2** | Shared Save sheet → Now Playing / Search / Home |
| **3a** | Detail Add/Sort/Edit; view-only sort default; list ⋮ |
| **3b** | Edit-mode drag → one `sync_tracks` |
| **4** | Multi-select; Synced/Pending/Conflict; in-playlist search |
| **5** | Local deep review + DeepSeek audit → ship **2.1.5** |

### Phase-5 gate (DeepSeek)

- First audit: **PASS WITH FIXES** (Library badge N+1 broke unit tests; reorder cancel could flush stale).
- Fixes: per-row badge try/catch + test stubs; `orderVersion` short-circuit in `persistTrackOrder`; repo `add_tracks` payload = actually-added; DragHandle; honest download denominator; Done → `commitTrackOrder()`.
- Re-audit: **PASS WITH FIXES** → **P0 NONE** (residual P1: refresh-vs-reorder test gap; comma id delimiter).

## Regression freeze

`PlaylistDetailViewModelTest`, `PlaylistRepository*`, `PlaylistSyncWorker*`,
`PlaylistConflictTest`, `LibraryViewModelTest` playlist paths, Album/Artist
picker tests, `PlaylistDetailChromeTest`, DAO `addTracksToPlaylist*`,
`AppHeaderRidingClipHostComposeTest`, manual: covers + FAB + create→Add Songs.

## Test IDs (Phase 1+)

T1–T8 unit (selection, empty, duplicate, offline copy/create, sync conflict,
moveTrack) · T9–T13 Room/VM badges & sort · T14–T18 Compose picker/Home/FAB ·
edge matrix: offline, empty, temp id, duplicate, mid-flush remap, refresh vs
reorder, remove-by-id under filter.

## Out of scope

Collaboration / Jam / Blend · Symfonium smart-rule builder · server cover upload.
