package com.lucasdss.ftpmusic.app.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real-SQLite upgrade safety for Play 2.0.0 (schema 68) → Room 71.
 *
 * Mock [AppDatabaseMigrationsTest] only checks SQL strings; this suite seeds
 * v68 tables, runs the migrations, and asserts Daily Mix pointer / version
 * invariants plus additive cover/palette tables.
 */
@RunWith(RobolectricTestRunner::class)
class Room68To71MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun openAt68(name: String): SupportSQLiteOpenHelper {
        context.deleteDatabase(name)
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(68) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createSchema68Minimal(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
    }

    /** Minimal v68 tables touched by 68→71 (FK-safe order). */
    private fun createSchema68Minimal(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_mixes` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `name` TEXT NOT NULL,
              `source_kind` TEXT NOT NULL,
              `genres_json` TEXT NOT NULL,
              `decades_json` TEXT NOT NULL,
              `artists_json` TEXT NOT NULL DEFAULT '',
              `include_favorite_artists` INTEGER NOT NULL DEFAULT 0,
              `auto_cache` INTEGER NOT NULL,
              `allow_cross_mix_fill` INTEGER NOT NULL DEFAULT 0,
              `is_default` INTEGER NOT NULL,
              `created_at` INTEGER NOT NULL,
              `fixed_cover_kind` TEXT,
              `fixed_cover_value` TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `daily_mix` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `date` TEXT NOT NULL,
              `mix_id` INTEGER NOT NULL,
              `created_at` INTEGER NOT NULL,
              FOREIGN KEY(`mix_id`) REFERENCES `custom_mixes`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `idx_daily_mix_date_mix_id` " +
                "ON `daily_mix` (`date`, `mix_id`)",
        )
    }

    private fun insertRecipe(db: SupportSQLiteDatabase, id: Long, name: String) {
        db.execSQL(
            """
            INSERT INTO custom_mixes
              (id, name, source_kind, genres_json, decades_json, artists_json,
               include_favorite_artists, auto_cache, allow_cross_mix_fill,
               is_default, created_at)
            VALUES ($id, '$name', 'genres', 'Rock', '', '', 0, 0, 0, 1, 1000)
            """.trimIndent(),
        )
    }

    private fun insertDaily(
        db: SupportSQLiteDatabase,
        id: Int,
        mixId: Long,
        date: String,
        createdAt: Long,
    ) {
        db.execSQL(
            """
            INSERT INTO daily_mix (id, date, mix_id, created_at)
            VALUES ($id, '$date', $mixId, $createdAt)
            """.trimIndent(),
        )
    }

    private fun queryLong(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        }

    private fun queryNullableLong(db: SupportSQLiteDatabase, sql: String): Long? =
        db.query(sql).use { c ->
            assertTrue(c.moveToFirst())
            if (c.isNull(0)) null else c.getLong(0)
        }

    private fun queryString(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { c ->
            assertTrue(c.moveToFirst())
            c.getString(0)
        }

    private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean =
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$name'",
        ).use { c ->
            c.moveToFirst() && c.getLong(0) == 1L
        }

    private fun indexUnique(db: SupportSQLiteDatabase, name: String): Boolean =
        db.query("PRAGMA index_list('daily_mix')").use { c ->
            while (c.moveToNext()) {
                val idxName = c.getString(c.getColumnIndexOrThrow("name"))
                if (idxName == name) {
                    return c.getInt(c.getColumnIndexOrThrow("unique")) == 1
                }
            }
            false
        }

    @Test
    fun `68 to 69 ranks versions and points active at newest`() {
        val name = "mig-68-69-${System.nanoTime()}.db"
        val helper = openAt68(name)
        val db = helper.writableDatabase

        insertRecipe(db, 1L, "Rock")
        insertRecipe(db, 2L, "Jazz")
        insertDaily(db, 10, 1L, "2026-11-01", 100)
        insertDaily(db, 11, 1L, "2026-11-02", 200)
        insertDaily(db, 12, 1L, "2026-11-03", 300)
        insertDaily(db, 20, 2L, "2026-11-01", 150)
        insertDaily(db, 21, 2L, "2026-11-05", 250)

        AppDatabase.MIGRATION_68_69.migrate(db)

        assertEquals(1L, queryLong(db, "SELECT version FROM daily_mix WHERE id=10"))
        assertEquals(2L, queryLong(db, "SELECT version FROM daily_mix WHERE id=11"))
        assertEquals(3L, queryLong(db, "SELECT version FROM daily_mix WHERE id=12"))
        assertEquals(1L, queryLong(db, "SELECT version FROM daily_mix WHERE id=20"))
        assertEquals(2L, queryLong(db, "SELECT version FROM daily_mix WHERE id=21"))

        assertEquals(12L, queryLong(db, "SELECT active_daily_mix_id FROM custom_mixes WHERE id=1"))
        assertEquals(21L, queryLong(db, "SELECT active_daily_mix_id FROM custom_mixes WHERE id=2"))
        assertNull(queryNullableLong(db, "SELECT pending_daily_mix_id FROM custom_mixes WHERE id=1"))

        assertEquals("ready", queryString(db, "SELECT status FROM daily_mix WHERE id=12"))
        assertEquals("superseded", queryString(db, "SELECT status FROM daily_mix WHERE id=10"))
        assertEquals("superseded", queryString(db, "SELECT status FROM daily_mix WHERE id=11"))
        assertEquals("ready", queryString(db, "SELECT status FROM daily_mix WHERE id=21"))
        assertEquals("superseded", queryString(db, "SELECT status FROM daily_mix WHERE id=20"))

        assertFalse(
            "date/mix index must no longer be unique after 68→69",
            indexUnique(db, "idx_daily_mix_date_mix_id"),
        )
        assertTrue(indexUnique(db, "idx_daily_mix_mix_version"))

        // Same (date, mix_id) allowed after unique drop.
        db.execSQL(
            "INSERT INTO daily_mix (date, mix_id, created_at, version, status) " +
                "VALUES ('2026-11-03', 1, 400, 4, 'ready')",
        )
        var rejected = false
        try {
            db.execSQL(
                "INSERT INTO daily_mix (date, mix_id, created_at, version, status) " +
                    "VALUES ('2026-11-10', 1, 500, 3, 'ready')",
            )
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("duplicate (mix_id, version) must be rejected", rejected)

        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `68 to 69 empty recipes leave pointers null`() {
        val name = "mig-68-69-empty-${System.nanoTime()}.db"
        val helper = openAt68(name)
        val db = helper.writableDatabase
        insertRecipe(db, 5L, "Empty")

        AppDatabase.MIGRATION_68_69.migrate(db)

        assertNull(queryNullableLong(db, "SELECT active_daily_mix_id FROM custom_mixes WHERE id=5"))
        assertNull(queryNullableLong(db, "SELECT pending_daily_mix_id FROM custom_mixes WHERE id=5"))
        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `68 to 69 same-date rows rank by created_at then id`() {
        val name = "mig-68-69-tie-${System.nanoTime()}.db"
        val helper = openAt68(name)
        val db = helper.writableDatabase
        insertRecipe(db, 1L, "Tie")
        // v68 UNIQUE(date, mix_id) forbids same-date siblings; drop it to exercise
        // the migration's (created_at, id) tie-break that becomes reachable after
        // 68→69 makes the date index non-unique.
        db.execSQL("DROP INDEX IF EXISTS idx_daily_mix_date_mix_id")
        insertDaily(db, 1, 1L, "2026-11-01", 100)
        insertDaily(db, 2, 1L, "2026-11-01", 100)
        insertDaily(db, 3, 1L, "2026-11-01", 50)

        AppDatabase.MIGRATION_68_69.migrate(db)

        // created_at 50 → v1; then same created_at 100 with id 1 → v2, id 2 → v3
        assertEquals(1L, queryLong(db, "SELECT version FROM daily_mix WHERE id=3"))
        assertEquals(2L, queryLong(db, "SELECT version FROM daily_mix WHERE id=1"))
        assertEquals(3L, queryLong(db, "SELECT version FROM daily_mix WHERE id=2"))
        assertEquals(2L, queryLong(db, "SELECT active_daily_mix_id FROM custom_mixes WHERE id=1"))
        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `69 to 70 creates cover tables`() {
        val name = "mig-69-70-${System.nanoTime()}.db"
        val helper = openAt68(name)
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_68_69.migrate(db)
        AppDatabase.MIGRATION_69_70.migrate(db)

        assertTrue(tableExists(db, "user_cover_images"))
        assertTrue(tableExists(db, "genre_covers"))
        db.execSQL(
            "INSERT INTO user_cover_images (id, relative_path, created_at_ms, last_used_at_ms) " +
                "VALUES ('c1', 'covers/a.webp', 1, 2)",
        )
        db.execSQL(
            "INSERT INTO genre_covers (genre_name, fixed_cover_kind, fixed_cover_value, updated_at_ms) " +
                "VALUES ('Rock', 'bundled', 'rock.webp', 3)",
        )
        assertEquals(1L, queryLong(db, "SELECT COUNT(*) FROM user_cover_images"))
        assertEquals(1L, queryLong(db, "SELECT COUNT(*) FROM genre_covers"))
        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `70 to 71 creates art_palette_cache with nullable argb`() {
        val name = "mig-70-71-${System.nanoTime()}.db"
        val helper = openAt68(name)
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_68_69.migrate(db)
        AppDatabase.MIGRATION_69_70.migrate(db)
        AppDatabase.MIGRATION_70_71.migrate(db)

        assertTrue(tableExists(db, "art_palette_cache"))
        db.execSQL(
            """
            INSERT INTO art_palette_cache
              (server_scope, art_key, source_version, algorithm_version, argb, updated_at)
            VALUES ('s1', 'k1', 'v1', 1, NULL, 10)
            """.trimIndent(),
        )
        assertNull(
            queryNullableLong(
                db,
                "SELECT argb FROM art_palette_cache WHERE server_scope='s1' AND art_key='k1'",
            ),
        )
        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `68 to 71 chain preserves sticky DM pointers and new tables`() {
        val name = "mig-68-71-${System.nanoTime()}.db"
        val helper = openAt68(name)
        val db = helper.writableDatabase
        insertRecipe(db, 1L, "Rock")
        insertDaily(db, 10, 1L, "2026-11-01", 100)
        insertDaily(db, 11, 1L, "2026-11-07", 200)

        AppDatabase.MIGRATION_68_69.migrate(db)
        AppDatabase.MIGRATION_69_70.migrate(db)
        AppDatabase.MIGRATION_70_71.migrate(db)

        assertEquals(11L, queryLong(db, "SELECT active_daily_mix_id FROM custom_mixes WHERE id=1"))
        assertEquals("ready", queryString(db, "SELECT status FROM daily_mix WHERE id=11"))
        assertEquals("superseded", queryString(db, "SELECT status FROM daily_mix WHERE id=10"))
        assertTrue(tableExists(db, "user_cover_images"))
        assertTrue(tableExists(db, "genre_covers"))
        assertTrue(tableExists(db, "art_palette_cache"))

        helper.close()
        context.deleteDatabase(name)
    }
}
