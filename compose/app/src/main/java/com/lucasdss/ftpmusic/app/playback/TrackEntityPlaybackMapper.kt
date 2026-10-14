package com.lucasdss.ftpmusic.app.playback

import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import com.lucasdss.ftpmusic.app.data.model.Track

/** Room track → playback [Track] for Auto queue commands (ADR-0137). */
fun TrackEntity.toPlaybackTrack(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    duration = durationSeconds,
    trackNumber = trackNumber,
    discNumber = discNumber,
    bitrate = bitrate,
    suffix = suffix,
    contentType = contentType,
    path = path,
    coverArt = coverArtUrl,
    sizeBytes = sizeBytes,
    userRating = userRating,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
)
