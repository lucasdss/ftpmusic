# QS / Lock Thumbs — Behavior Report

Date: 2026-10-11
ADR: [0118-media-session-reaction-actions.md](adr/0118-media-session-reaction-actions.md)

## Surfaces

| Surface | Behavior |
|---|---|
| Notification shade (expanded) | Play/Pause, Next, Previous, Like, Dislike |
| Notification compact | Play/Pause, Next, Previous only (indices 0,1,2) |
| Lock / system media QS | Session custom layout + overflow thumbs; OEM-dependent |
| Custom `PlaybackTileService` | Unchanged — play/pause only |
| Android Auto | Custom commands + `ThumbRating` when controller supports |
| Classic BT AVRCP | Transport only — thumbs not promised |

## Semantics (ADR-0020)

- Like = Navidrome star (server mirror best-effort).
- Dislike = local Room only; clears star.
- Mutual exclusion enforced in `CurrentTrackReactionController`.
- Null / missing `currentMediaItem` → command ignored (`RESULT_ERROR_INVALID_STATE`).

## Command IDs

- `com.lucasdss.ftpmusic.app.action.LIKE`
- `com.lucasdss.ftpmusic.app.action.DISLIKE`

## State sync

1. Toggle (NP / shade / session / Auto rating) → controller snapshot.
2. MediaService collects → `updateExtraState` + `setMediaButtonPreferences` +
   `ThumbRating` on current item + notification rebuild.
3. PlaybackViewModel collects → NP thumbs stay live when toggle came from system.

## Edge cases

- Track advance mid-toggle → generation / track-id guards (VM + controller mutex).
- Notifications OFF → minimal FGS notif; session still advertises custom commands.
- Cast seat swap → reactions key off `currentMediaItem.mediaId`, not ViewModel.
- Process death → Room truth on next track load / resumption.
