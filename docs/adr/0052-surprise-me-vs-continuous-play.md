# ADR 0052 — Surprise Me vs Continuous Play

Date: 2026-09-30
Status: Accepted
Related: ADR-0039 (dual queue), ADR-0007 (queue architecture),
docs/SURPRISE_ME_BEHAVIOR_REPORT.md, docs/CONTINUOUS_PLAY_BEHAVIOR_REPORT.md

## Context

Surprise Me grew a NavHost `queueSize in 1..9` → `maybeRefillRandomQueue()` path that called `addAllToQueue` (Priority) with **no** `surprise-me` source gate. That conflated one-shot random play with Continuous Play and polluted user Priority for any short queue.

Continuous Play already existed: Settings flag + queue journal + MediaService last-item → `appendToContext`.

## Decision

1. **Surprise Me** = one-shot `tryStartContext` (50 random / cached). No auto-refill. Still journals as `random`/`surprise-me`.
2. **Continuous Play** = sole auto-continue feature. Journal weighted select → **context** append only. Never Priority. Never Surprise Me refill.
3. Remove NavHost refill hook and `LibraryViewModel.maybeRefillRandomQueue`.
4. Gate logic extracted to `ContinuousPlayGate` for unit tests.

## Consequences

- Short album/playlist queues no longer get random Priority dumps.
- After Surprise Me ends, Continuous Play (if ON) may continue from journal — including Surprise Me IDs — via context append.
- Settings copy must describe journal as recent **sources** and Continuous Play as context auto-append, not Surprise Me.
