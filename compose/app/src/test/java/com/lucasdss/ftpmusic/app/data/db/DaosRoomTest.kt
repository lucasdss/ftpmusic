package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real-Room integration tests (in-memory SQLite via Robolectric) for the
 * local-first critical DAO paths: cache-queue atomicity/ordering, queue
 * save transactions, and the 44→45 migration (dedupe + unique index).
 *
 * These are the ONLY tests that exercise actual SQL — every other DAO test
 * mocks the interface, so schema/query bugs previously surfaced only on
 * device. Covers the F8 (duplicate enqueue) and F22 (queue save atomicity)
 * fixes at the SQL layer.
 */
@RunWith(RobolectricTestRunner::class)
class DaosRoomTest {

    private lateinit var db: AppDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ── CacheQueueDao: F8 atomic enqueue ───────────────────────────────────

    @Test
    fun `insertIgnore dedupes on the unique track_id index`() = runBlocking {
        val dao = db.cacheQueueDao()
        val first = dao.insertIgnore(
            CacheQueueItemEntity(trackId = "t1", remoteUrl = "http://a", priority = 2),
        )
        // Second concurrent-style enqueue for the same track is ignored
        val second = dao.insertIgnore(
            CacheQueueItemEntity(trackId = "t1", remoteUrl = "http://b", priority = 0),
        )
        assertEquals(1L, first)
        assertEquals(-1L, second)
        val rows = dao.getAllFlow().first()
        assertEquals(1, rows.size)
        // The FIRST row wins — the upgrade path must apply priority on it
        assertEquals(2, rows[0].priority)
    }

    @Test
    fun `getNextPendingByPriority returns the highest-priority pending row`() = runBlocking {
        val dao = db.cacheQueueDao()
        dao.insertIgnore(CacheQueueItemEntity(trackId = "p2", remoteUrl = "http://2", priority = 2))
        dao.insertIgnore(CacheQueueItemEntity(trackId = "p0", remoteUrl = "http://0", priority = 0))
        dao.insertIgnore(CacheQueueItemEntity(trackId = "p1", remoteUrl = "http://1", priority = 1))

        // Priority 0 (play-queue urgent window) is always picked first
        assertEquals("p0", dao.getNextPendingByPriority(0)!!.trackId)
        assertEquals("p1", dao.getNextPendingByPriority(1)!!.trackId)
        assertEquals("p2", dao.getNextPendingByPriority(2)!!.trackId)
    }

    @Test
    fun `processing and completed rows are excluded from the worker poll`() = runBlocking {
        val dao = db.cacheQueueDao()
        val id = dao.insertIgnore(
            CacheQueueItemEntity(trackId = "busy", remoteUrl = "http://x", priority = 2),
        ).toInt()
        dao.insertIgnore(CacheQueueItemEntity(trackId = "done", remoteUrl = "http://y", priority = 2))
        dao.updateStatus(id, "processing")

        // Only one pending row remains pickable
        val picked = dao.getNextPendingByPriority(2)
        assertEquals("done", picked!!.trackId)
    }

    // ── QueueDao: F22 atomic save ──────────────────────────────────────────

    @Test
    fun `replaceAllAndState atomically replaces items and state`() = runBlocking {
        val dao = db.queueDao()
        dao.replaceAllAndState(
            listOf(
                QueueItemEntity(trackId = "a", title = "A", url = "http://a", position = 0),
                QueueItemEntity(trackId = "b", title = "B", url = "http://b", position = 1),
            ),
            QueueStateEntity(currentIndex = 1, positionMs = 30_000L, contextSize = 1),
        )

        val items = dao.getAllOnce()
        assertEquals(2, items.size)
        assertEquals("a", items[0].trackId)
        val state = dao.getState()!!
        assertEquals(1, state.currentIndex)
        assertEquals(30_000L, state.positionMs)
        assertEquals(1, state.contextSize)

        // Second save replaces BOTH tables — no residue from the first
        dao.replaceAllAndState(
            listOf(QueueItemEntity(trackId = "c", title = "C", url = "http://c", position = 0)),
            QueueStateEntity(currentIndex = 0, positionMs = 5_000L, contextSize = -1),
        )
        assertEquals(1, dao.getAllOnce().size)
        assertEquals("c", dao.getAllOnce()[0].trackId)
        assertEquals(5_000L, dao.getState()!!.positionMs)
    }

