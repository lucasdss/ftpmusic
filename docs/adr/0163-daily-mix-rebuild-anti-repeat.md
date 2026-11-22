# ADR-0163: Daily Mix rebuild anti-repeat (full prior lists + 7d listens)

## Status

Accepted

## Context

Users reported the same songs after Daily Mix rebuild. Anti-repeat only soft-
excluded scrobbled tracks on the active/last-superseded lists, cliffed off when
fresh &lt; 30, and used calendar-yesterday for weight penalty under sticky
versioning.

## Decision

1. Prefer-exclude = full tracklists of active ∪ last 2 superseded ∪ honest
   listens in the last 7 days.
2. Remove the &lt;30 fresh cliff; hard-fill re-admits only when needed.
3. Penalty set = active ∪ last superseded (sticky-safe).
4. Keep 2 superseded rows on activate for history depth.

## Consequences

- Lower cross-rebuild overlap when the eligible pool is large.
- Tiny pools still produce mixes via hard-fill.
- Slightly more Room rows retained per recipe (2 superseded).
