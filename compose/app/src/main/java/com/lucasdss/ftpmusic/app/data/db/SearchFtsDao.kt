package com.lucasdss.ftpmusic.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SearchFtsDao {
    @Query(
        """
        SELECT rowid AS rowid, entity_type, entity_id, body
        FROM search_fts
        WHERE search_fts MATCH :matchQuery
        LIMIT :limit
        """,
    )
    suspend fun match(matchQuery: String, limit: Int): List<SearchFtsEntity>

    @Query("SELECT COUNT(*) FROM search_fts")
    suspend fun count(): Int

    @Query("DELETE FROM search_fts")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SearchFtsEntity>)
}
