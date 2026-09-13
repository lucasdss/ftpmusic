package com.lucasdss.ftpmusic.app.playback

import android.os.Bundle
import androidx.media3.common.MediaItem

const val QUEUE_ENTRY_ID_EXTRA = "queueEntryId"

/**
 * JVM unit tests stub [Bundle] / [MediaItem] tag. Remember ids by instance
 * so Dual / projection / Cast tests still see stamps.
 */
internal object QueueEntryIdMemory {
    private val map = object : LinkedHashMap<Int, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Int>?): Boolean = size > 2048
    }

    @Synchronized
    fun put(item: MediaItem, id: Int) {
        if (id > 0) map[System.identityHashCode(item)] = id
    }

    @Synchronized
    fun get(item: MediaItem): Int = map[System.identityHashCode(item)] ?: 0
}

fun MediaItem.queueEntryId(): Int {
    QueueEntryIdMemory.get(this).takeIf { it > 0 }?.let { return it }
    (localConfiguration?.tag as? Int)?.takeIf { it > 0 }?.let { return it }
    return mediaMetadata.extras?.getInt(QUEUE_ENTRY_ID_EXTRA, 0) ?: 0
}

fun MediaItem.hasQueueEntryId(): Boolean = queueEntryId() > 0

fun MediaItem.withQueueEntryId(id: Int): MediaItem {
    if (id <= 0) return this
    if (queueEntryId() == id) return this
    val extras = Bundle(mediaMetadata.extras ?: Bundle())
    extras.putInt(QUEUE_ENTRY_ID_EXTRA, id)
    val built = buildUpon()
        .setTag(id)
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
    QueueEntryIdMemory.put(built, id)
    return built
}
