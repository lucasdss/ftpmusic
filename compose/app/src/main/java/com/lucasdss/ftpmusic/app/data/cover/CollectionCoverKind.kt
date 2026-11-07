package com.lucasdss.ftpmusic.app.data.cover

/** Stored in `fixed_cover_kind` on custom_mixes / playlists / genres (ADR 0115 / 0151). */
object CollectionCoverKind {
    const val NAVIDROME = "navidrome"
    const val LOCAL = "local"
    /** Bundled pack asset path under `assets/` (ADR 0151 Defaults pin). */
    const val BUNDLED = "bundled"

    fun isValid(kind: String?): Boolean =
        kind == NAVIDROME || kind == LOCAL || kind == BUNDLED
}
