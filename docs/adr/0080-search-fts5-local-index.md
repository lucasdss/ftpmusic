# ADR 0080 — Search FTS local index

Date: 2026-10-06
Status: Accepted (Phase-3)
Related: ADR 0077–0079

## Context

Market apps use tokenized full-text search. Phase-1 LIKE; Phase-2 FTS4;
Phase-3 keeps index fresh and adds soft typo + year tokens.

## Decision

1. Room `@Fts4(tokenizer = unicode61)` table `search_fts`
   `(entity_type, entity_id, body)`. FTS5 deferred until Room API bump.
2. `SearchIndexRebuilder.rebuildAll` after metadata sync + enrichment.
3. `scheduleRebuild` debounced after `cacheServerResults` and lyrics toggle.
4. `LocalSearchRepository`: FTS → soft edit-distance ≤1 → LIKE.
5. Optional lyrics rows when `KEY_SEARCH_LYRICS` is true.
6. Track FTS body includes album year + decade labels via JOIN projection.

## Consequences

- DB v59 FTS + enrichment; v60 `search_tags`.
- Cold start: LIKE until sync rebuilds; Search shows "Indexing library…".
- Voice search prefers LocalSearchRepository then search3.
