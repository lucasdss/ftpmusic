# ADR 0020 — Favorite Semantics: Like / Dislike / Bookmark

Date: 2026-08-23
Status: Accepted
Related: ADR 0009 (pending scrobbles removal),
`docs/adr/0013-local-first-architecture-and-offline-playback.md`

## Context

The design mock represents entity-level favorites with a Heart icon
(albums, artists, radio stations). The implementation already uses
**ThumbsUp/ThumbsDown** for tracks (`is_disliked` v41 migration, like == server
star). The user decided to unify all favorites around the existing thumb
semantics and a new Bookmark ribbon for radio, and to make every favorite
write **local-first** with Room persistence.

The existing `albums`/`artists` tables had `starred_at` columns but were never
populated — the metadata sync only writes `cached_albums`/`cached_artists`,
which are fully wiped (`replaceAlbums`/`replaceArtists`) on every sync. Any
favorite state stored on the wiped tables would be lost.

## Decision

1. **Favorite = unified concept.** Tracks, albums, and artists use
   Like (thumbs up == server star) / Dislike (thumbs down, local-only,
   mutually exclusive with like). Radio stations use Bookmark (local-only —
   Subsonic has no radio star endpoint). All are "Favorited" and surface on
   the Favorites tab and Home.
2. **Local-first Room writes.** Every toggle writes the local DB first (or
   optimistically), then best-effort server mirror for likes. Dislikes and
   bookmarks never touch the server. Offline failures roll the UI state back.
3. **Ledger tables.** `albums` and `artists` become favorite ledgers. The
   metadata sync repopulates them every run:
   - `INSERT OR IGNORE … SELECT … FROM cached_albums/cached_artists` (new rows)
   - correlated `UPDATE … FROM`-style refresh of metadata on existing rows —
     never touching `starred_at`, `user_rating`, `is_disliked`, `created_at`
   - prune of unstarred/unliked rows for entities removed from the server
   (favorites survive temporary gaps).
4. **Migration 42→43** adds `radio_favorites` (station_id PK, name, stream_url,
   home_page_url, bookmarked_at) and `is_disliked` on `albums`/`artists`, plus
   `albums.artist` (display name needed by Home/Favorites rows).
5. **Server star mirror extended.** `MetadataSyncWorker.syncStarredAndRatings`
   now mirrors `getStarred2` sections for albums (star + rating) and artists
   (star) in addition to tracks — bulk set + clear-non-starred, matching the
   track pattern.
6. **Radio bookmarks are local-only by design** — no server sync, survives
   restarts via Room.

## Consequences

- Favorites survive full metadata syncs (the old wipe trap is avoided by the
  ledger indirection).
- Liking an album/artist on one client mirrors to Navidrome and back; dislikes
  and bookmarks stay on-device (consistent with track dislikes).
- Ledger pruning keeps deleted entities out of Home/Favorites unless still
  favorited.
- A new migration is required for installs on DB version < 43; schema
  validation is enforced at compile time by Room.

## Review addendum (2026-08-23) — local-first ordering, mirror protection, push-back

Deep review of the v4 implementation found three gaps, now closed:

1. **Like is local-first, not server-first.** `starTrack/starAlbum/starArtist`
   previously called `api.star` before the local write, so an offline like
   failed entirely. Now every like writes Room **first** (clear dislike →
   `ensure*LedgerRow` → set `starred_at`) and mirrors to the server
   best-effort (log-only on failure — never throws, never rolls back). The
   ViewModel rollback now only triggers on DB-level failure.
2. **Ledger ensure-row.** Search upserts albums/artists into
   `cached_albums`/`cached_artists` without a ledger row, so
   `UPDATE albums SET starred_at …` matched nothing and the favorite was
   silently lost. `ensureAlbumLedgerRow`/`ensureArtistLedgerRow`
   (`INSERT OR IGNORE … SELECT … WHERE id = :id`) run before every ledger
   star/dislike write.
3. **Mirror protection + push-back (tracks, albums, artists).**
   `syncStarredAndRatings` previously wiped any local star the server didn't
   have — destroying offline likes. Now it:
   - captures `syncStartMs` and persists it as `last_star_sync_ms`;
   - pushes local-only stars back to the server with one comma-joined
     `star` call per entity type (Subsonic accepts comma-separated ids);
     push failures are silent and retried next sync;
   - clears non-starred rows only when `starred_at < syncStartMs` and only
     for ids outside `serverStarred ∪ pushedIds` — offline likes survive
     every sync until confirmed, then converge.
   The `clearNonStarred*` DAO queries gained the `beforeMs` bound; the clear
   is skipped entirely when the protected set is empty.

## Review addendum (2026-08-23, round 2) — local intent vs the mirror

The mirror was still server-authoritative in one direction: `setStarredAtBulk`
re-starred **anything** the server listed — resurrecting likes the user removed
locally (offline unlike) and colliding with dislikes (both thumbs lit). Fixed
with explicit local-intent tracking (migration 43→44):

1. **`pending_unstar_at` intent marker** on `tracks`/`albums`/`artists`. Set by
   local unlike/dislike (repo), cleared by local like/star. The mirror:
   - **never re-stars** rows that are `pending_unstar ∪ disliked`;
   - **pushes the unstar back** (chunked, comma-joined `unstar` calls) for
     locally-removed rows the server still lists;
   - **clears the marker** once the server confirms (row absent from the
     server list, or the unstar push succeeded); failures keep the marker so
     the skip persists and the push retries.
2. **Chunked push-back** (50 ids/call) for both star and unstar pushes —
   URL-length safety for long offline sessions.
3. The `last_star_sync_ms` pref was removed (dead state — protection uses the
   per-run `syncStartMs` watermark).

Resulting invariants:
- A server-starred entity that the user dislikes stays disliked locally, gets
  unstarred on the server, and never flips back.
- An offline unlike never resurrects; an offline like never vanishes.
- Re-liking clears the intent marker and pushes the star again.
