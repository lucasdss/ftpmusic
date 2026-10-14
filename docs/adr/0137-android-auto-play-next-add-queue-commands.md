# ADR 0137 — Android Auto Play Next / Add to Queue Commands

Date: 2026-10-14
Status: Accepted
Related: ADR-0118 (reactions), ADR-0134 (align), ADR-0135 (placement), ADR-0131
Behavior: `docs/ANDROID_AUTO_PLAY_NEXT_ADD_QUEUE_BEHAVIOR_REPORT.md`

## Context

Phone Dual exposes Play Next / Add to Queue. Auto HU could only Play
(album-expand / adopt). Peer apps expose queue actions on browse tracks.
Market gap listed in ANDROID_AUTO_CORRECTNESS_REVIEW appendix.

## Decision

1. **Custom session commands** — `PLAY_NEXT` / `ADD_TO_QUEUE` via
   `MediaSessionQueueCommands` (parallel to ADR-0118 reactions).
2. **Per-item buttons** — `MediaLibrarySession.setCommandButtonsForMediaItems`
   + `MediaMetadata.supportedCommands` on playable music browse/search tracks.
   Now-playing bar keeps Like/Dislike only.
3. **Mutations via PlaybackManager** — never Dual directly (Cast, align,
   placement toggle, persistence).
4. **Target mediaId** — from `MediaConstants.EXTRA_KEY_MEDIA_ID` in command
   args (browse item), not only `player.currentMediaItem`.
5. **Gates** — reject radio ids, browse folders, queue-node tracks, missing
   Room row, non-playable (null stream URI). LocalOnly = Room-only.
6. **Empty player** — after enqueue, if timeline was empty → `prepare()` +
   `play()` so HU gets audible feedback.
7. **OEM lottery** — if controller reports 0 media-item buttons, commands
   still registered; browse Queue node (ADR-0138) remains the feedback path.

## Consequences

- Auto Favorites/search/album tracks can Play Next / Add with phone Dual semantics.
- Settings `session_end` Add lands in LATE (Queued later).
- Radio items stay without queue buttons.
