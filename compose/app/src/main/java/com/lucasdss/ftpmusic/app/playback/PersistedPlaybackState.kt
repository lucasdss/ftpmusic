package com.lucasdss.ftpmusic.app.playback

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_state")
data class PersistedPlaybackState(
    @PrimaryKey val id: Int = 1, // single-row table
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val coverArtId: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val sleepTimerEndMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)
