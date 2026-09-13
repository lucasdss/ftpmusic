package com.lucasdss.ftpmusic.app.data.network

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface SubsonicApi {

    @GET("rest/ping")
    suspend fun ping(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "ftpmusic",
        @Query("f") format: String = "json",
    ): Map<String, Any>

    @GET("rest/search3")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 20,
        @Query("artistOffset") artistOffset: Int = 0,
        @Query("albumOffset") albumOffset: Int = 0,
        @Query("songOffset") songOffset: Int = 0,
        @QueryMap auth: Map<String, String>,
    ): Map<String, Any>

    @GET("rest/getAlbum")
    suspend fun getAlbum(@Query("id") id: String, @QueryMap auth: Map<String, String>): Map<String, Any>

    @GET("rest/getArtist")
    suspend fun getArtist(@Query("id") id: String, @QueryMap auth: Map<String, String>): Map<String, Any>

    @GET("rest/getArtists")
    suspend fun getArtists(@QueryMap auth: Map<String, String>): Map<String, Any>

    @GET("rest/getAlbumList2")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0,
        @QueryMap auth: Map<String, String>,
    ): Map<String, Any>

    @GET("rest/star")
    suspend fun star(
        @QueryMap params: Map<String, String>,
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): Map<String, Any>

    @GET("rest/unstar")
    suspend fun unstar(
        @QueryMap params: Map<String, String>,
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): Map<String, Any>

    @GET("rest/getStarred2")
    suspend fun getStarred2(@QueryMap params: Map<String, String>): Map<String, Any>

    @GET("rest/scrobble")
    suspend fun scrobble(
        @QueryMap params: Map<String, String>,
        @Query("id") id: String,
        @Query("submission") submission: Boolean = false,
    ): Map<String, Any>

    @GET("rest/getSimilarSongs2")
    suspend fun getSimilarSongs2(
        @QueryMap params: Map<String, String>,
        @Query("id") id: String,
        @Query("count") count: Int = 10,
    ): Map<String, Any>

    @GET("rest/savePlayQueue")
    suspend fun savePlayQueue(
        @QueryMap params: Map<String, String>,
        @Query("id") ids: String,
        @Query("current") current: String? = null,
        @Query("position") position: Long? = null,
    ): Map<String, Any>

    @GET("rest/deletePlaylist")
    suspend fun deletePlaylist(@QueryMap params: Map<String, String>, @Query("id") id: String): Map<String, Any>

    @GET("rest/setRating")
    suspend fun setRating(
        @QueryMap params: Map<String, String>,
        @Query("id") id: String,
        @Query("rating") rating: Int,
    ): Map<String, Any>

    @GET("rest/getGenres")
    suspend fun getGenres(@QueryMap params: Map<String, String>): Map<String, Any>

    @GET("rest/getSongsByGenre")
    suspend fun getSongsByGenre(
        @QueryMap params: Map<String, String>,
        @Query("genre") genre: String,
        @Query("count") count: Int = 100,
        @Query("offset") offset: Int = 0,
    ): Map<String, Any>

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(@QueryMap params: Map<String, String>): Map<String, Any>

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(@QueryMap params: Map<String, String>, @Query("id") id: String): Map<String, Any>

    @GET("rest/createPlaylist")
    suspend fun createPlaylist(
        @QueryMap params: Map<String, String>,
        @Query("name") name: String,
        @Query("songId") songIds: String = "",
    ): Map<String, Any>

    @GET("rest/updatePlaylist")
    suspend fun updatePlaylist(
        @QueryMap params: Map<String, String>,
        @Query("playlistId") playlistId: String,
        @Query("name") name: String = "",
        @Query("songIndexToRemove") removeIndices: String = "",
        @Query("songIdToAdd") addIds: String = "",
    ): Map<String, Any>

    @GET("rest/getLyrics")
    suspend fun getLyrics(
        @QueryMap params: Map<String, String>,
        @Query("artist") artist: String? = null,
        @Query("title") title: String? = null,
    ): Map<String, Any>

    @GET("rest/getInternetRadioStations")
    suspend fun getInternetRadioStations(@QueryMap params: Map<String, String>): Map<String, Any>

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @QueryMap auth: Map<String, String>,
        @Query("size") size: Int = 50,
        @Query("genre") genre: String? = null,
        @Query("fromYear") fromYear: Int? = null,
        @Query("toYear") toYear: Int? = null,
        @Query("musicFolderId") musicFolderId: String? = null,
    ): Map<String, Any>
}
