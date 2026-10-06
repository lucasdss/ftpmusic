package com.lucasdss.ftpmusic.app.data.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFtsDaoTest {

    private val db = mockk<AppDatabase>()
    private val dao = SearchFtsDao(db)

    @Test
    fun `match maps bm25 rows`() = runTest {
        val cursor = mockk<Cursor>(relaxed = true)
        every { db.query(any<SupportSQLiteQuery>()) } returns cursor
        every { cursor.getColumnIndexOrThrow("rowid") } returns 0
        every { cursor.getColumnIndexOrThrow("entity_type") } returns 1
        every { cursor.getColumnIndexOrThrow("entity_id") } returns 2
        every { cursor.getColumnIndexOrThrow("body") } returns 3
        every { cursor.getColumnIndexOrThrow("rank") } returns 4
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getLong(0) } returns 7L
        every { cursor.getString(1) } returns SearchFtsTypes.TRACK
        every { cursor.getString(2) } returns "t1"
        every { cursor.getString(3) } returns "Hello"
        every { cursor.getDouble(4) } returns 0.25

        val rows = dao.match("hello*", 10)
        assertEquals(1, rows.size)
        assertEquals("t1", rows[0].entityId)
        assertEquals(0.25, rows[0].rank, 0.0)
        assertEquals(SearchFtsTypes.TRACK, rows[0].entityType)
        verify { cursor.close() }
    }

    @Test
    fun `count returns first column`() = runTest {
        val cursor = mockk<Cursor>(relaxed = true)
        every { db.query(any<SupportSQLiteQuery>()) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getInt(0) } returns 42
        assertEquals(42, dao.count())
    }

    @Test
    fun `count empty cursor returns zero`() = runTest {
        val cursor = mockk<Cursor>(relaxed = true)
        every { db.query(any<SupportSQLiteQuery>()) } returns cursor
        every { cursor.moveToFirst() } returns false
        assertEquals(0, dao.count())
    }

    @Test
    fun `clearAll deletes fts table`() = runTest {
        val helper = mockk<SupportSQLiteOpenHelper>()
        val sqlite = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.openHelper } returns helper
        every { helper.writableDatabase } returns sqlite
        dao.clearAll()
        verify { sqlite.execSQL("DELETE FROM search_fts") }
    }

    @Test
    fun `insertAll no-op on empty`() = runTest {
        dao.insertAll(emptyList())
        verify(exactly = 0) { db.openHelper }
    }

    @Test
    fun `insertAll batches rows in transaction`() = runTest {
        val helper = mockk<SupportSQLiteOpenHelper>()
        val sqlite = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.openHelper } returns helper
        every { helper.writableDatabase } returns sqlite
        dao.insertAll(
            listOf(
                SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "a", body = "A"),
                SearchFtsEntity(entityType = SearchFtsTypes.ALBUM, entityId = "b", body = "B"),
            ),
        )
        verify { sqlite.beginTransaction() }
        verify { sqlite.setTransactionSuccessful() }
        verify { sqlite.endTransaction() }
        verify {
            sqlite.execSQL(
                match { it.startsWith("INSERT INTO search_fts") },
                any(),
            )
        }
        assertTrue(SearchFtsSchema.CREATE_FTS5.contains("FTS5"))
        assertEquals("1.0, 0.0, 10.0", SearchFtsSchema.BM25_WEIGHTS)
    }

    @Test
    fun `replaceAll deletes then inserts in one transaction`() = runTest {
        val helper = mockk<SupportSQLiteOpenHelper>()
        val sqlite = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.openHelper } returns helper
        every { helper.writableDatabase } returns sqlite
        dao.replaceAll(
            listOf(SearchFtsEntity(entityType = SearchFtsTypes.TRACK, entityId = "a", body = "A")),
        )
        verify { sqlite.execSQL("DELETE FROM search_fts") }
        verify {
            sqlite.execSQL(
                match { it.startsWith("INSERT INTO search_fts") },
                any(),
            )
        }
        verify { sqlite.setTransactionSuccessful() }
    }
}
