package com.lucasdss.ftpmusic.app.data.model

data class Album(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val year: Int? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val genre: String? = null,
    val rating: Int? = null,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String? = null,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val duration: Int? = null,
    val trackNumber: Int? = null,
    val bitrate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val path: String? = null,
    val coverArt: String? = null,
    val sizeBytes: Int? = null,
    val userRating: Int? = null,
) {
    val formattedDuration: String
        get() {
            val d = duration ?: return "--:--"
            val m = d / 60
            val s = d % 60
            return "$m:${s.toString().padStart(2, '0')}"
        }
}

data class AlbumWithTracks(val album: Album, val tracks: List<Track>)

data class Artist(val id: String, val name: String, val coverArt: String? = null, val albumCount: Int? = null)

data class SearchResults(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty() && playlists.isEmpty()
    val totalCount: Int get() = artists.size + albums.size + tracks.size + playlists.size
}

data class Playlist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val songCount: Int = 0,
    val duration: Int? = null,
    val coverArt: String? = null,
)
