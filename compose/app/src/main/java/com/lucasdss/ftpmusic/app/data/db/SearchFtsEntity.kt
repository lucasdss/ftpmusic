package com.lucasdss.ftpmusic.app.data.db

import androidx.room.ColumnInfo

/**
 * FTS5 search corpus row (ADR 0084). Not a Room @Entity — table created via
 * [SearchFtsSchema] (migration + [AppDatabase] callback) because Room has no
 * `@Fts5` annotation (Room 2.8.x still only `@Fts3`/`@Fts4`).
 *
 * entity_type: track | album | artist | playlist | genre | lyrics
 * [rank] populated only on MATCH queries via bm25(); ignored on insert.
 */
data class SearchFtsEntity(
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    val body: String,
    /** SQLite bm25() score — lower is better. 0 when not from MATCH. */
    val rank: Double = 0.0,
)

object SearchFtsTypes {
    const val TRACK = "track"
    const val ALBUM = "album"
    const val ARTIST = "artist"
    const val PLAYLIST = "playlist"
    const val GENRE = "genre"
    const val LYRICS = "lyrics"
}

/** Shared FTS5 DDL — migration 60→61 + fresh onCreate. */
object SearchFtsSchema {
    const val DROP = "DROP TABLE IF EXISTS `search_fts`"

    /**
     * entity_id UNINDEXED (lookup only). body heavily weighted in bm25().
     * tokenize unicode61 matches prior FTS4 behaviour.
     */
    val CREATE_FTS5 = """
        CREATE VIRTUAL TABLE IF NOT EXISTS `search_fts` USING FTS5(
          `entity_type`,
          `entity_id` UNINDEXED,
          `body`,
          tokenize = 'unicode61'
        )
    """.trimIndent()

    /**
     * bm25 weights: entity_type lightly indexed (lyrics vs track), entity_id
     * ignored by FTS5 when UNINDEXED (weight slot still required), body high.
     */
    const val BM25_WEIGHTS = "1.0, 0.0, 10.0"
}
