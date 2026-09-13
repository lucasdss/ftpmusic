package com.lucasdss.ftpmusic.app.data.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain-JVM tests for every Room migration in [AppDatabase] (JaCoCo does not
 * capture Robolectric-sandbox classes in this project, so migrations are run
 * against a mocked [SupportSQLiteDatabase] instead of an in-memory Room db).
 * The MIGRATION_28_29 JSON backfill additionally exercises its cursor-driven
 * branches: existing rows, non-JSON payloads, already-migrated rows and empty
 * cursors.
 */
class AppDatabaseMigrationsTest {

    private val db: SupportSQLiteDatabase = mockk(relaxed = true)

    private fun run(migration: androidx.room.migration.Migration) {
        migration.migrate(db)
    }

    @Test
    fun `migration 1 to 2 creates pending_scrobbles`() {
        run(AppDatabase.MIGRATION_1_2)
        verify {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_scrobbles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `track_id` TEXT NOT NULL, `listened_at` INTEGER NOT NULL, `duration_seconds` INTEGER NOT NULL, `flushed` INTEGER NOT NULL DEFAULT 0)",
            )
        }
    }

    @Test
    fun `migration 2 to 3 creates playlist tables`() {
        run(AppDatabase.MIGRATION_2_3)
        verify(exactly = 3) { db.execSQL(match { it.startsWith("CREATE TABLE IF NOT EXISTS") }) }
    }

    @Test
    fun `migration 3 to 4 adds track columns`() {
        run(AppDatabase.MIGRATION_3_4)
        verify(exactly = 3) { db.execSQL(match { it.startsWith("ALTER TABLE `tracks` ADD COLUMN") }) }
    }

    @Test
    fun `migration 4 to 5 creates cache queue table and indexes`() {
        run(AppDatabase.MIGRATION_4_5)
        verify(exactly = 3) { db.execSQL(any()) }
    }

    @Test
    fun `migration 5 to 6 creates session state table`() {
        run(AppDatabase.MIGRATION_5_6)
        verify { db.execSQL(match { it.contains("session_state") }) }
    }

    @Test
    fun `migration 6 to 7 is a no-op`() {
        run(AppDatabase.MIGRATION_6_7)
        verify(exactly = 0) { db.execSQL(any()) }
    }

    @Test
    fun `migration 7 to 8 creates queue items table`() {
        run(AppDatabase.MIGRATION_7_8)
        verify { db.execSQL(match { it.contains("queue_items") }) }
    }

    @Test
    fun `migration 8 to 9 recreates queue items and extends cache queue`() {
        run(AppDatabase.MIGRATION_8_9)
        verify { db.execSQL("DROP TABLE IF EXISTS `queue_items`") }
        verify { db.execSQL(match { it.startsWith("ALTER TABLE `cache_queue_items` ADD COLUMN `downloaded_bytes`") }) }
    }

    @Test
    fun `migration 9 to 10 adds queue item id columns`() {
        run(AppDatabase.MIGRATION_9_10)
        verify(exactly = 2) { db.execSQL(match { it.startsWith("ALTER TABLE queue_items ADD COLUMN") }) }
    }

    @Test
    fun `migration 10 to 11 adds cast columns to session state`() {
        run(AppDatabase.MIGRATION_10_11)
        verify(exactly = 2) { db.execSQL(match { it.startsWith("ALTER TABLE session_state ADD COLUMN") }) }
    }

    @Test
    fun `migration 11 to 12 adds artist to tracks`() {
        run(AppDatabase.MIGRATION_11_12)
        verify { db.execSQL("ALTER TABLE tracks ADD COLUMN artist TEXT") }
    }

    @Test
    fun `migration 12 to 13 adds genre and creates genres table`() {
        run(AppDatabase.MIGRATION_12_13)
        verify { db.execSQL(match { it.contains("genres") }) }
    }

    @Test
    fun `migration 13 to 14 adds retry count`() {
        run(AppDatabase.MIGRATION_13_14)
        verify { db.execSQL(match { it.contains("retry_count") }) }
    }

    @Test
    fun `migration 14 to 15 adds cover art to playlists`() {
        run(AppDatabase.MIGRATION_14_15)
        verify { db.execSQL(match { it.contains("coverArt") }) }
    }

    @Test
    fun `migration 15 to 16 creates playback state table`() {
        run(AppDatabase.MIGRATION_15_16)
        verify { db.execSQL(match { it.contains("playback_state") }) }
    }

    @Test
    fun `migration 16 to 17 creates lyrics cache table`() {
        run(AppDatabase.MIGRATION_16_17)
        verify { db.execSQL(match { it.contains("lyrics_cache") }) }
    }

    @Test
    fun `migration 17 to 18 adds cache version column`() {
        run(AppDatabase.MIGRATION_17_18)
        verify { db.execSQL(match { it.contains("cacheVersion") }) }
    }

    @Test
    fun `migration 18 to 19 adds raw json column`() {
        run(AppDatabase.MIGRATION_18_19)
        verify { db.execSQL(match { it.contains("rawJson") }) }
    }

    @Test
    fun `migration 19 to 20 creates playlist entries index`() {
        run(AppDatabase.MIGRATION_19_20)
        verify { db.execSQL(match { it.contains("idx_playlist_entries_playlist_id") }) }
    }

    @Test
    fun `migration 20 to 21 creates cached albums and artists`() {
        run(AppDatabase.MIGRATION_20_21)
        verify { db.execSQL(match { it.contains("cached_albums") }) }
        verify { db.execSQL(match { it.contains("cached_artists") }) }
    }

    @Test
    fun `migration 21 to 22 creates pending changes index`() {
        run(AppDatabase.MIGRATION_21_22)
        verify { db.execSQL(match { it.contains("idx_pending_playlist_changes_flushed_created_at") }) }
    }

    @Test
    fun `migration 22 to 23 creates cached album tracks`() {
        run(AppDatabase.MIGRATION_22_23)
        verify { db.execSQL(match { it.contains("cached_album_tracks") }) }
    }

    @Test
    fun `migration 23 to 24 creates queue journal`() {
        run(AppDatabase.MIGRATION_23_24)
        verify { db.execSQL(match { it.contains("queue_journal") }) }
    }

    @Test
    fun `migration 24 to 25 creates cached genres`() {
        run(AppDatabase.MIGRATION_24_25)
        verify(exactly = 3) { db.execSQL(match { it.contains("cached_genre") }) }
    }

    @Test
    fun `migration 25 to 26 creates daily mix table`() {
        run(AppDatabase.MIGRATION_25_26)
        verify { db.execSQL(match { it.contains("daily_mix") }) }
    }

    @Test
    fun `migration 26 to 27 adds user rating to tracks`() {
        run(AppDatabase.MIGRATION_26_27)
        verify { db.execSQL(match { it.contains("user_rating") }) }
    }

    @Test
    fun `migration 27 to 28 extends cached album tracks`() {
        run(AppDatabase.MIGRATION_27_28)
        verify(exactly = 6) { db.execSQL(match { it.startsWith("ALTER TABLE cached_album_tracks ADD COLUMN") }) }
    }

    @Test
    fun `migration 28 to 29 backfills existing json rows into join table`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getLong(0) } returns 42L
        every { cursor.getString(1) } returns """["t1","t2"]"""
        val countCursor = mockk<Cursor>(relaxed = true)
        every { countCursor.moveToFirst() } returns true
        every { countCursor.getInt(0) } returns 0
        every { db.query("SELECT id, track_ids_json FROM daily_mix") } returns cursor
        every { db.query("SELECT COUNT(*) FROM daily_mix_tracks WHERE mix_id = ?", any()) } returns countCursor

        run(AppDatabase.MIGRATION_28_29)

        verify(exactly = 2) { db.execSQL(match { it.startsWith("INSERT INTO daily_mix_tracks") }, any()) }
        verify { db.execSQL(match { it.contains("CREATE TABLE daily_mix_new") }) }
        verify { db.execSQL("DROP TABLE daily_mix") }
    }

    @Test
    fun `migration 28 to 29 skips non-json payloads`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getLong(0) } returns 42L
        every { cursor.getString(1) } returns "not-json"
        val countCursor = mockk<Cursor>(relaxed = true)
        every { countCursor.moveToFirst() } returns true
        every { countCursor.getInt(0) } returns 0
        every { db.query("SELECT id, track_ids_json FROM daily_mix") } returns cursor
        every { db.query("SELECT COUNT(*) FROM daily_mix_tracks WHERE mix_id = ?", any()) } returns countCursor

        run(AppDatabase.MIGRATION_28_29)

        verify(exactly = 0) { db.execSQL(match { it.startsWith("INSERT INTO daily_mix_tracks") }, any()) }
    }

    @Test
    fun `migration 28 to 29 skips empty json payloads`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getLong(0) } returns 42L
        every { cursor.getString(1) } returns "[]"
        val countCursor = mockk<Cursor>(relaxed = true)
        every { countCursor.moveToFirst() } returns true
        every { countCursor.getInt(0) } returns 0
        every { db.query("SELECT id, track_ids_json FROM daily_mix") } returns cursor
        every { db.query("SELECT COUNT(*) FROM daily_mix_tracks WHERE mix_id = ?", any()) } returns countCursor

        run(AppDatabase.MIGRATION_28_29)

        verify(exactly = 0) { db.execSQL(match { it.startsWith("INSERT INTO daily_mix_tracks") }, any()) }
    }

    @Test
    fun `migration 28 to 29 skips rows already migrated`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getLong(0) } returns 42L
        every { cursor.getString(1) } returns """["t1"]"""
        val countCursor = mockk<Cursor>(relaxed = true)
        every { countCursor.moveToFirst() } returns true
        every { countCursor.getInt(0) } returns 3
        every { db.query("SELECT id, track_ids_json FROM daily_mix") } returns cursor
        every { db.query("SELECT COUNT(*) FROM daily_mix_tracks WHERE mix_id = ?", any()) } returns countCursor

        run(AppDatabase.MIGRATION_28_29)

        verify(exactly = 0) { db.execSQL(match { it.startsWith("INSERT INTO daily_mix_tracks") }, any()) }
    }

    @Test
    fun `migration 28 to 29 handles empty cursor`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every { db.query("SELECT id, track_ids_json FROM daily_mix") } returns cursor

        run(AppDatabase.MIGRATION_28_29)

        verify(exactly = 0) { db.execSQL(match { it.startsWith("INSERT INTO daily_mix_tracks") }, any()) }
    }

    @Test
    fun `migration 29 to 30 creates queue state table`() {
        run(AppDatabase.MIGRATION_29_30)
        verify { db.execSQL(match { it.contains("queue_state") }) }
    }

    @Test
    fun `migration 30 to 31 adds user rating to albums`() {
        run(AppDatabase.MIGRATION_30_31)
        verify { db.execSQL(match { it.contains("user_rating") }) }
    }

    @Test
    fun `migration 31 to 32 adds is download and backfills`() {
        run(AppDatabase.MIGRATION_31_32)
        verify { db.execSQL(match { it.contains("is_download") }) }
        verify { db.execSQL(match { it.contains("UPDATE cache_queue_items") }) }
    }

    @Test
    fun `migration 32 to 33 drops pending scrobbles`() {
        run(AppDatabase.MIGRATION_32_33)
        verify { db.execSQL(match { it.contains("pending_scrobbles") }) }
    }

    @Test
    fun `migration 33 to 34 adds last synced at to playlists`() {
        run(AppDatabase.MIGRATION_33_34)
        verify { db.execSQL(match { it.contains("last_synced_at") }) }
    }

    @Test
    fun `migration 34 to 35 adds sleep timer column`() {
        run(AppDatabase.MIGRATION_34_35)
        verify { db.execSQL(match { it.contains("sleepTimerEndMs") }) }
    }

    @Test
    fun `migration 35 to 36 creates track waveforms table`() {
        run(AppDatabase.MIGRATION_35_36)
        verify { db.execSQL(match { it.contains("track_waveforms") }) }
    }

    @Test
    fun `migration 36 to 37 creates artist index`() {
        run(AppDatabase.MIGRATION_36_37)
        verify { db.execSQL(match { it.contains("index_tracks_artist_id") }) }
    }

    @Test
    fun `migration 37 to 38 adds rating and mbid columns`() {
        run(AppDatabase.MIGRATION_37_38)
        verify(exactly = 9) { db.execSQL(match { it.startsWith("ALTER TABLE") }) }
    }

    @Test
    fun `migration 38 to 39 adds similar artists json`() {
        run(AppDatabase.MIGRATION_38_39)
        verify { db.execSQL(match { it.contains("similar_artists_json") }) }
    }

    @Test
    fun `migration 39 to 40 adds context size column`() {
        run(AppDatabase.MIGRATION_39_40)
        verify { db.execSQL(match { it.contains("context_size") }) }
    }

    @Test
    fun `migration 40 to 41 adds is disliked to tracks`() {
        run(AppDatabase.MIGRATION_40_41)
        verify { db.execSQL(match { it.contains("is_disliked") }) }
    }

    @Test
    fun `migration 41 to 42 creates daily mix settings table`() {
        run(AppDatabase.MIGRATION_41_42)
        verify { db.execSQL(match { it.contains("daily_mix_settings") }) }
    }

    @Test
    fun `migration 42 to 43 creates radio favorites and adds dislike columns`() {
        run(AppDatabase.MIGRATION_42_43)
        verify { db.execSQL(match { it.contains("radio_favorites") }) }
        verify(exactly = 2) { db.execSQL(match { it.contains("is_disliked") }) }
    }

    @Test
    fun `migration 43 to 44 adds pending unstar columns`() {
        run(AppDatabase.MIGRATION_43_44)
        verify(exactly = 3) { db.execSQL(match { it.contains("pending_unstar_at") }) }
    }

    @Test
    fun `migration 44 to 45 dedupes and creates unique index`() {
        run(AppDatabase.MIGRATION_44_45)
        verify { db.execSQL(match { it.startsWith("DELETE FROM cache_queue_items") }) }
        verify { db.execSQL(match { it.contains("CREATE UNIQUE INDEX") }) }
    }

    @Test
    fun `migration 45 to 46 drops and recreates unique index`() {
        run(AppDatabase.MIGRATION_45_46)
        verify { db.execSQL(match { it.startsWith("DELETE FROM cache_queue_items") }) }
        verify { db.execSQL(match { it.contains("DROP INDEX") }) }
        verify { db.execSQL(match { it.contains("CREATE UNIQUE INDEX") }) }
    }

    @Test
    fun `migration 46 to 47 creates custom mix tables and rekeys daily mix`() {
        run(AppDatabase.MIGRATION_46_47)
        verify { db.execSQL(match { it.contains("custom_mixes") }) }
        verify { db.execSQL(match { it.contains("custom_mix_cache_tracks") }) }
        verify { db.execSQL(match { it.contains("custom_mix_state") }) }
        verify { db.execSQL("DROP TABLE IF EXISTS `daily_mix_tracks`") }
        verify { db.execSQL("DROP TABLE IF EXISTS `daily_mix`") }
        verify { db.execSQL(match { it.contains("`mix_id` INTEGER NOT NULL") }) }
        verify { db.execSQL("DROP TABLE IF EXISTS `daily_mix_settings`") }
    }

    @Test
    fun `migration 46 to 47 maps the legacy selection to genre mixes`() {
        val cursor: Cursor = mockk(relaxed = true)
        every { db.query(any<String>()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "Jazz\nBlues"
        every { cursor.getInt(1) } returns 1

        run(AppDatabase.MIGRATION_46_47)

        verify { db.execSQL(match { it.contains("INSERT INTO custom_mixes") }, any()) }
        verify { db.execSQL(match { it.contains("custom_mix_state") }, any()) }
    }

    @Test
    fun `migration 46 to 47 skips mapping when no legacy settings row exists`() {
        val cursor: Cursor = mockk(relaxed = true)
        every { db.query(any<String>()) } returns cursor
        every { cursor.moveToFirst() } returns false

        run(AppDatabase.MIGRATION_46_47)

        verify(exactly = 0) { db.execSQL(match { it.contains("INSERT INTO custom_mixes") }, any()) }
        verify(exactly = 0) { db.execSQL(match { it.contains("INSERT OR REPLACE INTO custom_mix_state") }, any()) }
    }

    @Test
    fun `migration 46 to 47 does not mark seeded for an empty legacy selection`() {
        val cursor: Cursor = mockk(relaxed = true)
        every { db.query(any<String>()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "   "
        every { cursor.getInt(1) } returns 0

        run(AppDatabase.MIGRATION_46_47)

        verify(exactly = 0) { db.execSQL(match { it.contains("INSERT INTO custom_mixes") }, any()) }
        verify(exactly = 0) { db.execSQL(match { it.contains("INSERT OR REPLACE INTO custom_mix_state") }, any()) }
    }

    @Test
    fun `migration 47 to 48 adds composite filter columns and backfills favorites`() {
        run(AppDatabase.MIGRATION_47_48)
        verify { db.execSQL(match { it.contains("artists_json") }) }
        verify { db.execSQL(match { it.contains("include_favorite_artists") }) }
        verify { db.execSQL(match { it.startsWith("UPDATE custom_mixes") }) }
    }

    @Test
    fun `migration 48 to 49 adds is_priority and backfills from context_size`() {
        run(AppDatabase.MIGRATION_48_49)
        verify { db.execSQL(match { it.contains("is_priority") }) }
        verify { db.execSQL(match { it.contains("UPDATE queue_items") && it.contains("is_priority") }) }
    }

    @Test
    fun `migration 49 to 50 adds entry_id and next_entry_id`() {
        run(AppDatabase.MIGRATION_49_50)
        verify { db.execSQL(match { it.contains("entry_id") }) }
        verify { db.execSQL(match { it.contains("next_entry_id") }) }
    }

    @Test
    fun `all migrations arrays are ordered and complete`() {
        assertEquals(8, AppDatabase.ALL_MIGRATIONS.size)
        assertEquals(50, AppDatabase.ALL_MIGRATIONS_50.last().endVersion)
        // Referencing every array ensures the construction lines are covered
        val all = listOf(
            AppDatabase.ALL_MIGRATIONS_10, AppDatabase.ALL_MIGRATIONS_11,
            AppDatabase.ALL_MIGRATIONS_12, AppDatabase.ALL_MIGRATIONS_13,
            AppDatabase.ALL_MIGRATIONS_14, AppDatabase.ALL_MIGRATIONS_15,
            AppDatabase.ALL_MIGRATIONS_16, AppDatabase.ALL_MIGRATIONS_17,
            AppDatabase.ALL_MIGRATIONS_18, AppDatabase.ALL_MIGRATIONS_19,
            AppDatabase.ALL_MIGRATIONS_20, AppDatabase.ALL_MIGRATIONS_21,
            AppDatabase.ALL_MIGRATIONS_22, AppDatabase.ALL_MIGRATIONS_23,
            AppDatabase.ALL_MIGRATIONS_24, AppDatabase.ALL_MIGRATIONS_25,
            AppDatabase.ALL_MIGRATIONS_26, AppDatabase.ALL_MIGRATIONS_27,
            AppDatabase.ALL_MIGRATIONS_28, AppDatabase.ALL_MIGRATIONS_29,
            AppDatabase.ALL_MIGRATIONS_30, AppDatabase.ALL_MIGRATIONS_31,
            AppDatabase.ALL_MIGRATIONS_32, AppDatabase.ALL_MIGRATIONS_33,
            AppDatabase.ALL_MIGRATIONS_34, AppDatabase.ALL_MIGRATIONS_35,
            AppDatabase.ALL_MIGRATIONS_36, AppDatabase.ALL_MIGRATIONS_37,
            AppDatabase.ALL_MIGRATIONS_38, AppDatabase.ALL_MIGRATIONS_39,
            AppDatabase.ALL_MIGRATIONS_40, AppDatabase.ALL_MIGRATIONS_41,
            AppDatabase.ALL_MIGRATIONS_42, AppDatabase.ALL_MIGRATIONS_43,
            AppDatabase.ALL_MIGRATIONS_44, AppDatabase.ALL_MIGRATIONS_45,
            AppDatabase.ALL_MIGRATIONS_46, AppDatabase.ALL_MIGRATIONS_47,
            AppDatabase.ALL_MIGRATIONS_48, AppDatabase.ALL_MIGRATIONS_49,
            AppDatabase.ALL_MIGRATIONS_50,
        )
        assertEquals(41, all.size)
        for (m in all) {
            assertEquals(m.first().startVersion + m.size, m.last().endVersion)
        }
    }
}
