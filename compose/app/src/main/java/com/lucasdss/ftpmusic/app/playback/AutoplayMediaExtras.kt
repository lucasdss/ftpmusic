package com.lucasdss.ftpmusic.app.playback

import android.os.Bundle
import androidx.media3.common.MediaItem

/** MediaMetadata extras key — Continuous Play / Autoplay tail (ADR-0053). */
const val IS_AUTOPLAY_EXTRA = "is_autoplay"

/**
 * JVM unit tests stub [Bundle]; remember autoplay by instance like [QueueEntryIdMemory].
 */
internal object AutoplayFlagMemory {
    private val map = object : LinkedHashMap<Int, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>?): Boolean = size > 2048
    }

    @Synchronized
    fun put(item: MediaItem, flag: Boolean) {
        map[System.identityHashCode(item)] = flag
    }

    @Synchronized
    fun get(item: MediaItem): Boolean? = map[System.identityHashCode(item)]
}

fun MediaItem.isAutoplay(): Boolean {
    AutoplayFlagMemory.get(this)?.let { return it }
    return mediaMetadata.extras?.getBoolean(IS_AUTOPLAY_EXTRA, false) == true
}

fun MediaItem.withAutoplay(flag: Boolean = true): MediaItem {
    if (isAutoplay() == flag) return this
    val extras = Bundle(mediaMetadata.extras ?: Bundle())
    extras.putBoolean(IS_AUTOPLAY_EXTRA, flag)
    val built = buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
    AutoplayFlagMemory.put(built, flag)
    return built
}