    @Test
    fun `savePositionOnly preserves contextSize and index semantics`() = runBlocking {
        val dao = db.queueDao()
        dao.saveState(QueueStateEntity(currentIndex = 1, positionMs = 9_000L, contextSize = 3))

        dao.savePositionOnly(2, 12_000L)

        val state = dao.getState()!!
        assertEquals(2, state.currentIndex)
        assertEquals(12_000L, state.positionMs)
        assertEquals(3, state.contextSize)
    }

    // ── TrackDao ───────────────────────────────────────────────────────────

    @Test
    fun `watchTracksByIds observes cache status changes for many tracks`() = runBlocking {
        val dao = db.trackDao()
        dao.upsert(TrackEntity(id = "t1", title = "One"))
        dao.upsert(TrackEntity(id = "t2", title = "Two"))

        val flow = dao.watchTracksByIds(listOf("t1", "t2"))
        val initial = flow.first()
        assertEquals(setOf("t1", "t2"), initial.map { it.id }.toSet())

        // A tracks-table write invalidates the flow — one query, not one per track
        dao.upsert(
            TrackEntity(id = "t1", title = "One", cachedFilePath = "/x", cacheSizeBytes = 10, isAutoCached = true),
        )
        val updated = flow.first { it.any { row -> row.id == "t1" && row.cachedFilePath != null } }
        assertTrue(updated.first { it.id == "t1" }.isAutoCached)
        assertNull(updated.first { it.id == "t2" }.cachedFilePath)
    }

    // ── Migration 44→45 ────────────────────────────────────────────────────

