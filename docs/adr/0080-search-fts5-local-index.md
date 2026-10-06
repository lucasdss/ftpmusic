# ADR 0080 — Search FTS5 local index (deferred)

Date: 2026-10-06
Status: Proposed (deferred)
Related: ADR 0077–0079

## Context

Market apps use tokenized full-text search and light typo tolerance. FTP Music
now uses multi-field `LIKE … ESCAPE` + in-memory ranking. Large libraries may
need FTS5 for speed and tokenization.

## Decision (deferred)

Ship multi-field LIKE first. Follow-up when profiling shows need:

1. Room `@Fts4` / FTS5 virtual table keyed by `(entity_type, entity_id, body)`.
2. Rebuild on metadata upsert / MetadataSyncWorker.
3. Query FTS → hydrate by id → union with `search3` (ADR 0077).
4. Optional lyrics body behind user setting.

## Consequences

- No FTS migration in v58.
- Ranking/diacritic fold already in `SearchResultMerger` / `SearchQueryNormalizer`
  for reuse when FTS lands.
