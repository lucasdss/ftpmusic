package com.lucasdss.ftpmusic.app.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FTS5 access outside Room entity schema (ADR 0084).
 * Table created via [SearchFtsSchema] migration + [AppDatabase.FTS5_CALLBACK].
 */
@Singleton
class SearchFtsDao @Inject constructor(private val db: AppDatabase) {
    /** MATCH ordered by bm25 (lower = better). */
    suspend fun match(matchQuery: String, limit: Int): List<SearchFtsEntity> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT rowid AS rowid, entity_type, entity_id, body,
                   bm25(search_fts, ${SearchFtsSchema.BM25_WEIGHTS}) AS rank
            FROM search_fts
            WHERE search_fts MATCH ?
            ORDER BY rank
            LIMIT ?
        """.trimIndent()
        val query = SimpleSQLiteQuery(sql, arrayOf(matchQuery, limit))
        db.query(query).use { c ->
            val rowidIdx = c.getColumnIndexOrThrow("rowid")
            val typeIdx = c.getColumnIndexOrThrow("entity_type")
            val idIdx = c.getColumnIndexOrThrow("entity_id")
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val rankIdx = c.getColumnIndexOrThrow("rank")
            buildList {
                while (c.moveToNext()) {
                    add(
                        SearchFtsEntity(
                            rowId = c.getLong(rowidIdx),
                            entityType = c.getString(typeIdx),
                            entityId = c.getString(idIdx),
                            body = c.getString(bodyIdx),
                            rank = c.getDouble(rankIdx),
                        ),
                    )
                }
            }
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM search_fts")).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL("DELETE FROM search_fts")
    }

    suspend fun insertAll(rows: List<SearchFtsEntity>) = withContext(Dispatchers.IO) {
        if (rows.isEmpty()) return@withContext
        val sqlite = db.openHelper.writableDatabase
        sqlite.beginTransaction()
        try {
            insertChunks(sqlite, rows)
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    /** Atomic clear + insert — no empty-index window mid-rebuild. */
    suspend fun replaceAll(rows: List<SearchFtsEntity>) = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.beginTransaction()
        try {
            sqlite.execSQL("DELETE FROM search_fts")
            if (rows.isNotEmpty()) insertChunks(sqlite, rows)
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun insertChunks(sqlite: androidx.sqlite.db.SupportSQLiteDatabase, rows: List<SearchFtsEntity>) {
        for (chunk in rows.chunked(100)) {
            val placeholders = chunk.joinToString(",") { "(?,?,?)" }
            val args = ArrayList<Any?>(chunk.size * 3)
            for (row in chunk) {
                args.add(row.entityType)
                args.add(row.entityId)
                args.add(row.body)
            }
            sqlite.execSQL(
                "INSERT INTO search_fts(entity_type, entity_id, body) VALUES $placeholders",
                args.toArray(),
            )
        }
    }
}
