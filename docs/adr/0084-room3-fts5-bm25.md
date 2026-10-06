# ADR 0084 — Room 2.7.2 + FTS5 / BM25 (SupportSQLite)

Date: 2026-10-06
Status: Accepted (Phase-7; amended Phase-7 review)
Supersedes (engine choice): ADR 0080, ADR 0083
Related: ADR 0078, 0079, 0081

## Context

Market apps rank by relevance. Phase-6 kept FTS4 + app lexical rank because
Room 2.6 only exposed `@Fts4` and device SQLite often lacks FTS5.
Maven has no separate "Room 3" artifact; Room **2.7.2** adds
`BundledSQLiteDriver` but existing migrations only override
`migrate(SupportSQLiteDatabase)`.

Room still has **no `@Fts5` annotation** — only `@Fts3`/`@Fts4`.

**Amendment (review):** Shipping `setDriver(BundledSQLiteDriver())` without
dual `migrate(SQLiteConnection)` on every migration causes
`NotImplementedError` on upgrade and no-ops `Callback.onCreate` (FTS missing).
`SearchFtsDao` via `openHelper`/`query` also throws when the driver is set.
Bundled driver is therefore **deferred**.

## Decision

1. Bump Room to **2.7.2** (2.8.x blocked: kotlinx-serialization 1.8 needs
   Kotlin 2.1+; project is Kotlin 2.0.21).
2. `DatabaseModule` uses **SupportSQLite** (no `setDriver`).
   `FTS5_CALLBACK` onCreate + onOpen with try/catch CREATE IF NOT EXISTS.
3. Remove `search_fts` from Room `@Database` entities. Create via
   `SearchFtsSchema.CREATE_FTS5` on migrate **60→61** and callback.
4. `SearchFtsDao` is a `@Singleton` store using `RoomDatabase.query` /
   `openHelper` (not Room `@Dao`).
5. MATCH uses `ORDER BY bm25(search_fts, 1.0, 0.0, 10.0)` (body weighted).
6. Hydrate preserves BM25 order; `SearchResultMerger` fuses exact → BM25 →
   lexical → popularity. Over-fetch `limit * 3`.
7. Rebuild full-table after sync/enrich (never keystroke); clear+insert
   one transaction.
8. **Follow-up:** dual-override all migrations for `SQLiteConnection`, then
   re-enable `BundledSQLiteDriver` for FTS5 on every API level.

## Consequences

- DB version **61**. Existing installs DROP FTS4 + CREATE FTS5; index empty
  until next `SearchIndexRebuilder.rebuildAll`.
- **Empty-FTS backstop:** `ensureIndexed()` rebuilds when `ftsCount==0` and
  Room still has tracks/artists/albums (cold start / sync skipped).
- `replaceAll` failure invalidates `cachedFtsCount` (null) so next count
  re-queries the live table — does not force permanent LIKE.
- Schema JSON no longer lists `search_fts` (external virtual table).
- Devices without FTS5: CREATE fails quietly → LIKE fallback.
- ADR 0080/0083 engine decisions superseded; lyrics SERP + popularity
  tie-break remain.
