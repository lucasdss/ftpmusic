package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.model.Album
import com.lucasdss.ftpmusic.app.data.model.AlbumWithTracks
import com.lucasdss.ftpmusic.app.data.model.Artist
import com.lucasdss.ftpmusic.app.data.model.Playlist
import com.lucasdss.ftpmusic.app.data.model.SearchResults
import com.lucasdss.ftpmusic.app.data.model.Track
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepository @Inject constructor(private val api: SubsonicApi) {
    /**
     * Fetch album with all tracks from the server.
     * Returns null if the response is malformed or missing.
     */
    suspend fun getAlbum(albumId: String, username: String, password: String): AlbumWithTracks? {
        val auth = SubsonicAuthHelper().buildAuthParams(username, password)
        val response = api.getAlbum(albumId, auth)
        return parseAlbumResponse(response)
    }

    private fun parseAlbumResponse(response: Map<String, Any>): AlbumWithTracks? {
        val subsonicResponse = response["subsonic-response"] as? Map<*, *> ?: return null
        val albumData = subsonicResponse["album"] as? Map<*, *> ?: return null

        val album = Album(
            id = albumData["id"] as? String ?: return null,
            name = albumData["name"] as? String ?: return null,
            artist = albumData["artist"] as? String,
            artistId = albumData["artistId"] as? String,
            year = (albumData["year"] as? Number)?.toInt(),
            coverArt = albumData["coverArt"] as? String,
            songCount = (albumData["songCount"] as? Number)?.toInt(),
            duration = (albumData["duration"] as? Number)?.toInt(),
            genre = albumData["genre"] as? String,
        )

        val songs = albumData["song"] as? List<*> ?: return AlbumWithTracks(album, emptyList())
        val tracks = songs.mapNotNull { song ->
            val s = song as? Map<*, *> ?: return@mapNotNull null
            Track(
                id = s["id"] as? String ?: return@mapNotNull null,
                title = s["title"] as? String ?: return@mapNotNull null,
                artist = s["artist"] as? String,
                artistId = s["artistId"] as? String,
                album = s["album"] as? String,
                albumId = s["albumId"] as? String,
                duration = (s["duration"] as? Number)?.toInt(),
                trackNumber = (s["track"] as? Number)?.toInt(),
                bitrate = (s["bitrate"] as? Number)?.toInt(),
                suffix = s["suffix"] as? String,
                contentType = s["contentType"] as? String,
                path = s["path"] as? String,
                coverArt = s["coverArt"] as? String,
                sizeBytes = (s["size"] as? Number)?.toInt(),
            )
        }

        return AlbumWithTracks(album, tracks)
    }
}

@Singleton
class SearchRepository @Inject constructor(private val api: SubsonicApi) {
    suspend fun search(
        query: String,
        username: String,
        password: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 20,
        artistOffset: Int = 0,
        albumOffset: Int = 0,
        songOffset: Int = 0,
    ): SearchResults {
        if (query.length < 2) return SearchResults()
        val auth = SubsonicAuthHelper().buildAuthParams(username, password)
        val response = api.search3(
            query,
            artistCount,
            albumCount,
            songCount,
            artistOffset,
            albumOffset,
            songOffset,
            auth,
        )
        return parseSearchResponse(response)
    }

    private fun parseSearchResponse(response: Map<String, Any>): SearchResults {
        val subsonicResponse = response["subsonic-response"] as? Map<*, *> ?: return SearchResults()
        val searchResult = subsonicResponse["searchResult3"] as? Map<*, *> ?: return SearchResults()

        val artists = (searchResult["artist"] as? List<*>)?.mapNotNull { a ->
            val m = a as? Map<*, *> ?: return@mapNotNull null
            Artist(
                id = m["id"] as? String ?: return@mapNotNull null,
                name = m["name"] as? String ?: return@mapNotNull null,
                coverArt = m["coverArt"] as? String,
                albumCount = (m["albumCount"] as? Number)?.toInt(),
            )
        } ?: emptyList()

        val albums = (searchResult["album"] as? List<*>)?.mapNotNull { a ->
            val m = a as? Map<*, *> ?: return@mapNotNull null
            Album(
                id = m["id"] as? String ?: return@mapNotNull null,
                name = m["name"] as? String ?: return@mapNotNull null,
                artist = m["artist"] as? String,
                artistId = m["artistId"] as? String,
                year = (m["year"] as? Number)?.toInt(),
                coverArt = m["coverArt"] as? String,
                rating = (m["userRating"] as? Number)?.toInt(),
            )
        } ?: emptyList()

        val tracks = (searchResult["song"] as? List<*>)?.mapNotNull { t ->
            val m = t as? Map<*, *> ?: return@mapNotNull null
            Track(
                id = m["id"] as? String ?: return@mapNotNull null,
                title = m["title"] as? String ?: return@mapNotNull null,
                artist = m["artist"] as? String,
                artistId = m["artistId"] as? String,
                album = m["album"] as? String,
                albumId = m["albumId"] as? String,
                duration = (m["duration"] as? Number)?.toInt(),
                coverArt = m["coverArt"] as? String,
            )
        } ?: emptyList()

        val playlists = (searchResult["playlist"] as? List<*>)?.mapNotNull { p ->
            val m = p as? Map<*, *> ?: return@mapNotNull null
            Playlist(
                id = m["id"] as? String ?: return@mapNotNull null,
                name = m["name"] as? String ?: return@mapNotNull null,
                comment = m["comment"] as? String,
                songCount = (m["songCount"] as? Number)?.toInt() ?: 0,
                duration = (m["duration"] as? Number)?.toInt(),
                coverArt = m["coverArt"] as? String,
            )
        } ?: emptyList()

        return SearchResults(artists, albums, tracks, playlists)
    }
}
