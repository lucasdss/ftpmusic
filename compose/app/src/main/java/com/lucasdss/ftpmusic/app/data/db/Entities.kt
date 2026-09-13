package com.lucasdss.ftpmusic.app.data.db

import androidx.room.*

// --- v1: Core tables ---

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val username: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String = "",
    @ColumnInfo(name = "auth_type") val authType: String = "password",
)

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String = "",
    val name: String,
    @ColumnInfo(name = "cover_art_url") val coverArtUrl: String? = null,
    @ColumnInfo(name = "musicbrainz_id") val musicbrainzId: String? = null,
    @ColumnInfo(name = "starred_at") val starredAt: Long? = null,
    // v43: Local dislike (thumbs down). Mutually exclusive with like (= starred_at).
    @ColumnInfo(name = "is_disliked", defaultValue = "0") val isDisliked: Boolean = false,
    // v44: Local-first intent marker (see TrackEntity.pendingUnstarAt).
    @ColumnInfo(name = "pending_unstar_at") val pendingUnstarAt: Long? = null,
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String = "",
    @ColumnInfo(name = "artist_id") val artistId: String? = null,
    val name: String,
    // v43: artist display name carried by the ledger (from cached_albums.artist)
    val artist: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "cover_art_url") val coverArtUrl: String? = null,
    val genre: String? = null,
    @ColumnInfo(name = "starred_at") val starredAt: Long? = null,
    @ColumnInfo(name = "user_rating") val userRating: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,
    // v43: Local dislike (thumbs down). Mutually exclusive with like (= starred_at).
    @ColumnInfo(name = "is_disliked", defaultValue = "0") val isDisliked: Boolean = false,
    // v44: Local-first intent marker (see TrackEntity.pendingUnstarAt).
    @ColumnInfo(name = "pending_unstar_at") val pendingUnstarAt: Long? = null,
)

// --- v1/v2: Tracks with cache columns added in v4 ---

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["server_id"]),
        Index(value = ["album_id"]),
        Index(value = ["artist_id"]),
        Index(value = ["cached_file_path"]),
        Index(value = ["play_count"]),
        // v45: hot query paths (Favorites, Recently Played, genre mixes, ratings)
        Index(value = ["starred_at"]),
        Index(value = ["last_played_at"]),
        Index(value = ["genre"]),
        Index(value = ["user_rating"]),
        Index(value = ["is_disliked"]),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String = "",
    @ColumnInfo(name = "album_id") val albumId: String? = null,
    @ColumnInfo(name = "artist_id") val artistId: String? = null,
    val title: String,
    val artist: String? = null,
    val genre: String? = null,
    @ColumnInfo(name = "track_number") val trackNumber: Int? = null,
    @ColumnInfo(name = "disc_number") val discNumber: Int? = null,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    val bitrate: Int? = null,
    val suffix: String? = null,
    @ColumnInfo(name = "content_type") val contentType: String? = null,
    val path: String? = null,
    @ColumnInfo(name = "cover_art_url") val coverArtUrl: String? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Int? = null,
    @ColumnInfo(name = "starred_at") val starredAt: Long? = null,
    @ColumnInfo(name = "user_rating") val userRating: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long? = null,

    // v4: Play tracking
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "play_count", defaultValue = "0") val playCount: Int = 0,
    @ColumnInfo(name = "cached_at") val cachedAt: Long? = null,

    // v4: Cache support
    @ColumnInfo(name = "is_downloaded", defaultValue = "0") val isDownloaded: Boolean = false,
    @ColumnInfo(name = "is_auto_cached", defaultValue = "0") val isAutoCached: Boolean = false,
    @ColumnInfo(name = "cached_file_path") val cachedFilePath: String? = null,
    @ColumnInfo(name = "cache_size_bytes") val cacheSizeBytes: Int? = null,

    // v38: MusicBrainz public rating + MBID cache
    @ColumnInfo(name = "public_rating") val publicRating: Double? = null,
    @ColumnInfo(name = "public_rating_votes") val publicRatingVotes: Int? = null,
    @ColumnInfo(name = "musicbrainz_id") val musicbrainzId: String? = null,

    // v41: Local dislike (thumbs down). Mutually exclusive with like (= starred_at).
    @ColumnInfo(name = "is_disliked", defaultValue = "0") val isDisliked: Boolean = false,

    // v44: Local-first intent marker — set when the user unstars/dislikes while
    // offline. The star mirror skips re-starring these rows and pushes the
    // unstar to the server; cleared once the server confirms (or on re-like).
    @ColumnInfo(name = "pending_unstar_at") val pendingUnstarAt: Long? = null,
)

