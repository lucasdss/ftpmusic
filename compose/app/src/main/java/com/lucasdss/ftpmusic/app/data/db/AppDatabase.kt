package com.lucasdss.ftpmusic.app.data.db

import androidx.room.Database
import androidx.room.Index
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lucasdss.ftpmusic.app.playback.PersistedPlaybackState

@Database(
    entities = [
        ServerEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        GenreEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        PendingPlaylistChangeEntity::class,
        CachedAlbumEntity::class,
        CachedArtistEntity::class,
        CachedAlbumTrackEntity::class,
        CacheQueueItemEntity::class,
        SessionStateEntity::class,
        QueueItemEntity::class,
        QueueStateEntity::class,
        PersistedPlaybackState::class,
        LyricsCacheEntity::class,
        QueueJournalEntity::class,
        CachedGenreEntity::class,
        CachedGenreSongEntity::class,
        DailyMixEntity::class,
        DailyMixTrackEntity::class,
        CustomMixEntity::class,
        CustomMixCacheTrackEntity::class,
        CustomMixStateEntity::class,
        TrackWaveformEntity::class,
        RadioFavoriteEntity::class,
    ],
    version = 50,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun genreDao(): GenreDao

    // abstract fun sessionStateDao(): SessionStateDao
    abstract fun cacheQueueDao(): CacheQueueDao
    abstract fun queueDao(): QueueDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun pendingPlaylistChangeDao(): PendingPlaylistChangeDao
    abstract fun cachedMetadataDao(): CachedMetadataDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun queueJournalDao(): QueueJournalDao
    abstract fun genreMixDao(): GenreMixDao
    abstract fun customMixDao(): CustomMixDao
    abstract fun trackWaveformDao(): TrackWaveformDao
    abstract fun radioFavoriteDao(): RadioFavoriteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_scrobbles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `track_id` TEXT NOT NULL, `listened_at` INTEGER NOT NULL, `duration_seconds` INTEGER NOT NULL, `flushed` INTEGER NOT NULL DEFAULT 0)",
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT PRIMARY KEY NOT NULL, `server_id` TEXT NOT NULL, `name` TEXT NOT NULL, `comment` TEXT, `owner` TEXT, `is_public` INTEGER NOT NULL, `track_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `is_conflicted` INTEGER NOT NULL DEFAULT 0, `conflict_message` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `playlist_id` TEXT NOT NULL, `track_id` TEXT NOT NULL, `position` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_playlist_changes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `playlist_id` TEXT NOT NULL, `change_type` TEXT NOT NULL, `payload` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `flushed` INTEGER NOT NULL DEFAULT 0)",
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `last_played_at` INTEGER")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `play_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `cached_at` INTEGER")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cache_queue_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `track_id` TEXT NOT NULL, `remote_url` TEXT NOT NULL, `priority` INTEGER NOT NULL DEFAULT 2, `status` TEXT NOT NULL DEFAULT 'pending', `created_at` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cache_queue_items_track_id` ON `cache_queue_items` (`track_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cache_queue_items_status` ON `cache_queue_items` (`status`)",
                )
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_state` (`id` INTEGER PRIMARY KEY NOT NULL, `queue_json` TEXT NOT NULL, `current_index` INTEGER NOT NULL DEFAULT 0, `position_ms` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL)",
                )
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // positionMs Kotlin type changed Int→Long; SQLite INTEGER unchanged
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `queue_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `track_id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT, `album` TEXT, `cover_art_id` TEXT, `url` TEXT NOT NULL, `position` INTEGER NOT NULL, `duration_seconds` INTEGER, `added_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_items_position` ON `queue_items` (`position`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop and recreate to fix schema validation (AUTOINCREMENT nullability)
                db.execSQL("DROP TABLE IF EXISTS `queue_items`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `queue_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `track_id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT, `album` TEXT, `cover_art_id` TEXT, `url` TEXT NOT NULL, `position` INTEGER NOT NULL, `duration_seconds` INTEGER, `added_at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_items_position` ON `queue_items` (`position`)")
                db.execSQL("ALTER TABLE `cache_queue_items` ADD COLUMN `downloaded_bytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cache_queue_items` ADD COLUMN `total_bytes` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        )
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_items ADD COLUMN artist_id TEXT")
                db.execSQL("ALTER TABLE queue_items ADD COLUMN album_id TEXT")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session_state ADD COLUMN is_casting INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE session_state ADD COLUMN cast_device_name TEXT")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN artist TEXT")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN genre TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS genres (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, song_count INTEGER NOT NULL DEFAULT 0, album_count INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_genres_name ON genres (name)")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cache_queue_items ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverArt TEXT")
            }
        }
        val ALL_MIGRATIONS_10 = ALL_MIGRATIONS + MIGRATION_9_10
        val ALL_MIGRATIONS_11 = ALL_MIGRATIONS_10 + MIGRATION_10_11
        val ALL_MIGRATIONS_12 = ALL_MIGRATIONS_11 + MIGRATION_11_12
        val ALL_MIGRATIONS_13 = ALL_MIGRATIONS_12 + MIGRATION_12_13
        val ALL_MIGRATIONS_14 = ALL_MIGRATIONS_13 + MIGRATION_13_14
        val ALL_MIGRATIONS_15 = ALL_MIGRATIONS_14 + MIGRATION_14_15
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playback_state` (`id` INTEGER PRIMARY KEY NOT NULL, `trackId` TEXT, `title` TEXT, `artist` TEXT, `album` TEXT, `albumId` TEXT, `artistId` TEXT, `coverArtId` TEXT, `durationMs` INTEGER NOT NULL DEFAULT 0, `positionMs` INTEGER NOT NULL DEFAULT 0, `isPlaying` INTEGER NOT NULL DEFAULT 0, `isCasting` INTEGER NOT NULL DEFAULT 0, `castDeviceName` TEXT, `repeatMode` INTEGER NOT NULL DEFAULT 0, `shuffleEnabled` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL)",
                )
            }
        }
        val ALL_MIGRATIONS_16 = ALL_MIGRATIONS_15 + MIGRATION_15_16

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS lyrics_cache (trackId TEXT NOT NULL PRIMARY KEY, artist TEXT, title TEXT, syncedLinesJson TEXT, unstructuredText TEXT, fetchedAt INTEGER NOT NULL)",
                )
            }
        }
        val ALL_MIGRATIONS_17 = ALL_MIGRATIONS_16 + MIGRATION_16_17

        // Migration 17→18: add cacheVersion column to lyrics_cache for schema version tracking
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Old caches default to version 0 — they'll be invalidated on next read
                database.execSQL("ALTER TABLE lyrics_cache ADD COLUMN cacheVersion INTEGER NOT NULL DEFAULT 0")
            }
        }
        val ALL_MIGRATIONS_18 = ALL_MIGRATIONS_17 + MIGRATION_17_18

        // Migration 18→19: add rawJson column for raw API response preservation
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE lyrics_cache ADD COLUMN rawJson TEXT")
            }
        }
        val ALL_MIGRATIONS_19 = ALL_MIGRATIONS_18 + MIGRATION_18_19

        // Migration 19→20: add index on playlist_entries.playlist_id for query performance
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // IF NOT EXISTS makes this idempotent — works for both upgrade and fresh install
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_playlist_entries_playlist_id ON playlist_entries (playlist_id)",
                )
            }
        }
        val ALL_MIGRATIONS_20 = ALL_MIGRATIONS_19 + MIGRATION_19_20

        // Migration 20→21: add cached_albums and cached_artists tables for offline metadata
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_albums (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        artist TEXT,
                        artist_id TEXT,
                        year INTEGER,
                        cover_art TEXT,
                        song_count INTEGER,
                        duration INTEGER,
                        genre TEXT,
                        cached_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_artists (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        cover_art TEXT,
                        album_count INTEGER,
                        cached_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
            }
        }
        val ALL_MIGRATIONS_21 = ALL_MIGRATIONS_20 + MIGRATION_20_21

        // Migration 21→22: add index on pending_playlist_changes for flush queries
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_pending_playlist_changes_flushed_created_at ON pending_playlist_changes (flushed, created_at)",
                )
            }
        }
        val ALL_MIGRATIONS_22 = ALL_MIGRATIONS_21 + MIGRATION_21_22

        // Migration 22→23: add cached_album_tracks for offline album detail
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_album_tracks (
                        id TEXT NOT NULL PRIMARY KEY,
                        album_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT,
                        duration INTEGER,
                        track_number INTEGER,
                        cover_art TEXT,
                        suffix TEXT,
                        content_type TEXT
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_cached_album_tracks_album_id ON cached_album_tracks (album_id)",
                )
            }
        }
        val ALL_MIGRATIONS_23 = ALL_MIGRATIONS_22 + MIGRATION_22_23

        // Migration 23→24: add queue_journal for source-based playback history
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS queue_journal (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        source_type TEXT NOT NULL,
                        source_id TEXT NOT NULL,
                        source_name TEXT,
                        track_ids_json TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_queue_journal_source ON queue_journal (source_type, source_id)",
                )
            }
        }
        val ALL_MIGRATIONS_24 = ALL_MIGRATIONS_23 + MIGRATION_23_24

        // Migration 24→25: add cached_genres and cached_genre_songs for Genre Mixes
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS cached_genres (name TEXT PRIMARY KEY NOT NULL, song_count INTEGER NOT NULL DEFAULT 0, album_count INTEGER NOT NULL DEFAULT 0, cached_at INTEGER NOT NULL)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS cached_genre_songs (id TEXT PRIMARY KEY NOT NULL, genre TEXT NOT NULL, title TEXT NOT NULL, artist TEXT, album_id TEXT, artist_id TEXT, duration INTEGER, track_number INTEGER, cover_art TEXT, suffix TEXT, content_type TEXT)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_genre_songs_genre ON cached_genre_songs (genre)",
                )
            }
        }
        val ALL_MIGRATIONS_25 = ALL_MIGRATIONS_24 + MIGRATION_24_25

        // Migration 25→26: add daily_mix table for once-daily genre mix persistence
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS daily_mix (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, genre TEXT NOT NULL, track_ids_json TEXT NOT NULL, created_at INTEGER NOT NULL)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_mix_date_genre ON daily_mix (date, genre)",
                )
            }
        }
        val ALL_MIGRATIONS_26 = ALL_MIGRATIONS_25 + MIGRATION_25_26

        // Migration 26→27: add user_rating column to tracks
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN user_rating INTEGER")
            }
        }
        val ALL_MIGRATIONS_27 = ALL_MIGRATIONS_26 + MIGRATION_26_27

        // Migration 27→28: add genre, year, artist_id, disc_number, bitrate, size to cached_album_tracks
        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_album_tracks ADD COLUMN genre TEXT")
                database.execSQL("ALTER TABLE cached_album_tracks ADD COLUMN year INTEGER")
                database.execSQL("ALTER TABLE cached_album_tracks ADD COLUMN artist_id TEXT")
                database.execSQL("ALTER TABLE cached_album_tracks ADD COLUMN disc_number INTEGER")
                database.execSQL("ALTER TABLE cached_album_tracks ADD COLUMN bitrate INTEGER")
                database.execSQL("ALTER TABLE cached_album_tracks ADD COLUMN size INTEGER")
            }
        }
        val ALL_MIGRATIONS_28 = ALL_MIGRATIONS_27 + MIGRATION_27_28

        // Migration 28→29: daily_mix_tracks join table, drop track_ids_json from daily_mix
        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create join table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_mix_tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mix_id INTEGER NOT NULL,
                        track_id TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        FOREIGN KEY (mix_id) REFERENCES daily_mix(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_daily_mix_tracks_mix_id ON daily_mix_tracks (mix_id)")

                // Migrate existing JSON data into join table (idempotent — skips if already migrated)
                val cursor = database.query("SELECT id, track_ids_json FROM daily_mix")
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val mixId = c.getLong(0)
                        val json = c.getString(1)
                        if (json != null && json.startsWith("[")) {
                            // Check if tracks for this mix already migrated
                            val existing = database.query(
                                "SELECT COUNT(*) FROM daily_mix_tracks WHERE mix_id = ?",
                                arrayOf(mixId),
                            )
                            val alreadyMigrated = existing.use { it.moveToFirst() && it.getInt(0) > 0 }
                            if (!alreadyMigrated) {
                                try {
                                    val cleaned = json.trim().removeSurrounding("[", "]")
                                    val ids = cleaned.split(",").map {
                                        it.trim().removeSurrounding("\"")
                                    }.filter { it.isNotEmpty() }
                                    ids.forEachIndexed { index, trackId ->
                                        database.execSQL(
                                            "INSERT INTO daily_mix_tracks (mix_id, track_id, position) VALUES (?, ?, ?)",
                                            arrayOf(mixId, trackId, index),
                                        )
                                    }
                                } catch (_: Exception) { /* skip corrupted JSON */ }
                            }
                        }
                    }
                }
                // Drop track_ids_json from daily_mix (safe across all API levels via table recreation)
                database.execSQL(
                    """
                    CREATE TABLE daily_mix_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        genre TEXT NOT NULL,
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "INSERT INTO daily_mix_new (id, date, genre, created_at) SELECT id, date, genre, created_at FROM daily_mix",
                )
                database.execSQL("DROP TABLE daily_mix")
                database.execSQL("ALTER TABLE daily_mix_new RENAME TO daily_mix")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_mix_date_genre ON daily_mix (date, genre)",
                )
            }
        }
        val ALL_MIGRATIONS_29 = ALL_MIGRATIONS_28 + MIGRATION_28_29

        // Migration 29→30: queue_state table for playback state (replaces SharedPreferences)
        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS queue_state (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        current_index INTEGER NOT NULL DEFAULT 0,
                        position_ms INTEGER NOT NULL DEFAULT 0,
                        is_casting INTEGER NOT NULL DEFAULT 0,
                        cast_device_name TEXT,
                        updated_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
            }
        }
        val ALL_MIGRATIONS_30 = ALL_MIGRATIONS_29 + MIGRATION_29_30
        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN user_rating INTEGER DEFAULT NULL")
            }
        }
        val ALL_MIGRATIONS_31 = ALL_MIGRATIONS_30 + MIGRATION_30_31

        // Migration 31→32: explicit is_download flag on cache queue items so a
        // download request can't silently degrade to auto-cache
        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cache_queue_items ADD COLUMN is_download INTEGER NOT NULL DEFAULT 0")
                // Backfill: priority 1 has always meant an explicit download request
                database.execSQL("UPDATE cache_queue_items SET is_download = 1 WHERE priority = 1")
            }
        }
        val ALL_MIGRATIONS_32 = ALL_MIGRATIONS_31 + MIGRATION_31_32

        // Migration 32→33: drop dead pending_scrobbles table
        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS pending_scrobbles")
            }
        }
        val ALL_MIGRATIONS_33 = ALL_MIGRATIONS_32 + MIGRATION_32_33

        // Migration 33→34: add last_synced_at to playlists for per-playlist sync badge
        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE playlists ADD COLUMN last_synced_at INTEGER DEFAULT NULL")
            }
        }
        val ALL_MIGRATIONS_34 = ALL_MIGRATIONS_33 + MIGRATION_33_34

        // Migration 34→35: add sleep_timer_end_ms to playback_state for sleep timer persistence
        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE playback_state ADD COLUMN sleepTimerEndMs INTEGER NOT NULL DEFAULT 0")
            }
        }
        val ALL_MIGRATIONS_35 = ALL_MIGRATIONS_34 + MIGRATION_34_35

        // Migration 35→36: add track_waveforms table for waveform scrubber
        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS track_waveforms (
                        track_id TEXT PRIMARY KEY NOT NULL,
                        bars_json TEXT NOT NULL,
                        cached_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
        val ALL_MIGRATIONS_36 = ALL_MIGRATIONS_35 + MIGRATION_35_36

        // Migration 36→37: add artist_id index on tracks for getOfflineArtists() JOIN
        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tracks_artist_id ON tracks (artist_id)")
            }
        }
        val ALL_MIGRATIONS_37 = ALL_MIGRATIONS_36 + MIGRATION_36_37

        // Migration 37→38: MusicBrainz public ratings + MBID cache
        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN public_rating REAL DEFAULT NULL")
                database.execSQL("ALTER TABLE tracks ADD COLUMN public_rating_votes INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE tracks ADD COLUMN musicbrainz_id TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE cached_albums ADD COLUMN public_rating REAL DEFAULT NULL")
                database.execSQL("ALTER TABLE cached_albums ADD COLUMN public_rating_votes INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE cached_albums ADD COLUMN musicbrainz_id TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE cached_artists ADD COLUMN public_rating REAL DEFAULT NULL")
                database.execSQL("ALTER TABLE cached_artists ADD COLUMN public_rating_votes INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE cached_artists ADD COLUMN musicbrainz_id TEXT DEFAULT NULL")
            }
        }
        val ALL_MIGRATIONS_38 = ALL_MIGRATIONS_37 + MIGRATION_37_38

        // Migration 38→39: similar artists (last.fm) as JSON on cached_artists
        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_artists ADD COLUMN similar_artists_json TEXT DEFAULT NULL")
            }
        }
        val ALL_MIGRATIONS_39 = ALL_MIGRATIONS_38 + MIGRATION_38_39

        // Migration 39→40: persist the dual-queue context/priority split so a
        // Cast disconnect/reconnect restores the structure instead of absorbing
        // priority tracks into the context section. -1 = legacy (all context).
        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE queue_state ADD COLUMN context_size INTEGER NOT NULL DEFAULT -1")
            }
        }
        val ALL_MIGRATIONS_40 = ALL_MIGRATIONS_39 + MIGRATION_39_40

        // Migration 40→41: local dislike flag (thumbs down). Like == starred_at
        // (already persisted); dislike is local-only and mutually exclusive.
        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN is_disliked INTEGER NOT NULL DEFAULT 0")
            }
        }
        val ALL_MIGRATIONS_41 = ALL_MIGRATIONS_40 + MIGRATION_40_41

        // Migration 41→42: single-row Daily Mix genre selection settings.
        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_mix_settings` (`id` INTEGER NOT NULL, `genre_names_json` TEXT NOT NULL, `is_custom` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }
        val ALL_MIGRATIONS_42 = ALL_MIGRATIONS_41 + MIGRATION_41_42

        // Migration 42→43: entity-level favorites.
        // - radio_favorites: local-only bookmarked internet radio stations.
        // - is_disliked on albums/artists: local thumbs-down, mutually exclusive
        //   with like (= starred_at), mirroring the track-level v41 semantics.
        // - albums.artist: display name carried by the favorites ledger.
        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS radio_favorites (
                        station_id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        stream_url TEXT NOT NULL,
                        home_page_url TEXT,
                        bookmarked_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("ALTER TABLE albums ADD COLUMN artist TEXT")
                database.execSQL("ALTER TABLE albums ADD COLUMN is_disliked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE artists ADD COLUMN is_disliked INTEGER NOT NULL DEFAULT 0")
            }
        }
        val ALL_MIGRATIONS_43 = ALL_MIGRATIONS_42 + MIGRATION_42_43

        // Migration 43→44: local-first intent markers (pending_unstar_at).
        // Set when the user unstars/dislikes while offline; the star mirror
        // skips re-starring these rows and pushes the unstar to the server.
        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN pending_unstar_at INTEGER")
                database.execSQL("ALTER TABLE albums ADD COLUMN pending_unstar_at INTEGER")
                database.execSQL("ALTER TABLE artists ADD COLUMN pending_unstar_at INTEGER")
            }
        }
        val ALL_MIGRATIONS_44 = ALL_MIGRATIONS_43 + MIGRATION_43_44

        // Migration 44→45 (local-first hardening):
        // 1. UNIQUE index on cache_queue_items.track_id — makes enqueue atomic.
        //    Pre-existing duplicates (the bug this fixes) are deduped first.
        // 2. Hot-path indexes for Favorites / Recently Played / genre mixes /
        //    ratings and cached-metadata joins.
        //
        // NOTE: the CREATE UNIQUE INDEX is a silent NO-OP on real devices — the
        // v44 schema already had a non-unique index with the same auto-generated
        // name (index_cache_queue_items_track_id from Index(value=["track_id"])),
        // so IF NOT EXISTS skipped it. MIGRATION_45_46 repairs the index.
        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "DELETE FROM cache_queue_items WHERE id NOT IN " +
                        "(SELECT MAX(id) FROM cache_queue_items GROUP BY track_id)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cache_queue_items_track_id` " +
                        "ON `cache_queue_items` (`track_id`)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_starred_at` ON `tracks` (`starred_at`)")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tracks_last_played_at` ON `tracks` (`last_played_at`)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_genre` ON `tracks` (`genre`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_user_rating` ON `tracks` (`user_rating`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_is_disliked` ON `tracks` (`is_disliked`)")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_albums_artist_id` ON `cached_albums` (`artist_id`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_albums_artist` ON `cached_albums` (`artist`)",
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_albums_name` ON `cached_albums` (`name`)")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_album_tracks_artist_id` ON `cached_album_tracks` (`artist_id`)",
                )
            }
        }
        val ALL_MIGRATIONS_45 = ALL_MIGRATIONS_44 + MIGRATION_44_45

        // Migration 45→46 (hotfix): 44→45's CREATE UNIQUE INDEX IF NOT EXISTS was
        // a silent no-op for existing databases — the v44 schema already had a
        // NON-unique index under the same auto-generated name, so devices that
        // migrated to 45 kept the non-unique index and Room's schema validation
        // crashed on every open ("Migration didn't properly handle:
        // cache_queue_items"). Drop the stale non-unique index and recreate it
        // as UNIQUE (dedupe first for safety on any device that skipped 44→45).
        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "DELETE FROM cache_queue_items WHERE id NOT IN " +
                        "(SELECT MAX(id) FROM cache_queue_items GROUP BY track_id)",
                )
                database.execSQL("DROP INDEX IF EXISTS `index_cache_queue_items_track_id`")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_cache_queue_items_track_id` " +
                        "ON `cache_queue_items` (`track_id`)",
                )
            }
        }
        val ALL_MIGRATIONS_46 = ALL_MIGRATIONS_45 + MIGRATION_45_46

        // Migration 46→47: Custom Daily Mixes.
        // - `custom_mixes` recipe table + `custom_mix_cache_tracks` ownership +
        //   `custom_mix_state` seed marker.
        // - `daily_mix` rekeyed from genre to mix_id (unique per date+mix).
        //   Existing generated rows are transient (today/yesterday) and are
        //   dropped; Home lazy generation rebuilds them on next open.
        // - Legacy `daily_mix_settings` selection is mapped to one mix per
        //   selected genre (is_default = !isCustom) so user picks survive.
        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_mixes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`source_kind` TEXT NOT NULL, " +
                        "`genres_json` TEXT NOT NULL, " +
                        "`decades_json` TEXT NOT NULL, " +
                        "`auto_cache` INTEGER NOT NULL, " +
                        "`is_default` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_mix_cache_tracks` (" +
                        "`custom_mix_id` INTEGER NOT NULL, " +
                        "`track_id` TEXT NOT NULL, " +
                        "`cached_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`custom_mix_id`, `track_id`), " +
                        "FOREIGN KEY(`custom_mix_id`) REFERENCES `custom_mixes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_mix_cache_tracks_track_id` " +
                        "ON `custom_mix_cache_tracks` (`track_id`)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_mix_state` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`seeded_at` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )

                // Map the legacy selection to mixes before dropping the table.
                database.query(
                    "SELECT genre_names_json, is_custom FROM daily_mix_settings WHERE id = 1",
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val names = cursor.getString(0)
                            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(20)
                        val isCustom = cursor.getInt(1) == 1
                        val now = System.currentTimeMillis()
                        names.forEach { name ->
                            database.execSQL(
                                "INSERT INTO custom_mixes " +
                                    "(name, source_kind, genres_json, decades_json, " +
                                    "auto_cache, is_default, created_at) " +
                                    "VALUES (?, 'genres', ?, '', 0, ?, ?)",
                                arrayOf("$name Mix", name, if (isCustom) 0 else 1, now),
                            )
                        }
                        if (names.isNotEmpty()) {
                            database.execSQL(
                                "INSERT OR REPLACE INTO custom_mix_state (id, seeded_at) VALUES (1, ?)",
                                arrayOf(now),
                            )
                        }
                    }
                }

                // Rebuild daily_mix keyed by mix_id (old generated rows dropped).
                database.execSQL("DROP TABLE IF EXISTS `daily_mix_tracks`")
                database.execSQL("DROP TABLE IF EXISTS `daily_mix`")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_mix` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`mix_id` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`mix_id`) REFERENCES `custom_mixes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `idx_daily_mix_date_mix_id` " +
                        "ON `daily_mix` (`date`, `mix_id`)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_mix_tracks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mix_id` INTEGER NOT NULL, " +
                        "`track_id` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`mix_id`) REFERENCES `daily_mix`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_daily_mix_tracks_mix_id` ON `daily_mix_tracks` (`mix_id`)",
                )
                database.execSQL("DROP TABLE IF EXISTS `daily_mix_settings`")
            }
        }
        val ALL_MIGRATIONS_47 = ALL_MIGRATIONS_46 + MIGRATION_46_47

        // Migration 47→48: composite Custom Daily Mix filters + searchable
        // artists. Adds an explicit artist-id list and a dynamic "all favorite
        // artists" flag. Legacy favoriteArtists mixes keep their dynamic
        // behavior via the backfill.
        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE custom_mixes ADD COLUMN artists_json TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    "ALTER TABLE custom_mixes ADD COLUMN include_favorite_artists INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "UPDATE custom_mixes SET include_favorite_artists = 1 WHERE source_kind = 'favoriteArtists'",
                )
            }
        }
        val ALL_MIGRATIONS_48 = ALL_MIGRATIONS_47 + MIGRATION_47_48

        // Migration 48→49: per-row queue origin (industry dual-queue).
        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE queue_items ADD COLUMN is_priority INTEGER NOT NULL DEFAULT 0",
                )
                // Legacy leading-context layout: mark trailing rows as priority when
                // context_size is a valid split. -1 / missing → leave all context.
                database.execSQL(
                    """
                    UPDATE queue_items
                    SET is_priority = 1
                    WHERE EXISTS (
                        SELECT 1 FROM queue_state
                        WHERE id = 1 AND context_size >= 0
                    )
                    AND position >= (
                        SELECT context_size FROM queue_state WHERE id = 1
                    )
                    """.trimIndent(),
                )
            }
        }
        val ALL_MIGRATIONS_49 = ALL_MIGRATIONS_48 + MIGRATION_48_49

        // Migration 49→50: monotonic queue entry id (Cast itemId / Compose key).
        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE queue_items ADD COLUMN entry_id INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "UPDATE queue_items SET entry_id = position + 1 WHERE entry_id = 0",
                )
                database.execSQL(
                    "ALTER TABLE queue_state ADD COLUMN next_entry_id INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    """
                    UPDATE queue_state
                    SET next_entry_id = COALESCE(
                        (SELECT MAX(entry_id) + 1 FROM queue_items),
                        1
                    )
                    WHERE id = 1
                    """.trimIndent(),
                )
            }
        }
        val ALL_MIGRATIONS_50 = ALL_MIGRATIONS_49 + MIGRATION_49_50
    }
}
