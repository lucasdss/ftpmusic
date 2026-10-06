# ADR 0080 — Search FTS local index

Date: 2026-10-06
Status: Accepted
Related: ADR 0077–0079

## Context

Market apps use tokenized full-text search. Phase-1 used multi-field LIKE +
in-memory ranking. Large libraries need FTS for speed and tokenization.

## Decision

1. Room `@Fts4(tokenizer = unicode61)` table `search_fts` with
   `(entity_type, entity_id, body)`. Room 2.6.1 has no FTS5 annotation API;
   FTS4 + unicode61 delivers tokenized MATCH (ADR goal). Upgrade to FTS5 when
   Room exposes it without a large dependency jump.
2. `SearchIndexRebuilder` rebuilds after metadata sync + enrichment.
3. `LocalSearchRepository` queries FTS when `count() > 0`, else LIKE fallback.
4. Optional lyrics rows when `KEY_SEARCH_LYRICS` is true (Settings).
5. Enrichment columns: `cached_artists.biography` / `search_aliases`,
   `cached_albums.notes` (filled by getArtistInfo2 / getAlbumInfo2 in background).

## Consequences

- DB v59 migration creates FTS + enrichment columns.
- First search after upgrade uses LIKE until sync rebuilds FTS.
- Voice search prefers LocalSearchRepository then search3.