// --- v3: Playlists ---

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String = "",
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    @ColumnInfo(name = "is_public") val isPublic: Boolean = false,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0,
    val coverArt: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_conflicted", defaultValue = "0") val isConflicted: Boolean = false,
    @ColumnInfo(name = "conflict_message") val conflictMessage: String? = null,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
)

@Entity(
    tableName = "playlist_entries",
    indices = [Index(value = ["playlist_id"], name = "idx_playlist_entries_playlist_id")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val position: Int,
)

/** v43: Bookmarked internet radio stations (local-only favorites — Subsonic
 *  has no star endpoint for radio). Bookmarked stations appear on Home and
 *  in the Favorites tab. */
@Entity(tableName = "radio_favorites")
data class RadioFavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "station_id") val stationId: String,
    val name: String,
    @ColumnInfo(name = "stream_url") val streamUrl: String,
    @ColumnInfo(name = "home_page_url") val homePageUrl: String? = null,
    @ColumnInfo(name = "bookmarked_at") val bookmarkedAt: Long = System.currentTimeMillis(),
)

// --- Cached metadata for offline/startup availability ---

@Entity(
    tableName = "cached_albums",
    // v45: artist/name lookups (artist detail, offline library, search)
    indices = [
        Index(value = ["artist_id"]),
        Index(value = ["artist"]),
        Index(value = ["name"]),
    ],
)
data class CachedAlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String? = null,
    @ColumnInfo(name = "artist_id") val artistId: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "cover_art") val coverArt: String? = null,
    @ColumnInfo(name = "song_count") val songCount: Int? = null,
    val duration: Int? = null,
    val genre: String? = null,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),

    // v38: MusicBrainz public rating + MBID cache
    @ColumnInfo(name = "public_rating") val publicRating: Double? = null,
    @ColumnInfo(name = "public_rating_votes") val publicRatingVotes: Int? = null,
    @ColumnInfo(name = "musicbrainz_id") val musicbrainzId: String? = null,
)

/** Lazy-cached album tracks — populated when user opens an album. Survives offline. */
@Entity(
    tableName = "cached_album_tracks",
    indices = [
        Index(value = ["album_id"], name = "idx_cached_album_tracks_album_id"),
        // v45: artist join (albums-by-track-artist queries)
        Index(value = ["artist_id"]),
    ],
)
data class CachedAlbumTrackEntity(
    @PrimaryKey val id: String, // track ID
    @ColumnInfo(name = "album_id") val albumId: String,
    val title: String,
    val artist: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "artist_id") val artistId: String? = null,
    @ColumnInfo(name = "disc_number") val discNumber: Int? = null,
    val duration: Int? = null,
    @ColumnInfo(name = "track_number") val trackNumber: Int? = null,
    val bitrate: Int? = null,
    val size: Long? = null,
    @ColumnInfo(name = "cover_art") val coverArt: String? = null,
    val suffix: String? = null,
    @ColumnInfo(name = "content_type") val contentType: String? = null,
)

@Entity(tableName = "cached_artists")
data class CachedArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "cover_art") val coverArt: String? = null,
    @ColumnInfo(name = "album_count") val albumCount: Int? = null,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),

    // v38: MusicBrainz public rating + MBID cache
    @ColumnInfo(name = "public_rating") val publicRating: Double? = null,
    @ColumnInfo(name = "public_rating_votes") val publicRatingVotes: Int? = null,
    @ColumnInfo(name = "musicbrainz_id") val musicbrainzId: String? = null,

    // v39: Similar artists (last.fm) as JSON array of {name,mbid,match}
    @ColumnInfo(name = "similar_artists_json") val similarArtistsJson: String? = null,
)

@Entity(
    tableName = "pending_playlist_changes",
    indices = [Index(value = ["flushed", "created_at"], name = "idx_pending_playlist_changes_flushed_created_at")],
)
data class PendingPlaylistChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    @ColumnInfo(name = "change_type") val changeType: String,
    val payload: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val flushed: Boolean = false,
)

