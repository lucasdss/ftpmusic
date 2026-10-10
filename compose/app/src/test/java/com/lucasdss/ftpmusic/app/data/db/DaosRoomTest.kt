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
import org.junit.Assert.assertNotNull
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
            // No BundledSQLiteDriver / FTS5 callback: Robolectric framework SQLite
            // lacks FTS5 JNI. Production DatabaseModule wires both (ADR 0084).
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
    fun `mix decade pool excludes disliked albums`() = runBlocking {
        val trackDao = db.trackDao()
        val meta = db.cachedMetadataDao()
        trackDao.upsert(TrackEntity(id = "y1", title = "Y1", albumId = "al1"))
        trackDao.upsert(TrackEntity(id = "y2", title = "Y2", albumId = "al2"))
        meta.replaceAlbums(
            listOf(
                CachedAlbumEntity(id = "al1", name = "Good", artist = "X", year = 1985),
                CachedAlbumEntity(id = "al2", name = "Bad", artist = "Y", year = 1986),
            ),
        )
        meta.insertNewAlbumsToLedger()
        meta.setAlbumDisliked("al2", true)

        assertEquals(listOf("y1"), trackDao.getMixTrackIdsByYearRange(1980, 1989))
    }

    @Test
    fun `mix genre pool excludes disliked album and artist via join`() = runBlocking {
        val trackDao = db.trackDao()
        val meta = db.cachedMetadataDao()
        trackDao.upsert(
            TrackEntity(id = "g1", title = "G1", genre = "Rock", albumId = "al1", artistId = "ar1"),
        )
        trackDao.upsert(
            TrackEntity(id = "g2", title = "G2", genre = "Rock", albumId = "al2", artistId = "ar1"),
        )
        trackDao.upsert(
            TrackEntity(id = "g3", title = "G3", genre = "Rock", albumId = "al1", artistId = "ar2"),
        )
        meta.replaceAlbums(
            listOf(
                CachedAlbumEntity(id = "al1", name = "A1", artist = "X", year = 2000),
                CachedAlbumEntity(id = "al2", name = "A2", artist = "Y", year = 2001),
            ),
        )
        meta.insertNewAlbumsToLedger()
        // ensureArtistLedgerRow reads from cached_artists — seed those first.
        meta.replaceArtists(
            listOf(
                CachedArtistEntity(id = "ar1", name = "Artist One"),
                CachedArtistEntity(id = "ar2", name = "Artist Two"),
            ),
        )
        meta.ensureArtistLedgerRow("ar1")
        meta.ensureArtistLedgerRow("ar2")
        meta.setAlbumDisliked("al2", true)
        meta.setArtistDisliked("ar2", true)

        assertEquals(listOf("g1"), trackDao.getMixTrackIdsByGenre("Rock"))
        assertEquals(listOf("g1"), trackDao.filterMixPlayableIds(listOf("g1", "g2", "g3")))
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
    fun `searchAllTracks finds singles with null album_id by title and genre`() = runBlocking {
        val trackDao = db.trackDao()
        trackDao.upsertAll(
            listOf(
                TrackEntity(
                    id = "single-1",
                    title = "Lonely Single",
                    artist = "Solo Act",
                    album = null,
                    albumId = null,
                    genre = "Indie",
                ),
            ),
        )
        val byTitle = trackDao.searchAllTracks("Lonely")
        assertEquals(1, byTitle.size)
        assertNull(byTitle[0].albumId)
        val byGenre = trackDao.searchAllTracks("Indie")
        assertEquals(1, byGenre.size)
        assertEquals("single-1", byGenre[0].id)
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

    @Test
    fun `insertArtistsIgnore fills cache so ensureArtistLedgerRow like sticks`() = runBlocking {
        val meta = db.cachedMetadataDao()
        // Seed one enriched artist — IGNORE must not wipe enrichment on re-insert.
        meta.upsertArtists(
            listOf(
                CachedArtistEntity(
                    id = "ar-keep",
                    name = "Keep",
                    musicbrainzId = "mbid-keep",
                    publicRating = 4.5,
                ),
            ),
        )
        // Simulate Library API artists: one existing, one missing from cache.
        meta.insertArtistsIgnore(
            listOf(
                CachedArtistEntity(id = "ar-keep", name = "Keep Overwrite Attempt", musicbrainzId = null),
                CachedArtistEntity(id = "ar-new", name = "New From API", coverArt = "cov"),
            ),
        )

        val kept = meta.getArtistById("ar-keep")!!
        assertEquals("Keep", kept.name)
        assertEquals("mbid-keep", kept.musicbrainzId)
        assertEquals(4.5, kept.publicRating!!, 0.0)

        val neu = meta.getArtistById("ar-new")!!
        assertEquals("New From API", neu.name)

        // Like path without prior syncArtistLedger — ensure + star must persist.
        meta.ensureArtistLedgerRow("ar-new")
        meta.setArtistStarredAt("ar-new", 1_700_000_000_000L)
        val starred = meta.getStarredArtists()
        assertEquals(1, starred.size)
        assertEquals("ar-new", starred[0].id)
        assertEquals(1_700_000_000_000L, starred[0].starredAt)

        // Dislike path for a cache-only-via-IGNORE artist.
        meta.ensureArtistLedgerRow("ar-keep")
        meta.setArtistDisliked("ar-keep", true, at = 99L)
        assertTrue(meta.getDislikedArtistIds().contains("ar-keep"))
    }

    @Test
    fun `insertAlbumsIgnore fills cache so ensureAlbumLedgerRow like sticks`() = runBlocking {
        val meta = db.cachedMetadataDao()
        meta.upsertAlbums(
            listOf(
                CachedAlbumEntity(
                    id = "al-keep",
                    name = "Keep",
                    musicbrainzId = "mbid-al",
                    publicRating = 3.2,
                ),
            ),
        )
        meta.insertAlbumsIgnore(
            listOf(
                CachedAlbumEntity(id = "al-keep", name = "Overwrite Attempt", musicbrainzId = null),
                CachedAlbumEntity(id = "al-new", name = "New From API", artist = "X", coverArt = "cov"),
            ),
        )

        val kept = meta.getAlbumById("al-keep")!!
        assertEquals("Keep", kept.name)
        assertEquals("mbid-al", kept.musicbrainzId)

        val neu = meta.getAlbumById("al-new")!!
        assertEquals("New From API", neu.name)

        meta.ensureAlbumLedgerRow("al-new")
        meta.setAlbumStarredAt("al-new", 1_700_000_000_000L)
        assertTrue(meta.isAlbumStarred("al-new"))
        assertEquals(1, meta.getStarredAlbums().size)
    }

    @Test
    fun `ensureTrackRow lets like stick without prior track sync`() = runBlocking {
        val tracks = db.trackDao()
        tracks.ensureTrackRow("t-ghost")
        tracks.setStarredAt("t-ghost", 99L)
        assertTrue(tracks.isTrackStarred("t-ghost"))
        tracks.setDisliked("t-ghost", true, at = 100L)
        assertTrue(tracks.isTrackDisliked("t-ghost"))
    }

    @Test
    fun `upsertAlbumsPreserveEnrich keeps notes and mbid across catalog refresh`() = runBlocking {
        val meta = db.cachedMetadataDao()
        meta.upsertAlbums(
            listOf(
                CachedAlbumEntity(
                    id = "al-en",
                    name = "Old",
                    notes = "keep-notes",
                    musicbrainzId = "mbid-al",
                    publicRating = 4.0,
                ),
            ),
        )
        meta.upsertAlbumsPreserveEnrich(
            listOf(
                CachedAlbumEntity(id = "al-en", name = "New Name", songCount = 12, notes = null),
            ),
        )
        val row = meta.getAlbumById("al-en")!!
        assertEquals("New Name", row.name)
        assertEquals(12, row.songCount)
        assertEquals("keep-notes", row.notes)
        assertEquals("mbid-al", row.musicbrainzId)
        assertEquals(4.0, row.publicRating!!, 0.0)
    }

    @Test
    fun `replaceAlbumsDiffPreserveEnrich deletes missing and keeps enrich`() = runBlocking {
        val meta = db.cachedMetadataDao()
        meta.upsertAlbums(
            listOf(
                CachedAlbumEntity(id = "keep", name = "K", notes = "n1", musicbrainzId = "mb1"),
                CachedAlbumEntity(id = "gone", name = "G", notes = "n2"),
            ),
        )
        meta.replaceAlbumsDiffPreserveEnrich(
            listOf(CachedAlbumEntity(id = "keep", name = "K2", songCount = 3)),
        )
        assertNull(meta.getAlbumById("gone"))
        val kept = meta.getAlbumById("keep")!!
        assertEquals("K2", kept.name)
        assertEquals("n1", kept.notes)
        assertEquals("mb1", kept.musicbrainzId)
    }

    @Test
    fun `upsertArtistsPreserveEnrich keeps biography aliases tags`() = runBlocking {
        val meta = db.cachedMetadataDao()
        meta.upsertArtists(
            listOf(
                CachedArtistEntity(
                    id = "ar-1",
                    name = "Artist",
                    biography = "bio",
                    searchAliases = "aka",
                    searchTags = "rock",
                    musicbrainzId = "mb-ar",
                ),
            ),
        )
        meta.upsertArtistsPreserveEnrich(
            listOf(CachedArtistEntity(id = "ar-1", name = "Artist Renamed", albumCount = 5)),
        )
        val row = meta.getArtistById("ar-1")!!
        assertEquals("Artist Renamed", row.name)
        assertEquals(5, row.albumCount)
        assertEquals("bio", row.biography)
        assertEquals("aka", row.searchAliases)
        assertEquals("rock", row.searchTags)
        assertEquals("mb-ar", row.musicbrainzId)
    }

    @Test
    fun `upsertTracksPreserveCache keeps album_id and cache columns on orphan merge`() = runBlocking {
        val tracks = db.trackDao()
        tracks.upsert(
            TrackEntity(
                id = "t-merge",
                title = "Old",
                albumId = "al-1",
                album = "Album",
                starredAt = 99L,
                cachedFilePath = "/cache/t",
                isDownloaded = true,
            ),
        )
        tracks.upsertTracksPreserveCache(
            listOf(
                TrackEntity(
                    id = "t-merge",
                    title = "New Title",
                    artist = "Solo",
                    albumId = null,
                    album = null,
                ),
            ),
        )
        val row = tracks.getTrack("t-merge")!!
        assertEquals("New Title", row.title)
        assertEquals("al-1", row.albumId)
        assertEquals("Album", row.album)
        assertEquals(99L, row.starredAt)
        assertEquals("/cache/t", row.cachedFilePath)
        assertTrue(row.isDownloaded)
        assertEquals(1, tracks.trackCountAll())
    }

    @Test
    fun `upsertTracksPreserveCache fills path and mbid from densify onto stub`() = runBlocking {
        val tracks = db.trackDao()
        tracks.upsert(
            TrackEntity(
                id = "t-path",
                title = "Stub",
                path = null,
                musicbrainzId = null,
                albumId = "al-1",
            ),
        )
        tracks.upsertTracksPreserveCache(
            listOf(
                TrackEntity(
                    id = "t-path",
                    title = "Stub",
                    path = "/music/a.mp3",
                    musicbrainzId = "mb-track",
                    albumId = null,
                ),
            ),
        )
        val row = tracks.getTrack("t-path")!!
        assertEquals("/music/a.mp3", row.path)
        assertEquals("mb-track", row.musicbrainzId)
        assertEquals("al-1", row.albumId)
    }

    @Test
    fun `orphan track without album_id persists and counts once`() = runBlocking {
        val tracks = db.trackDao()
        tracks.upsertTracksPreserveCache(
            listOf(
                TrackEntity(id = "orphan", title = "Single", albumId = null, artist = "A"),
                TrackEntity(id = "orphan", title = "Single", albumId = null, artist = "A"),
            ),
        )
        assertEquals(1, tracks.trackCountAll())
        assertNull(tracks.getTrack("orphan")!!.albumId)
    }

    @Test
    fun `starred paging page0 and page1 are disjoint and short page clears hasMore`() = runBlocking {
        val tracks = db.trackDao()
        val base = 1_700_000_000_000L
        // 3 starred tracks — page size 2 → page0=[newest,next], page1=[oldest], page2=[]
        for (i in 0 until 3) {
            val id = "page-t-$i"
            tracks.ensureTrackRow(id)
            tracks.setStarredAt(id, base + i)
        }
        val page0 = tracks.getStarred(limit = 2, offset = 0)
        val page1 = tracks.getStarred(limit = 2, offset = 2)
        val page2 = tracks.getStarred(limit = 2, offset = 4)
        assertEquals(2, page0.size)
        assertEquals(1, page1.size)
        assertEquals(0, page2.size)
        val ids0 = page0.map { it.id }.toSet()
        val ids1 = page1.map { it.id }.toSet()
        assertTrue(ids0.intersect(ids1).isEmpty())
        assertTrue(page1.size < 2) // hasMore false when short page

        val meta = db.cachedMetadataDao()
        for (i in 0 until 3) {
            val id = "page-al-$i"
            meta.upsertAlbums(listOf(CachedAlbumEntity(id = id, name = "A$i")))
            meta.ensureAlbumLedgerRow(id)
            meta.setAlbumStarredAt(id, base + i)
        }
        val al0 = meta.getStarredAlbums(2, 0)
        val al1 = meta.getStarredAlbums(2, 2)
        assertEquals(2, al0.size)
        assertEquals(1, al1.size)
        assertTrue(al0.map { it.id }.toSet().intersect(al1.map { it.id }.toSet()).isEmpty())
    }

    // ── Pending-unstar survival (YT Music second-tap unlike) ───────────────

    @Test
    fun `reconcileSearchCorpusAgainstCatalog prunes densify ghosts keeps local weight`() = runBlocking {
        val tracks = db.trackDao()
        val meta = db.cachedMetadataDao()
        val genres = db.genreMixDao()

        meta.upsertAlbums(listOf(CachedAlbumEntity(id = "al-1", name = "Keep", songCount = 1)))
        meta.upsertAlbumTracks(
            listOf(CachedAlbumTrackEntity(id = "t-album", albumId = "al-1", title = "Album Song")),
        )
        genres.upsertSongs(
            listOf(
                CachedGenreSongEntity(
                    id = "t-genre",
                    genre = "Rock",
                    title = "Genre Song",
                ),
            ),
        )
        tracks.upsert(TrackEntity(id = "t-album", title = "Album Song", albumId = "al-1"))
        tracks.upsert(TrackEntity(id = "t-genre", title = "Genre Song", genre = "Rock"))
        tracks.upsert(TrackEntity(id = "t-ghost", title = "Ghost densify"))
        tracks.upsert(TrackEntity(id = "t-star", title = "Starred ghost", starredAt = 99L))
        tracks.upsert(TrackEntity(id = "t-played", title = "Played ghost", playCount = 3))
        tracks.upsert(
            TrackEntity(
                id = "t-played-once",
                title = "Last-played ghost",
                playCount = 0,
                lastPlayedAt = 1_700_000_000_000L,
            ),
        )
        tracks.upsert(
            TrackEntity(
                id = "t-dl",
                title = "Downloaded ghost",
                isDownloaded = true,
                cachedFilePath = "/dl",
            ),
        )

        val pruned = tracks.reconcileSearchCorpusAgainstCatalog()
        assertEquals(1, pruned)
        assertEquals(6, tracks.trackCountAll())
        assertNull(tracks.getTrack("t-ghost"))
        assertNotNull(tracks.getTrack("t-album"))
        assertNotNull(tracks.getTrack("t-genre"))
        assertNotNull(tracks.getTrack("t-star"))
        assertNotNull(tracks.getTrack("t-played"))
        assertNotNull(tracks.getTrack("t-played-once"))
        assertNotNull(tracks.getTrack("t-dl"))
    }

    @Test
    fun `populateAllTrackGenres preserves pending_unstar_at`() = runBlocking {
        val tracks = db.trackDao()
        val meta = db.cachedMetadataDao()
        tracks.upsert(
            TrackEntity(
                id = "t-pending",
                title = "Pending",
                albumId = "al-p",
                starredAt = null,
                pendingUnstarAt = 1_700_000_000_111L,
            ),
        )
        meta.upsertAlbums(
            listOf(CachedAlbumEntity(id = "al-p", name = "Album", genre = "Rock")),
        )
        meta.upsertAlbumTracks(
            listOf(
                CachedAlbumTrackEntity(
                    id = "t-pending",
                    albumId = "al-p",
                    title = "Pending Updated",
                    artist = "A",
                ),
            ),
        )

        tracks.populateAllTrackGenres()

        val row = tracks.getTrack("t-pending")!!
        assertEquals(1_700_000_000_111L, row.pendingUnstarAt)
        assertNull(row.starredAt)
        assertEquals("Pending Updated", row.title)
    }

    @Test
    fun `clearStarAndMarkPendingUnstar is atomic`() = runBlocking {
        val tracks = db.trackDao()
        tracks.ensureTrackRow("t-atom")
        tracks.setStarredAt("t-atom", 99L)
        tracks.clearStarAndMarkPendingUnstar("t-atom", 1_700_000_000_222L)
        val row = tracks.getTrack("t-atom")!!
        assertNull(row.starredAt)
        assertEquals(1_700_000_000_222L, row.pendingUnstarAt)
        assertEquals(listOf("t-atom"), tracks.getPendingUnstarIds())
    }

    @Test
    fun `pruneAlbumLedger keeps pending-unstar rows`() = runBlocking {
        val meta = db.cachedMetadataDao()
        // Ledger-only albums (not in cached_albums).
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO albums (id, server_id, name, is_disliked, pending_unstar_at) " +
                "VALUES ('al-pend', '', 'Gone', 0, 1700000000333)",
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO albums (id, server_id, name, is_disliked) " +
                "VALUES ('al-drop', '', 'Drop Me', 0)",
        )

        meta.pruneAlbumLedger()

        val pending = meta.getPendingUnstarAlbumIds()
        assertTrue("pending-unstar album must survive prune", pending.contains("al-pend"))
        assertFalse(pending.contains("al-drop"))
    }

    @Test
    fun `pruneArtistLedger keeps pending-unstar rows`() = runBlocking {
        val meta = db.cachedMetadataDao()
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO artists (id, server_id, name, is_disliked, pending_unstar_at) " +
                "VALUES ('ar-pend', '', 'Gone', 0, 1700000000444)",
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO artists (id, server_id, name, is_disliked) " +
                "VALUES ('ar-drop', '', 'Drop Me', 0)",
        )

        meta.pruneArtistLedger()

        assertTrue(meta.getPendingUnstarArtistIds().contains("ar-pend"))
        assertFalse(meta.getPendingUnstarArtistIds().contains("ar-drop"))
    }
}
