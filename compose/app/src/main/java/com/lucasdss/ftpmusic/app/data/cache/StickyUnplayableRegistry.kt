package com.lucasdss.ftpmusic.app.data.cache

/**
 * Process-local sticky CT / unplayable track ids so Previous can walk without a
 * Room hop after download or playback marks `failed_ct`.
 */
object StickyUnplayableRegistry {
    private val ids = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun mark(trackId: String) {
        if (trackId.isNotBlank()) ids.add(trackId)
    }

    fun contains(trackId: String): Boolean = ids.contains(trackId)

    fun clearForTests() {
        ids.clear()
    }
}