// --- v5: Cache Queue ---

@Entity(
    tableName = "cache_queue_items",
    indices = [
        // Unique: two concurrent enqueues for the same track must never create
        // two rows — both workers would write to the same temp file and
        // interleave bytes into a corrupt span. INSERT OR IGNORE + this index
        // makes enqueue atomic.
        Index(value = ["track_id"], unique = true),
        Index(value = ["status"]),
    ],
)
data class CacheQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "remote_url") val remoteUrl: String,
    val priority: Int = 2,
    val status: String = "pending",
    /** True if the user explicitly requested a permanent download (pinned).
     *  Kept separate from [priority] so a play-queue item (priority 0) later
     *  requested as a download is still pinned on completion. */
    @ColumnInfo(name = "is_download", defaultValue = "0") val isDownload: Boolean = false,
    @ColumnInfo(name = "retry_count", defaultValue = "0") val retryCount: Int = 0,
    @ColumnInfo(name = "downloaded_bytes", defaultValue = "0") val downloadedBytes: Long = 0,
    @ColumnInfo(name = "total_bytes", defaultValue = "0") val totalBytes: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

// --- v6: Session State ---

@Entity(tableName = "session_state")
data class SessionStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "queue_json") val queueJson: String,
    @ColumnInfo(name = "current_index", defaultValue = "0") val currentIndex: Int = 0,
    @ColumnInfo(name = "position_ms", defaultValue = "0") val positionMs: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_casting", defaultValue = "0") val isCasting: Boolean = false,
    @ColumnInfo(name = "cast_device_name") val castDeviceName: String? = null,
)

// --- v7: Playback Queue (individual rows, replaces session_state JSON blob) ---

@Entity(
    tableName = "queue_items",
    indices = [Index(value = ["position"])],
)
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "track_id") val trackId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    @ColumnInfo(name = "cover_art_id") val coverArtId: String? = null,
    @ColumnInfo(name = "artist_id") val artistId: String? = null,
    @ColumnInfo(name = "album_id") val albumId: String? = null,
    val url: String,
    val position: Int,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    /** True = user Queue (PRIORITY); false = CONTEXT / Continue Playing. */
    @ColumnInfo(name = "is_priority") val isPriority: Boolean = false,
    /** Monotonic occurrence id. 0 = legacy unstamped row. */
    @ColumnInfo(name = "entry_id") val entryId: Int = 0,
)

/** Single-row table for queue playback state (index, position, cast status). */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 1, // single row
    @ColumnInfo(name = "current_index") val currentIndex: Int = 0,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0L,
    @ColumnInfo(name = "is_casting") val isCasting: Boolean = false,
    @ColumnInfo(name = "cast_device_name") val castDeviceName: String? = null,
    // Number of tracks in the persisted queue that belong to the CONTEXT
    // section (album/playlist/mix). -1 = legacy save without the split →
    // treat everything as context. Preserved so a Cast disconnect/reconnect
    // can rebuild the dual-queue structure instead of absorbing priority
    // tracks into the context.
    @ColumnInfo(name = "context_size") val contextSize: Int = -1,
    /** Next unused queueEntryId after restore. */
    @ColumnInfo(name = "next_entry_id") val nextEntryId: Int = 1,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

// ── v11: Genres ────────────────────────────────────────────────────────────────

@Entity(tableName = "genres", indices = [Index(value = ["name"], unique = true)])
data class GenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "song_count") val songCount: Int = 0,
    @ColumnInfo(name = "album_count") val albumCount: Int = 0,
)

// ── v25: Genre Mixes ───────────────────────────────────────────────────────────

@Entity(tableName = "cached_genres")
data class CachedGenreEntity(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "song_count") val songCount: Int = 0,
    @ColumnInfo(name = "album_count") val albumCount: Int = 0,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cached_genre_songs", indices = [Index(value = ["genre"])])
data class CachedGenreSongEntity(
    @PrimaryKey val id: String,
    val genre: String,
    val title: String,
    val artist: String? = null,
    @ColumnInfo(name = "album_id") val albumId: String? = null,
    @ColumnInfo(name = "artist_id") val artistId: String? = null,
    val duration: Int? = null,
    @ColumnInfo(name = "track_number") val trackNumber: Int? = null,
    @ColumnInfo(name = "cover_art") val coverArt: String? = null,
    val suffix: String? = null,
    @ColumnInfo(name = "content_type") val contentType: String? = null,
)