    @Test
    fun `migration 44 to 45 dedupes rows and 45 to 46 repairs the unique index`() {
        // Unique per run: Robolectric app data persists across gradle invocations.
        val testDb = "migration-44-46-${System.nanoTime()}.db"
        context.deleteDatabase(testDb)
        // Real framework SQLite at version 44 (raw SupportSQLiteDatabase —
        // MigrationTestHelper would require exported Room schemas).
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(44) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE cache_queue_items (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                track_id TEXT NOT NULL,
                                remote_url TEXT NOT NULL,
                                priority INTEGER NOT NULL,
                                status TEXT NOT NULL,
                                is_download INTEGER NOT NULL,
                                retry_count INTEGER NOT NULL DEFAULT 0,
                                downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                                total_bytes INTEGER NOT NULL DEFAULT 0,
                                created_at INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        // REAL v44 schema: Room auto-generated a NON-unique
                        // index on track_id named index_cache_queue_items_track_id.
                        // 44→45's CREATE UNIQUE INDEX IF NOT EXISTS is a no-op
                        // against it (the bug that crashed devices at v45).
                        db.execSQL(
                            "CREATE INDEX `index_cache_queue_items_track_id` " +
                                "ON `cache_queue_items` (`track_id`)",
                        )
                        // Minimal tables the migration indexes (column names
                        // matter — SQLite validates them at CREATE INDEX time)
                        db.execSQL(
                            "CREATE TABLE tracks (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, starred_at INTEGER, last_played_at INTEGER, genre TEXT, user_rating INTEGER, is_disliked INTEGER NOT NULL DEFAULT 0)",
                        )
                        db.execSQL(
                            "CREATE TABLE cached_albums (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, artist TEXT, artist_id TEXT)",
                        )
                        db.execSQL(
                            "CREATE TABLE cached_album_tracks (id TEXT PRIMARY KEY NOT NULL, album_id TEXT NOT NULL, title TEXT NOT NULL, artist_id TEXT)",
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build(),
        )
        val db = helper.writableDatabase
        // Duplicates exactly like the F8 bug produced (two enqueues, same track)
        db.execSQL(
            "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('dup1', 'http://a', 2, 'pending', 0, 1000)",
        )
        db.execSQL(
            "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('dup1', 'http://a', 0, 'pending', 0, 2000)",
        )
        db.execSQL(
            "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('solo', 'http://b', 1, 'pending', 0, 3000)",
        )

        AppDatabase.MIGRATION_44_45.migrate(db)

        // Dedup: only the MAX(id) row per track survives (the newest)
        val rows = mutableListOf<String>()
        db.query("SELECT track_id FROM cache_queue_items ORDER BY track_id").use { cursor ->
            while (cursor.moveToNext()) rows.add(cursor.getString(0))
        }
        assertEquals(listOf("dup1", "solo"), rows)

        // 44→45 ALONE leaves the index NON-unique (known no-op against the
        // pre-existing index) — a duplicate insert is still accepted.
        db.execSQL(
            "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('dup1', 'http://e', 0, 'pending', 0, 6000)",
        )
        var rejected = false
        try {
            db.execSQL(
                "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('dup1', 'http://f', 0, 'pending', 0, 7000)",
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertFalse("44->45 must NOT enforce uniqueness on its own (known no-op)", rejected)

        // 45→46 drops the stale non-unique index and recreates it as UNIQUE —
        // the repair that fixes the device crash.
        AppDatabase.MIGRATION_45_46.migrate(db)

        val dupCount = db.query("SELECT COUNT(*) FROM cache_queue_items WHERE track_id = 'dup1'").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        assertEquals("45->46 dedupes before creating the unique index", 1L, dupCount)

        // Unique index enforced from now on: first insert OK, duplicate rejected
        db.execSQL(
            "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('fresh', 'http://c', 2, 'pending', 0, 4000)",
        )
        rejected = false
        try {
            db.execSQL(
                "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('fresh', 'http://d', 2, 'pending', 0, 5000)",
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Second insert for the same track must be rejected after 45->46", rejected)
        helper.close()
        context.deleteDatabase(testDb)
    }

    @Test
    fun `migration 45 to 46 repairs a device already stuck at 45`() {
        // Simulates the crashed phone state: DB at v45 with the non-unique
        // index and a duplicate row (44→45's dedupe ran, but its unique-index
        // creation was a no-op — duplicates could have been re-inserted).
        val testDb = "migration-45-46-${System.nanoTime()}.db"
        context.deleteDatabase(testDb)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(45) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE cache_queue_items (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                track_id TEXT NOT NULL,
                                remote_url TEXT NOT NULL,
                                priority INTEGER NOT NULL,
                                status TEXT NOT NULL,
                                is_download INTEGER NOT NULL,
                                retry_count INTEGER NOT NULL DEFAULT 0,
                                downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                                total_bytes INTEGER NOT NULL DEFAULT 0,
                                created_at INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            "CREATE INDEX `index_cache_queue_items_track_id` " +
                                "ON `cache_queue_items` (`track_id`)",
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build(),
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('x', 'http://x', 2, 'pending', 0, 1000)",
        )
        db.execSQL(
            "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('x', 'http://y', 0, 'pending', 0, 2000)",
        )

        AppDatabase.MIGRATION_45_46.migrate(db)

        val count = db.query("SELECT COUNT(*) FROM cache_queue_items WHERE track_id = 'x'").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        assertEquals(1L, count)
        var rejected = false
        try {
            db.execSQL(
                "INSERT INTO cache_queue_items (track_id, remote_url, priority, status, is_download, created_at) VALUES ('x', 'http://z', 2, 'pending', 0, 3000)",
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Unique index must be enforced after the repair", rejected)
        helper.close()
        context.deleteDatabase(testDb)
    }

    // ── Custom Daily Mix DAOs (v47) ────────────────────────────────────────

    @Test
    fun `seedOnce fills only free slots and is idempotent`() = runBlocking {
        val dao = db.customMixDao()
        dao.insertIfUnderCap(
            CustomMixEntity(name = "User Mix", sourceKind = "genres", genresJson = "Rock"),
            20,
        )
        val seeded = (1..20).map {
            CustomMixEntity(name = "Default $it", sourceKind = "genres", genresJson = "G$it")
        }

        assertTrue(dao.seedOnce(seeded, 20))
        assertEquals("must not overshoot the cap", 20, dao.count())
        assertFalse("second seed must be a no-op", dao.seedOnce(seeded, 20))
        assertEquals(20, dao.count())
    }

    @Test
    fun `insertIfUnderCap rejects the twenty-first mix`() = runBlocking {
        val dao = db.customMixDao()
        repeat(20) { i ->
            dao.insertIfUnderCap(CustomMixEntity(name = "Mix $i", sourceKind = "genres", genresJson = "G"), 20)
        }

        assertEquals(-1L, dao.insertIfUnderCap(CustomMixEntity(name = "Extra", sourceKind = "genres"), 20))
        assertEquals(20, dao.count())
    }

    @Test
    fun `deleting a mix cascades daily rows and cache ownership`() = runBlocking {
        val mixDao = db.customMixDao()
        val mixId = mixDao.insert(CustomMixEntity(name = "Rock Mix", sourceKind = "genres", genresJson = "Rock"))
        val genreMixDao = db.genreMixDao()
        genreMixDao.replaceDailyMix(
            "2026-09-10",
            mixId,
            listOf(
                DailyMixTrackEntity(mixId = 0, trackId = "t1", position = 0),
                DailyMixTrackEntity(mixId = 0, trackId = "t2", position = 1),
            ),
        )
        mixDao.upsertCacheTracks(listOf(CustomMixCacheTrackEntity(customMixId = mixId, trackId = "t1")))
        val daily = genreMixDao.getDailyMix("2026-09-10", mixId)!!
        assertEquals(listOf("t1", "t2"), genreMixDao.getDailyMixTrackIds(daily.id))

        mixDao.deleteById(mixId)

        assertNull(genreMixDao.getDailyMix("2026-09-10", mixId))
        assertEquals(emptyList<String>(), mixDao.getOwnedTrackIds(mixId))
    }

    @Test
    fun `getDailyMixCovers returns distinct covers in position order`() = runBlocking {
        val mixDao = db.customMixDao()
        val mixId = mixDao.insert(CustomMixEntity(name = "Rock Mix", sourceKind = "genres", genresJson = "Rock"))
        val trackDao = db.trackDao()
        trackDao.upsert(TrackEntity(id = "t1", title = "One", coverArtUrl = "ca-1"))
        trackDao.upsert(TrackEntity(id = "t2", title = "Two", coverArtUrl = "ca-2"))
        trackDao.upsert(TrackEntity(id = "t3", title = "Three", coverArtUrl = "ca-1"))
        trackDao.upsert(TrackEntity(id = "t4", title = "Four", coverArtUrl = "ca-4"))
        val genreMixDao = db.genreMixDao()
        genreMixDao.replaceDailyMix(
            "2026-09-10",
            mixId,
            listOf("t1", "t2", "t3", "t4").mapIndexed { i, id ->
                DailyMixTrackEntity(mixId = 0, trackId = id, position = i)
            },
        )
        val daily = genreMixDao.getDailyMix("2026-09-10", mixId)!!

        val covers = genreMixDao.getDailyMixCovers(daily.id).map { it.coverArtUrl }

        assertEquals(listOf("ca-1", "ca-2", "ca-4"), covers)
    }

    @Test
    fun `mix source pools filter disliked tracks and join albums and artists`() = runBlocking {
        val trackDao = db.trackDao()
        trackDao.upsert(TrackEntity(id = "g1", title = "G1", genre = "Rock"))
        trackDao.upsert(TrackEntity(id = "g2", title = "G2", genre = "Rock", isDisliked = true))
        trackDao.upsert(TrackEntity(id = "y1", title = "Y1", albumId = "al1"))
        trackDao.upsert(TrackEntity(id = "a1", title = "A1", artistId = "ar1"))
        trackDao.upsert(TrackEntity(id = "a2", title = "A2", artist = "Name Only"))
        db.cachedMetadataDao().replaceAlbums(
            listOf(CachedAlbumEntity(id = "al1", name = "Album", artist = "X", year = 1985)),
        )
        db.cachedMetadataDao().insertNewAlbumsToLedger()

        assertEquals(listOf("g1"), trackDao.getMixTrackIdsByGenre("Rock"))
        assertEquals(listOf("y1"), trackDao.getMixTrackIdsByYearRange(1980, 1989))
        assertEquals(listOf("a1"), trackDao.getMixTrackIdsByArtistIds(listOf("ar1")))
        assertEquals(listOf("a2"), trackDao.getMixTrackIdsByArtistNames(listOf("Name Only")))
    }

    @Test
    fun `migration 46 to 47 maps settings and rekeys daily mix on real SQLite`() {
        val testDb = "migration-46-47-${System.nanoTime()}.db"
        context.deleteDatabase(testDb)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(46) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE daily_mix (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "date TEXT NOT NULL, genre TEXT NOT NULL, created_at INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE daily_mix_tracks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "mix_id INTEGER NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE daily_mix_settings (id INTEGER NOT NULL, " +
                                "genre_names_json TEXT NOT NULL, is_custom INTEGER NOT NULL, PRIMARY KEY(id))",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build(),
        )
        val raw = helper.writableDatabase
        raw.execSQL("INSERT INTO daily_mix_settings (id, genre_names_json, is_custom) VALUES (1, 'Jazz\nBlues', 1)")
        raw.execSQL("INSERT INTO daily_mix (date, genre, created_at) VALUES ('2026-01-01', 'Jazz', 1)")
        raw.execSQL("INSERT INTO daily_mix_tracks (mix_id, track_id, position) VALUES (1, 't1', 0)")

        AppDatabase.MIGRATION_46_47.migrate(raw)

        val names = mutableListOf<String>()
        raw.query("SELECT name FROM custom_mixes ORDER BY id").use { c ->
            while (c.moveToNext()) names.add(c.getString(0))
        }
        assertEquals(listOf("Jazz Mix", "Blues Mix"), names)

        val seededAtNull = raw.query("SELECT seeded_at FROM custom_mix_state WHERE id = 1").use { c ->
            c.moveToFirst()
            c.isNull(0)
        }
        assertFalse("seed marker must be set by the legacy mapping", seededAtNull)

        val columns = mutableListOf<String>()
        raw.query("PRAGMA table_info(daily_mix)").use { c ->
            while (c.moveToNext()) columns.add(c.getString(1))
        }
        assertTrue(columns.contains("mix_id"))
        assertFalse(columns.contains("genre"))

        var settingsGone = false
        try {
            raw.query("SELECT 1 FROM daily_mix_settings LIMIT 1").close()
        } catch (_: Exception) {
            settingsGone = true
        }
        assertTrue("legacy settings table must be dropped", settingsGone)

        helper.close()
        context.deleteDatabase(testDb)
    }

    @Test
    fun `searchArtistsPaged pages through the library and getArtistsByIds resolves names`() = runBlocking {
        val dao = db.cachedMetadataDao()
        dao.replaceArtists(
            (1..75).map { i ->
                CachedArtistEntity(id = "ar$i", name = "Artist %02d".format(i))
            },
        )

        val firstPage = dao.searchArtistsPaged("Artist", limit = 50, offset = 0)
        val secondPage = dao.searchArtistsPaged("Artist", limit = 50, offset = 50)

        assertEquals(50, firstPage.size)
        assertEquals(25, secondPage.size)
        assertEquals(listOf("Artist 01", "Artist 02"), dao.getArtistsByIds(listOf("ar1", "ar2")).map { it.name })
    }

    @Test
    fun `migration 47 to 48 adds composite filter columns and backfills favorites`() {
        val testDb = "migration-47-48-${System.nanoTime()}.db"
        context.deleteDatabase(testDb)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(47) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE custom_mixes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "name TEXT NOT NULL, source_kind TEXT NOT NULL, " +
                                "genres_json TEXT NOT NULL, decades_json TEXT NOT NULL, " +
                                "auto_cache INTEGER NOT NULL, is_default INTEGER NOT NULL, created_at INTEGER NOT NULL)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build(),
        )
        val raw = helper.writableDatabase
        raw.execSQL(
            "INSERT INTO custom_mixes " +
                "(name, source_kind, genres_json, decades_json, auto_cache, is_default, created_at) " +
                "VALUES ('Favs', 'favoriteArtists', '', '', 0, 0, 1)",
        )
        raw.execSQL(
            "INSERT INTO custom_mixes " +
                "(name, source_kind, genres_json, decades_json, auto_cache, is_default, created_at) " +
                "VALUES ('Rock Mix', 'genres', 'Rock', '', 0, 1, 2)",
        )

        AppDatabase.MIGRATION_47_48.migrate(raw)

        val columns = mutableListOf<String>()
        raw.query("PRAGMA table_info(custom_mixes)").use { c ->
            while (c.moveToNext()) columns.add(c.getString(1))
        }
        assertTrue(columns.contains("artists_json"))
        assertTrue(columns.contains("include_favorite_artists"))

        val favFlag = raw.query("SELECT include_favorite_artists FROM custom_mixes WHERE name = 'Favs'").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        val genreFlag = raw.query(
            "SELECT include_favorite_artists FROM custom_mixes WHERE name = 'Rock Mix'",
        ).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        assertEquals(1, favFlag)
        assertEquals(0, genreFlag)
        helper.close()
        context.deleteDatabase(testDb)
    }
}
