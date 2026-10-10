# ADR 0108 — Tracks Corpus Reconcile on FULL Heal

Date: 2026-10-10
Status: Accepted
Related: ADR 0045 (adaptive delta), ADR 0068 (idempotency), ADR 0085 (orphan densify),
docs/LIBRARY_SYNC_BEHAVIOR_REPORT.md

## Context

ADR-0085 densifies the search corpus (`tracks`) via album populate, genre
offset, and `getRandomSongs`. FULL heal already diff-deletes missing
`cached_albums` and prunes `cached_album_tracks` orphans, but **never deleted
from `tracks`**. Subsonic ID churn + random densify therefore left Syncing
**Tracks** and local corpus counts far above server library size. Settings
Resync always writes `last_full_sync_ms` only, so **Last delta sync** stayed
Never unless periodic WorkManager completed a DELTA.

## Decision

1. **FULL corpus reconcile** — after populate on `LibrarySyncMode.FULL` (incl.
   force Resync), run `TrackDao.reconcileSearchCorpusAgainstCatalog()`:
   delete `tracks` rows whose id is not in `cached_album_tracks` ∪
   `cached_genre_songs`, unless local weight remains (starred, disliked,
   downloaded, cached file, or `play_count > 0`).
2. **DELTA** — no corpus prune (upsert-only densify continues).
3. **Honest metrics** — Settings shows album-track meta, search corpus, and
   `SUM(song_count)` separately; prefs record `last_sync_mode` and
   `last_sync_skip_reason`.
4. **Syncing Tracks row** — always `COUNT(tracks)` (never session fetch sum /
   `cached_album_tracks` seed).

## Consequences

- FULL Resync can shrink inflated search corpus toward catalog + intentional
  local rows.
- Settings Cached Tracks (album meta) still reflects Subsonic `getAlbum` unique
  ids — if that is ~70K, server song set is the SoT for that metric.
- Delta watermark advances only on completed periodic DELTA; UI copy states
  Resync is always FULL.
- Played/starred ghosts of deleted server ids survive until local weight clears.