// ── v26: Daily Mix ───────────────────────────────────────────────────────

/** Once-daily generated tracklist for a custom mix recipe — stable for the
 *  full day unless refreshed. Keyed by (date, mix_id); the recipe itself lives
 *  in `custom_mixes` (v47). */
@Entity(
    tableName = "daily_mix",
    foreignKeys = [
        ForeignKey(
            entity = CustomMixEntity::class,
            parentColumns = ["id"],
            childColumns = ["mix_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["date", "mix_id"], unique = true, name = "idx_daily_mix_date_mix_id")],
)
data class DailyMixEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    @ColumnInfo(name = "mix_id") val mixId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "daily_mix_tracks",
    foreignKeys = [
        ForeignKey(
            entity = DailyMixEntity::class,
            parentColumns = ["id"],
            childColumns = ["mix_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mix_id"], name = "idx_daily_mix_tracks_mix_id")],
)
data class DailyMixTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "mix_id") val mixId: Long,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "position") val position: Int,
)

// ── v47: Custom Daily Mixes ──────────────────────────────────────────────

/** A user-managed Daily Mix recipe (v47; composite filters v48).
 *  Every non-empty dimension narrows the pool; dimensions are ANDed while
 *  picks within one dimension are ORed. `source_kind` is a derived display
 *  value (genres/decades/favoriteArtists/mixed); `include_favorite_artists`
 *  keeps the legacy dynamic "all liked artists" behavior. Names/ids are
 *  newline-joined. */
@Entity(tableName = "custom_mixes")
data class CustomMixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "genres_json") val genresJson: String = "",
    @ColumnInfo(name = "decades_json") val decadesJson: String = "",
    @ColumnInfo(name = "artists_json", defaultValue = "") val artistsJson: String = "",
    @ColumnInfo(name = "include_favorite_artists", defaultValue = "0") val includeFavoriteArtists: Boolean = false,
    @ColumnInfo(name = "auto_cache") val autoCache: Boolean = false,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** Auto-cache ownership: which tracks a mix asked to keep cached. A track may
 *  be owned by several mixes; eviction only removes tracks no other auto-cache
 *  mix owns and that are not pinned downloads. */
@Entity(
    tableName = "custom_mix_cache_tracks",
    primaryKeys = ["custom_mix_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = CustomMixEntity::class,
            parentColumns = ["id"],
            childColumns = ["custom_mix_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["track_id"])],
)
data class CustomMixCacheTrackEntity(
    @ColumnInfo(name = "custom_mix_id") val customMixId: Long,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)

/** Single-row seed marker: non-null `seeded_at` means the first-run top-20
 *  genre mixes were materialized. Deleting every mix must NOT re-seed. */
@Entity(tableName = "custom_mix_state")
data class CustomMixStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "seeded_at") val seededAt: Long? = null,
)

/** Return type for getTrackPlayCounts — lightweight id + play_count projection. */
data class PlayCountInfo(val id: String, @ColumnInfo(name = "play_count") val playCount: Int)

data class StarredIdProjection(val id: String, @ColumnInfo(name = "user_rating") val userRating: Int)

/** Cover-art projection for Daily Mix cards (first N unique covers). */
data class CoverArtProjection(@ColumnInfo(name = "cover_art_url") val coverArtUrl: String?)

/** Projection: track count per `daily_mix` row (Home card batch read). */
data class DailyMixTrackCount(
    @ColumnInfo(name = "mix_id") val mixId: Int,
    @ColumnInfo(name = "track_count") val trackCount: Int,
)

// ── v24: Queue Journal ─────────────────────────────────────────────────────────

@Entity(
    tableName = "queue_journal",
    indices = [Index(value = ["source_type", "source_id"], unique = true, name = "idx_queue_journal_source")],
)
data class QueueJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_name") val sourceName: String?,
    @ColumnInfo(name = "track_ids_json") val trackIdsJson: String,
    val position: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

// --- v36: Track Waveforms ---

@Entity(tableName = "track_waveforms")
data class TrackWaveformEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "bars_json") val barsJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
)
