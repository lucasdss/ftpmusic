package com.lucasdss.ftpmusic.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.FtsOptions
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Unified FTS search corpus (ADR 0080).
 * Room 2.6 exposes FTS4; unicode61 tokenizer gives tokenized MATCH.
 * entity_type: track | album | artist | playlist | genre | lyrics
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "search_fts")
data class SearchFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    val body: String,
)

object SearchFtsTypes {
    const val TRACK = "track"
    const val ALBUM = "album"
    const val ARTIST = "artist"
    const val PLAYLIST = "playlist"
    const val GENRE = "genre"
    const val LYRICS = "lyrics"
}
