# Dislikes Behavior Report

Caveman terse. Favorites tab Disliked segment. ADR-0020 + `disliked_at` v55.

## Entry

- Favorites bottom tab → chips **Liked | Disliked** (default Liked).
- No new nav tab. No Settings deep link.

## Data

| Entity | Flag | Timestamp | Server |
|--------|------|-----------|--------|
| Track | `is_disliked` | `disliked_at` | local only |
| Album | `is_disliked` | `disliked_at` | local only |
| Artist | `is_disliked` | `disliked_at` | local only |
| Radio | N/A | N/A | bookmarks only on Liked |

- Like ↔ dislike mutually exclusive (`FavoriteRepository`).
- Disliked list: `ORDER BY disliked_at DESC` paged with `FavoritesPaging.PAGE_SIZE` + OFFSET (mirrors starred). See `docs/FAVORITES_PAGING_BEHAVIOR_REPORT.md`.
- Migration 54→55 adds columns + backfills existing `is_disliked = 1`.
- Ledger sync preserves `disliked_at` (never wiped by metadata refresh).

## UI (Disliked mode)

- Sections: Tracks, Artists, Albums (same Settings toggles as Liked for artists/albums).
- No Radio section.
- Trailing red ThumbDown → `clearDislike*` (optimistic + rollback).
- Row tap: play track / open album / artist (same NavHost callbacks).
- Empty: “No dislikes yet” + Daily Mix hint.

## Edge

- Process death → mode resets to Liked (not persisted).
- Offline clearDislike OK (local-only).
- Re-like from elsewhere clears dislike + `disliked_at`.
- Daily Mix still excludes via `is_disliked` (ADR-0037); no cascade.

## Out of scope

- Server sync of dislikes.
- Radio dislike.
- Play-all disliked queue.
