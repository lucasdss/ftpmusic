package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.DesiredItemDao
import com.lucasdss.ftpmusic.app.data.db.DesiredItemEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesiredRepositoryTest {

    private val desiredItemDao: DesiredItemDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private val metadataDao: CachedMetadataDao = mockk(relaxed = true)
    private lateinit var repo: DesiredRepository

    @Before
    fun setUp() {
        repo = DesiredRepository(desiredItemDao, trackDao, metadataDao)
    }

    @Test
    fun `identityKey prefers mbid over deezer over name`() {
        assertEquals(
            "mb:ARTIST:abc",
            DesiredRepository.identityKey("ARTIST", "X", null, "ABC", "9"),
        )
        assertEquals(
            "dz:ALBUM:42",
            DesiredRepository.identityKey("ALBUM", "X", "A", null, "42"),
        )
        assertEquals(
            "nm:TRACK:song|band",
            DesiredRepository.identityKey("TRACK", " Song ", "Band", null, null),
        )
    }

    @Test
    fun `addDesired inserts new row`() = runTest {
        coEvery { desiredItemDao.getByIdentityKey(any()) } returns null
        val captured = slot<DesiredItemEntity>()
        coEvery { desiredItemDao.upsert(capture(captured)) } returns Unit

        val result = repo.addDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            musicbrainzId = "mbid-1",
            source = "musicbrainz",
        )

        assertEquals("mb:ARTIST:mbid-1", result.identityKey)
        assertEquals("Ext Artist", captured.captured.title)
        coVerify(exactly = 1) { desiredItemDao.upsert(any()) }
    }

    @Test
    fun `addDesired is idempotent for same identity`() = runTest {
        val existing = DesiredItemEntity(
            id = "id-1",
            identityKey = "mb:ARTIST:mbid-1",
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            musicbrainzId = "mbid-1",
            source = "musicbrainz",
        )
        coEvery { desiredItemDao.getByIdentityKey("mb:ARTIST:mbid-1") } returns existing

        val result = repo.addDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            musicbrainzId = "mbid-1",
            deezerId = "99",
            source = "deezer",
        )

        assertEquals("id-1", result.id)
        assertEquals("99", result.deezerId)
        coVerify { desiredItemDao.upsert(match { it.id == "id-1" && it.deezerId == "99" }) }
    }

    @Test
    fun `snapshotShareText includes header and rows`() = runTest {
        coEvery { desiredItemDao.getAll() } returns listOf(
            DesiredItemEntity(
                id = "1",
                identityKey = "mb:TRACK:t1",
                entityType = DesiredRepository.TYPE_TRACK,
                title = "Song",
                artistName = "Band",
                musicbrainzId = "t1",
                source = "musicbrainz",
            ),
        )
        val text = repo.snapshotShareText()
        assertTrue(text.startsWith("ftpmusic desired list"))
        assertTrue(text.contains("count=1"))
        assertTrue(text.contains("[TRACK] Song — Band"))
    }

    @Test
    fun `reconcileAgainstLibrary sets matched local id`() = runTest {
        val item = DesiredItemEntity(
            id = "1",
            identityKey = "mb:ARTIST:mbid-1",
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Local Artist",
            musicbrainzId = "mbid-1",
            source = "musicbrainz",
        )
        coEvery { desiredItemDao.getAll() } returns listOf(item)
        coEvery { metadataDao.getArtistByMbid("mbid-1") } returns
            CachedArtistEntity(id = "ar-1", name = "Local Artist", musicbrainzId = "mbid-1")

        repo.reconcileAgainstLibrary()

        coVerify { desiredItemDao.setMatchedLocalId("1", "ar-1") }
    }
}
