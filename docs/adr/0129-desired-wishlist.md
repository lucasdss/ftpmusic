# ADR 0129 — Desired wishlist (local wantlist)

Date: 2026-10-12
Status: Accepted
Related: ADR 0020, ADR 0048, docs/DESIRED_WISHLIST_BEHAVIOR_REPORT.md

## Context

Discover surfaces artists/tracks not in the Navidrome library. Users need a
shopping / request list orthogonal to Like (server star) and Dislike. Market
apps (Discogs Wantlist, Bandcamp wishlist) use a dedicated manage surface with
search-to-add and export/share — not star semantics on library rows.

## Decision

1. New Room table `desired_items` (migration 63→64), not `is_desired` columns
   on tracks/albums/artists — rows often have no library id.
2. `DesiredRepository` owns add/remove/upsert/reconcile/share snapshot.
   Favorites tab gains third chip **Desired**; UI peer of Liked/Disliked,
   separate data path from `FavoriteRepository`.
3. Identity: MBID canonical, then Deezer id, then normalized title+artist.
4. Fulfillment v1: reconcile sets `matched_local_id`; keep row with badge;
   user clears manually.
5. Share via `ACTION_SEND` plain text from Desired mode (diagnostics pattern).
6. In-page filter on Desired queries Room; Add-from-catalog is a separate
   subsection when query ≥2 and online.

## Consequences

- Local-only list; share text is the backup/export path.
- Favorites `when (mode)` branches grow; Desired must not reuse star/dislike DAO.
- Metadata sync must call reconcile (best-effort).
