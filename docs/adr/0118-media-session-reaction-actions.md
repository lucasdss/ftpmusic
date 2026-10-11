# ADR 0118 — Media Session Reaction Actions (Like / Dislike)

Date: 2026-10-11
Status: Accepted
Related: ADR-0020 (favorite semantics), ADR-0087 (resumption), ADR-0091 (Auto browse),
`docs/QS_LOCK_THUMBS_BEHAVIOR_REPORT.md`

## Context

Now Playing exposes thumbs up/down via `PlaybackViewModel` → `FavoriteRepository`.
System surfaces (notification shade, lock screen, system media QS, Android Auto)
only advertised transport (play/pause/next/prev). Classic BT AVRCP units rarely
surface custom actions.

## Decision

1. **Service-safe controller.** `CurrentTrackReactionController` owns like/dislike
   mutations (ADR-0020 mutual exclusion, local-first). Shared by ViewModel and
   `MediaSessionCallback` / `MediaService`.
2. **Media3 custom commands.** Advertise `ACTION_LIKE` / `ACTION_DISLIKE` on
   connect; handle in `onCustomCommand`. Buttons use `ICON_THUMB_*` with
   `SLOT_OVERFLOW` so compact transport stays play/pause/prev/next.
3. **Notification expanded row.** `PlaybackNotificationProvider` appends thumbs
   after transport; compact indices stay `0,1,2`. Custom ActionFactory routes
   to `MediaActionReceiver` / `ReactionCommandBridge` (never stub → play/pause).
4. **ThumbRating metadata.** Sync `ThumbRating` on the current `MediaItem` for
   Auto / rating-aware controllers. `onSetRating(ThumbRating)` maps to the same
   controller. Dislike remains local-only (ADR-0020).
5. **BT car HU thumbs = non-goal.** Document AVRCP lottery; do not promise.

## Consequences

- Shade + many lock/QS media rows show thumbs; OEM compact slots vary.
- Android Auto may show custom buttons / thumb rating where supported.
- Classic Bluetooth transport unchanged; custom thumbs usually absent.
- Reaction state refreshes session button prefs + notification `buildKey`.
