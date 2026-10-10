package com.lucasdss.ftpmusic.app.data.cover

/** Stored in `fixed_cover_kind` on custom_mixes / playlists (ADR 0115). */
object CollectionCoverKind {
    const val NAVIDROME = "navidrome"
    const val LOCAL = "local"

    fun isValid(kind: String?): Boolean = kind == NAVIDROME || kind == LOCAL
}
