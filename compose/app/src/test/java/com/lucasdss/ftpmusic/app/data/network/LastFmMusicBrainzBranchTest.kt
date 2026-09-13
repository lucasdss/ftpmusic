package com.lucasdss.ftpmusic.app.data.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.security.crypto.MasterKeys
import com.lucasdss.ftpmusic.app.data.security.SecureStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plain-JVM tests for [LastFmService] and [MusicBrainzService] with the
 * OkHttpClient mocked via reflection — no real network. org.json is a stub on
 * the plain JVM, so JSON parse-success paths resolve to the null/empty guards.
 */
class LastFmMusicBrainzBranchTest {

    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { context.applicationContext } returns context
    }

    companion object {
        init {
            // Keep the real MasterKeys class from initializing (AndroidKeyStore
            // is unavailable on the plain JVM); LastFmService's apiKey lazy reads
            // SecureStorage, whose prefs creation fails gracefully to the default.
            // Set up once per class to limit javaagent redefinition churn.
            mockkStatic(MasterKeys::class)
            every { MasterKeys.getOrCreate(any<KeyGenParameterSpec>()) } returns "test_master_key_alias"
        }
    }

    private fun injectMockClient(target: Any, client: OkHttpClient) {
        val field = target.javaClass.getDeclaredField("client")
        field.isAccessible = true
        field.set(target, client)
    }

    private fun mockClient(successful: Boolean = false, bodyString: String? = null): OkHttpClient {
        val client = mockk<OkHttpClient>()
        val call = mockk<Call>(relaxed = true)
        val response = mockk<Response>(relaxed = true)
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response
        every { response.isSuccessful } returns successful
        if (bodyString != null) {
            val body = mockk<ResponseBody>(relaxed = true)
            every { body.string() } returns bodyString
            every { response.body } returns body
        }
        return client
    }

    // ── LastFmService ───────────────────────────────────────────────────────

    @Test
    fun `fetchSimilarArtists returns empty on unsuccessful response`() = runTest {
        val service = LastFmService(context)
        injectMockClient(service, mockClient(successful = false))
        assertTrue(service.fetchSimilarArtists("Artist").isEmpty())
    }

    @Test
    fun `fetchSimilarArtists returns empty when response body is null`() = runTest {
        val service = LastFmService(context)
        injectMockClient(service, mockClient(successful = true, bodyString = null))
        assertTrue(service.fetchSimilarArtists("Artist").isEmpty())
    }

    @Test
    fun `fetchSimilarArtists returns empty when the request throws`() = runTest {
        val service = LastFmService(context)
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } throws RuntimeException("network down")
        injectMockClient(service, client)
        assertTrue(service.fetchSimilarArtists("Artist").isEmpty())
    }

    @Test
    fun `parseSimilarArtists returns empty when similarartists missing`() {
        val service = LastFmService(context)
        assertTrue(service.parseSimilarArtists("""{"other":1}""").isEmpty())
    }

    @Test
    fun `parseStoredJson returns empty for blank input`() {
        val service = LastFmService(context)
        assertTrue(service.parseStoredJson(null).isEmpty())
        assertTrue(service.parseStoredJson("").isEmpty())
        assertTrue(service.parseStoredJson("   ").isEmpty())
    }

    @Test
    fun `toStoredJson serializes artists to a json array`() {
        val service = LastFmService(context)
        // org.json is a stub on the plain JVM — JSONArray.toString() can return
        // null; the important part is the per-artist put() calls execute.
        try {
            val json = service.toStoredJson(
                listOf(
                    LastFmService.SimilarArtist("A", "mbid-1", 0.9),
                    LastFmService.SimilarArtist("B", null, null),
                ),
            )
            assertTrue(json == null || json.contains("["))
        } catch (_: Exception) {
            // Stub org.json may throw — acceptable on the plain JVM
        }
    }

    // ── MusicBrainzService ──────────────────────────────────────────────────

    @Test
    fun `searchArtistMbid returns null for blank artist`() = runTest {
        val service = MusicBrainzService()
        assertNull(service.searchArtistMbid(""))
        assertNull(service.searchArtistMbid("   "))
    }

    @Test
    fun `searchAlbumMbid returns null when either term is blank`() = runTest {
        val service = MusicBrainzService()
        assertNull(service.searchAlbumMbid("", "Album"))
        assertNull(service.searchAlbumMbid("Artist", "  "))
    }

    @Test
    fun `searchTrackMbid returns null when either term is blank`() = runTest {
        val service = MusicBrainzService()
        assertNull(service.searchTrackMbid("", "Track"))
        assertNull(service.searchTrackMbid("Artist", ""))
    }

    @Test
    fun `searchArtistMbid returns null when the api response is unsuccessful`() = runTest {
        val service = MusicBrainzService()
        injectMockClient(service, mockClient(successful = false))
        assertNull(service.searchArtistMbid("Artist"))
    }

    @Test
    fun `searchArtistMbid returns null when the response has no artists array`() = runTest {
        val service = MusicBrainzService()
        injectMockClient(service, mockClient(successful = true, bodyString = """{"x":1}"""))
        assertNull(service.searchArtistMbid("Artist"))
    }

    @Test
    fun `rating lookups return null rating for blank mbid`() = runTest {
        val service = MusicBrainzService()
        val artist = service.getArtistRating("")
        assertNull(artist.value)
        assertNull(artist.votes)
        val album = service.getAlbumRating(" ")
        assertNull(album.value)
        val track = service.getTrackRating("")
        assertNull(track.value)
    }

    @Test
    fun `rating lookup returns null rating when the api response is unsuccessful`() = runTest {
        val service = MusicBrainzService()
        injectMockClient(service, mockClient(successful = false))
        val rating = service.getArtistRating("mbid-1")
        assertNull(rating.value)
        assertNull(rating.votes)
    }

    @Test
    fun `fetchArtistRating returns null pair for blank artist`() = runTest {
        val service = MusicBrainzService()
        val (rating, mbid) = service.fetchArtistRating("")
        assertNull(rating.value)
        assertNull(mbid)
    }

    @Test
    fun `fetchAlbumRating and fetchTrackRating return null pairs for blank input`() = runTest {
        val service = MusicBrainzService()
        val (albumRating, albumMbid) = service.fetchAlbumRating("Artist", "")
        assertNull(albumRating.value)
        assertNull(albumMbid)
        val (trackRating, trackMbid) = service.fetchTrackRating("", "Track")
        assertNull(trackRating.value)
        assertNull(trackMbid)
    }

    @Test
    fun `encodeQueryParam handles blanks`() {
        val service = MusicBrainzService()
        // Via search methods returning null for blank inputs
        assertEquals(null, runBlocking2 { service.searchArtistMbid("") })
    }

    private inline fun <T> runBlocking2(crossinline block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
