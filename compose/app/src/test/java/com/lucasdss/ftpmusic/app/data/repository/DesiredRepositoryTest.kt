package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.CachedArtistEntity
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.DesiredItemDao
import com.lucasdss.ftpmusic.app.data.db.DesiredItemEntity
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.db.TrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        coEvery { desiredItemDao.getByMusicbrainzId(any(), any()) } returns null
        coEvery { desiredItemDao.getByDeezerId(any(), any()) } returns null
        coEvery { desiredItemDao.getByIdentityKey(any()) } returns null
        coEvery { desiredItemDao.getById(any()) } returns null
        coEvery { desiredItemDao.getByNormalizedTitleArtist(any(), any(), any()) } returns emptyList()
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
    fun `addDesired inserts new row via mergeReplace`() = runTest {
        val captured = slot<DesiredItemEntity>()
        coEvery { desiredItemDao.mergeReplace(capture(captured), emptyList()) } returns Unit

        val result = repo.addDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            musicbrainzId = "mbid-1",
            source = "musicbrainz",
        )

        assertEquals("mb:ARTIST:mbid-1", result.identityKey)
        assertEquals("Ext Artist", captured.captured.title)
        coVerify(exactly = 1) { desiredItemDao.mergeReplace(any(), emptyList()) }
    }

    @Test
    fun `addDesired merges name row into mbid`() = runTest {
        val nameRow = DesiredItemEntity(
            id = "nm-1",
            identityKey = "nm:ARTIST:ext artist|",
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            source = "manual",
            desiredAt = 100L,
            matchedLocalId = "local-ar",
        )
        coEvery {
            desiredItemDao.getByIdentityKey("nm:ARTIST:ext artist|")
        } returns nameRow
        coEvery { desiredItemDao.getById("nm-1") } answers {
            DesiredItemEntity(
                id = "nm-1",
                identityKey = "mb:ARTIST:mbid-1",
                entityType = DesiredRepository.TYPE_ARTIST,
                title = "Ext Artist",
                musicbrainzId = "mbid-1",
                source = "musicbrainz",
                desiredAt = 100L,
                matchedLocalId = "local-ar",
            )
        }

        val result = repo.addDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            musicbrainzId = "mbid-1",
            source = "musicbrainz",
        )

        assertEquals("nm-1", result.id)
        assertEquals("mb:ARTIST:mbid-1", result.identityKey)
        assertEquals("local-ar", result.matchedLocalId)
        coVerify {
            desiredItemDao.mergeReplace(
                match { it.id == "nm-1" && it.identityKey.startsWith("mb:") && it.matchedLocalId == "local-ar" },
                emptyList(),
            )
        }
    }

    @Test
    fun `addDesired merges deezer into mbid and deletes loser`() = runTest {
        val dzRow = DesiredItemEntity(
            id = "dz-1",
            identityKey = "dz:ARTIST:99",
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            deezerId = "99",
            source = "deezer",
            desiredAt = 50L,
        )
        coEvery { desiredItemDao.getByDeezerId("ARTIST", "99") } returns dzRow
        coEvery { desiredItemDao.getByIdentityKey("dz:ARTIST:99") } returns dzRow
        coEvery { desiredItemDao.getById("dz-1") } answers {
            dzRow.copy(identityKey = "mb:ARTIST:mbid-1", musicbrainzId = "mbid-1")
        }

        val result = repo.addDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            musicbrainzId = "mbid-1",
            deezerId = "99",
            source = "musicbrainz",
        )

        assertEquals("dz-1", result.id)
        assertEquals("mbid-1", result.musicbrainzId)
        assertEquals("99", result.deezerId)
        coVerify {
            desiredItemDao.mergeReplace(
                match { it.id == "dz-1" && it.identityKey == "mb:ARTIST:mbid-1" },
                emptyList(),
            )
        }
    }

    @Test
    fun `addDesired preserves matched_local_id on metadata upsert`() = runTest {
        val existing = DesiredItemEntity(
            id = "id-1",
            identityKey = "mb:ARTIST:mbid-1",
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Ext Artist",
            musicbrainzId = "mbid-1",
            source = "musicbrainz",
            matchedLocalId = "keep-me",
            desiredAt = 10L,
        )
        coEvery { desiredItemDao.getByMusicbrainzId("ARTIST", "mbid-1") } returns existing
        coEvery { desiredItemDao.getByIdentityKey("mb:ARTIST:mbid-1") } returns existing
        coEvery { desiredItemDao.getById("id-1") } returns existing.copy(title = "Renamed")

        val result = repo.addDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Renamed",
            musicbrainzId = "mbid-1",
            source = "manual",
        )

        assertEquals("keep-me", result.matchedLocalId)
        coVerify {
            desiredItemDao.mergeReplace(
                match { it.matchedLocalId == "keep-me" && it.title == "Renamed" },
                emptyList(),
            )
        }
    }

    @Test
    fun `findMatchingDesired resolves deezer key`() = runTest {
        val dz = DesiredItemEntity(
            id = "1",
            identityKey = "dz:ARTIST:27",
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Daft Punk",
            deezerId = "27",
            source = "deezer",
        )
        coEvery { desiredItemDao.getByDeezerId("ARTIST", "27") } returns dz

        val found = repo.findMatchingDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Daft Punk",
            deezerId = "27",
        )
        assertNotNull(found)
        assertEquals("1", found!!.id)
    }

    @Test
    fun `findMatchingDesired bridges deezer row by title when id unknown`() = runTest {
        val dz = DesiredItemEntity(
            id = "1",
            identityKey = "dz:ARTIST:27",
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Daft Punk",
            deezerId = "27",
            source = "deezer",
        )
        coEvery {
            desiredItemDao.getByNormalizedTitleArtist("ARTIST", "Daft Punk", "")
        } returns listOf(dz)

        val found = repo.findMatchingDesired(
            entityType = DesiredRepository.TYPE_ARTIST,
            title = "Daft Punk",
        )
        assertNotNull(found)
        assertEquals("dz:ARTIST:27", found!!.identityKey)
    }

    @Test
    fun `reconcileAgainstLibrary track falls back to exact title artist`() = runTest {
        val item = DesiredItemEntity(
            id = "1",
            identityKey = "nm:TRACK:song|band",
            entityType = DesiredRepository.TYPE_TRACK,
            title = "Song",
            artistName = "Band",
            source = "manual",
        )
        coEvery { desiredItemDao.getAll() } returns listOf(item)
        coEvery { trackDao.getTrackByExactTitleArtist("Song", "Band") } returns
            TrackEntity(id = "tr-1", title = "Song", artist = "Band")

        repo.reconcileAgainstLibrary()

        coVerify { desiredItemDao.setMatchedLocalId("1", "tr-1") }
    }

    @Test
    fun `reconcileAgainstLibrary album skips name match when artist blank`() = runTest {
        val item = DesiredItemEntity(
            id = "1",
            identityKey = "nm:ALBUM:alone|",
            entityType = DesiredRepository.TYPE_ALBUM,
            title = "Alone",
            artistName = null,
            source = "manual",
        )
        coEvery { desiredItemDao.getAll() } returns listOf(item)

        repo.reconcileAgainstLibrary()

        coVerify(exactly = 0) { metadataDao.getAlbumByExactName(any(), any()) }
        coVerify(exactly = 0) { desiredItemDao.setMatchedLocalId(any(), any()) }
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

    @Test
    fun `removeMatching deletes by findMatchingDesired`() = runTest {
        val dz = DesiredItemEntity(
            id = "dz-row",
            identityKey = "dz:ALBUM:5",
            entityType = DesiredRepository.TYPE_ALBUM,
            title = "Discovery",
            artistName = "Daft Punk",
            deezerId = "5",
            source = "deezer",
        )
        coEvery { desiredItemDao.getByDeezerId("ALBUM", "5") } returns dz

        repo.removeMatching(
            entityType = DesiredRepository.TYPE_ALBUM,
            title = "Discovery",
            artistName = "Daft Punk",
            deezerId = "5",
        )

        coVerify { desiredItemDao.deleteById("dz-row") }
    }
}
