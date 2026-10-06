package com.lucasdss.ftpmusic.app.data.db

/** Projection for FTS rebuild — includes album year via JOIN. */
data class TrackSearchIndexRow(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val path: String?,
    val year: Int?,
    val musicbrainzId: String?,
)
