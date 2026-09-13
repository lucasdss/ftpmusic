# ADR 0015: Playlist Local-First Architecture

## Status
Accepted

## Context
The app initially had a mixed architecture for playlists: some read paths (`LibraryViewModel.loadPlaylists()`) used the local Room DB (`PlaylistDao.getAll()`), while others (`AlbumDetailViewModel.loadPlaylists()`) fetched directly from the Subsonic server API (`api.getPlaylists()`). This inconsistency meant the "Add to Playlist" picker in Album Detail showed server playlists instead of local playlists, and the recently-created playlists (from the Library screen's local-first flow) wouldn't appear there at all.

An audit of all 18 playlist read paths revealed 2 server-first violations:
1. `AlbumDetailViewModel.loadPlaylists()` — called `api.getPlaylists()` directly
2. `AlbumDetailViewModel.createPlaylist()` — called `api.createPlaylist()` with no local persistence

## Decision

### 1. Room DB as Source of Truth for Playlist Reads
All playlist read paths MUST read from the local Room DB (`PlaylistDao.getAll()`, `PlaylistDao.getById()`). Server data is a secondary refresh source, never the primary read path.

### 2. Local-First Mutations with Async Server Sync
All playlist mutations (create, rename, delete, add tracks, remove tracks, reorder) MUST follow the pattern:
1. Write to Room DB immediately (optimistic UI)
2. Enqueue a `PendingPlaylistChange` with the change type and payload
3. Trigger `PlaylistSyncWorker.flushNow()` for async server sync
4. `PlaylistSyncWorker` flushes pending changes to server, remaps temp IDs to server IDs

### 3. Server-Only Operations (Explicitly Allowed)
These operations are exempted because they inherently require server data:
- `loadServerPlaylists()` — fetch unimported playlists from server (import picker)
- `importPlaylist()` — fetch full playlist data from server for local persistence
- `shareQueue()` — create playlist on server with inline tracks, share externally

### 4. Background Refresh
`PlaylistDetailViewModel.loadPlaylist()` uses a hybrid approach: reads from local DB first (optimistic UI), then fires `refreshFromServer()` in the background to silently update metadata and entries from the server.

## Consequences
- "Add to Playlist" pickers (Album Detail, Track Action Sheet) show the same local playlists as the Library tab
- Playlist creation is instantaneous (no network wait) — temp ID is replaced with server ID asynchronously
- Offline playlist browsing works with cached data
- `PlaylistRepository` is the single facade for all playlist operations, injected into all ViewModels
- `PendingPlaylistChange` queue provides eventual consistency with the server
- 16 of 18 playlist read paths verified correct; 2 server-first violations fixed

## Audit Results (18 paths traced)

| # | Component | Method | Data Source | Status |
|---|-----------|--------|-------------|--------|
| 1 | AlbumDetailVM | `loadPlaylists()` | `playlistDao.getAll()` | ✅ Fixed |
| 2 | AlbumDetailVM | `loadServerPlaylists()` | `api.getPlaylists()` (filtered) | ✅ Correct |
| 3 | AlbumDetailVM | `createPlaylist()` | `playlistRepo.createPlaylist()` | ✅ Fixed |
| 4 | AlbumDetailVM | `importPlaylist()` | `api.getPlaylist()` | ✅ Correct |
| 5 | AlbumDetailVM | `addToPlaylist()` | `playlistDao.addTracksToPlaylist()` | ✅ Correct |
| 6 | LibraryVM | `loadPlaylists()` | `playlistDao.getAll()` | ✅ Correct |
| 7 | LibraryVM | `loadServerPlaylists()` | `api.getPlaylists()` (filtered) | ✅ Correct |
| 8 | LibraryVM | `importPlaylist()` | `api.getPlaylist()` | ✅ Correct |
| 9 | LibraryVM | `createPlaylist()` | `playlistRepo.createPlaylist()` | ✅ Correct |
| 10 | PlaylistDetailVM | `loadPlaylist()` | `playlistDao.getById()` + background refresh | ✅ Correct |
| 11 | PlaylistDetailVM | `refreshFromServer()` | `api.getPlaylist()` (background) | ✅ Correct |
| 12 | PlaylistDetailVM | `syncFromServer()` | `api.getPlaylist()` (user-initiated) | ✅ Correct |
| 13 | PlaylistDetailVM | `loadAvailablePlaylists()` | `playlistDao.getAll()` | ✅ Correct |
| 14 | PlaylistRepository | `createPlaylist()` | `playlistDao` + pending change | ✅ Correct |
| 15 | PlaylistRepository | `importPlaylist()` | `api.getPlaylist()` | ✅ Correct |
| 16 | PlaylistRepository | `loadServerPlaylists()` | `api.getPlaylists()` (filtered) | ✅ Correct |
| 17 | PlaylistRepository | `addToPlaylist()` | `playlistDao.addTracksToPlaylist()` | ✅ Correct |
| 18 | PlaybackVM | `shareQueue()` | `api.createPlaylist()` | ✅ Correct (by design) |
