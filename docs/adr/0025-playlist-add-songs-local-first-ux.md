# ADR 0025: Playlist Add-Songs Local-First UX

- Status: Accepted
- Date: 2026-08-25
- Related: `docs/PLAYLIST_MANAGEMENT_BEHAVIOR_REPORT.md`

## Context
Playlist creation ended with a silent dismiss, and a playlist's detail screen
offered no way to add tracks into it (only "copy tracks to another playlist").
The user asked for YouTube Music-style management: after creating a playlist,
offer to add tracks; inside a playlist, add and remove tracks easily — while
respecting the app's existing design system and its local-first
`pending_playlist_changes` pipeline (CONTEXT.md).

## Decision
1. **Reuse the pending-change pipeline; add no sync machinery.** Adds write
   `add_tracks` (comma-joined ids) changes, removes keep `remove_tracks`
   (position) — identical payloads to the existing `PlaylistSyncWorker`
   handlers. `removeFromPlaylist` and `addToPlaylist` were not altered.
2. **Two-step create sheet instead of navigation.** After
   `playlistCreated`, the existing `ModalBottomSheet` switches NAME → CREATED
   ("Add Songs" / "Done") → PICKER. Rationale: zero navigation changes, no
   back-stack/argument plumbing, and the created playlist stays visible in the
   list. The temp-id → server-id remap stays invisible to the user (worker
   `remapPlaylistId`).
3. **One shared picker component, two hosts.** `AddSongsSheet.kt` provides a
   ModalBottomSheet wrapper (detail screen) and an embeddable
   `AddSongsPickerContent` (Library create sheet — avoids nested sheets).
   Data access stays in the ViewModels (search/suggestions passthroughs);
   the composable is purely presentational with debounced search and
   `rememberSaveable` selection.
4. **Picker data sources are local-first.** Suggestions = recently played
   (Room, LIMIT 50); search = `trackDao.searchAllTracks` (title/artist LIKE,
   LIMIT 50). No server round-trip; works offline with whatever is cached.
   Already-added rows are disabled ("Added") — dedup enforced again in DAO
   and ViewModel.
5. **Removal is long-press menu only — in two places.** Track rows in the
   playlist detail use the long-press → "Remove from Playlist" menu; playlist
   rows in Library → Playlists use long-press → "Remove Locally" (local-only
   delete, server copy kept — same semantics as the detail screen's
   "Remove Locally"). Swipe-to-remove (`SwipeToDismissBox`,
   red Delete background) was added, then removed after device review: the red
   background clashed with the design system. `removeFromPlaylist` +
   position-based `remove_tracks` semantics are unchanged.
6. **Auto-download parity.** Both new add paths enqueue downloads when
   `auto_download_playlists` is on, matching `addToPlaylist`/`syncFromServer`.

## Consequences
- Adding tracks is instant (local state updated before server flush).
- The create-sheet Add Songs flow resolves the playlist's current id across
  the temp-id → server-id remap, so added tracks always land in the real
  playlist (fix for the "added songs disappear" report).
- Picker selection is transient (lost on process death) — acceptable: only
  explicit "Add" persists.
- No schema change; no new dependencies; no navigation graph change.
