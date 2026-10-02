# Artist Like Behavior Report

**Feature:** Library Artists thumbs (like/dislike)
**Date:** 2026-10-02
**Status:** Class A bug fixed — `loadArtists` IGNORE-inserts into `cached_artists`

## Flow

```
Library Artists row thumb
  → LibraryViewModel.toggleArtistLike / toggleArtistDislike
  → FavoriteRepository.likeArtist / dislikeArtist
  → ensureArtistLedgerRow (INSERT…SELECT FROM cached_artists)
  → setArtistStarredAt / setArtistDisliked
  → best-effort Subsonic star/unstar
```

Like/dislike **require** `cached_artists` row. Tracks/albums use different tables — can succeed while artist thumb fails.

## Failure classes

| Class | Symptom | Cause | Status |
| :--- | :--- | :--- | :--- |
| **A** | List thumbs fail for **some** artists | UI list from live `getArtists`; cache missing those ids → ensure INSERT 0 → UPDATE 0 → Flow reverts thumb | **Fixed** |
| **B** | No artist thumb on Artist detail | UI never wired | By design / out of scope |

## Scenarios (Class A)

1. Fresh install / pre-`syncArtists` — API list painted, cache empty
2. `syncArtists` failed (bad status, empty index, empty creds) — UI still has API overlay
3. Mid-`replaceArtists` clear window — transient miss
4. Partial cache vs full API index — subset of ids missing
5. Optimistic thumb on → `loadFavorites` overwrites from empty ledger → snap off
6. Ghost server star — `mirrorStar` may still call API after local 0-row write
7. `healEmptyLedger` — only ledger⊂cache count; **not** cache⊂API

## Repro (pre-fix)

1. Empty `cached_artists`, online Library → Artists
2. Tap thumbs on any row → thumb flashes / reverts; `artists.starred_at` stays null
3. After successful metadata artist sync → same row thumbs stick

## Fix

`LibraryViewModel.loadArtists` / `loadArtistsInternal` call `insertArtistsIgnore` after API parse so every displayed id exists in `cached_artists` without wiping MBID/rating/similar enrichment (`OnConflictStrategy.IGNORE`).

## Surfaces

| Surface | Artist like? |
| :--- | :--- |
| Library → Artists | Yes |
| Artist detail | No (album/track only) |
| Home / Favorites | Display / unlike only |
| Search / Genre | No |
