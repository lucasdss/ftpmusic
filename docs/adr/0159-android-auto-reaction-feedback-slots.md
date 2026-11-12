# ADR 0159 — Android Auto Reaction Feedback Slots

Date: 2026-11-12
Status: Accepted
Related: ADR-0118 (session reaction actions)
Supersedes ADR-0118 overflow-only for Auto companion controllers.

## Context

Like/dislike custom commands worked, but Auto HUs often hide
`SLOT_OVERFLOW` buttons, so filled/unfilled thumbs never appear — users
report “no feedback.”

## Decision

1. Keep overflow-only slots for notification / non-Auto controllers.
2. For Auto companion / Automotive controllers, advertise like in
   `SLOT_FORWARD` (+ overflow fallback) and dislike in `SLOT_BACK`
   (+ overflow), and refresh per-controller via
   `setMediaButtonPreferences(controller, …)` /
   `setCustomLayout(controller, …)`.
3. Continue syncing `ThumbRating` on the current `MediaItem`.
4. Classic BT AVRCP thumbs remain a non-goal.

## Consequences

- Auto Now Playing can show toggle state where the HU honors custom slots.
- OEM variance still possible; tester checklist required.
