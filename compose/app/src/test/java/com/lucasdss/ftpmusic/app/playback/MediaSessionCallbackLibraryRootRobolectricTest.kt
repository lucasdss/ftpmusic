package com.lucasdss.ftpmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import com.lucasdss.ftpmusic.app.data.db.CachedMetadataDao
import com.lucasdss.ftpmusic.app.data.db.TrackDao
import com.lucasdss.ftpmusic.app.data.network.SubsonicApi
import com.lucasdss.ftpmusic.app.data.network.SubsonicAuthHelper
import com.lucasdss.ftpmusic.app.di.DynamicBaseUrl
import com.lucasdss.ftpmusic.app.di.SubsonicCredentials
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-0146: content-style extras need real Bundle (Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaSessionCallbackLibraryRootRobolectricTest {

    @Before
    fun setup() {
        SubsonicCredentials.username = "user"
        SubsonicCredentials.password = "pass"
        DynamicBaseUrl.url = "https://music.example.com"
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.toString() } returns (firstArg() as String)
            uri
        }
        every { Uri.encode(any()) } answers { firstArg() }
        every { Uri.decode(any()) } answers { firstArg() }
    }

    @After
    fun teardown() {
        unmockkStatic(Uri::class)
        SubsonicCredentials.username = ""
        SubsonicCredentials.password = ""
        DynamicBaseUrl.url = ""
    }

    private fun callback(): MediaSessionCallback = MediaSessionCallback(
        trackDao = mockk<TrackDao>(relaxed = true),
        metadataDao = mockk<CachedMetadataDao>(relaxed = true),
        authHelper = SubsonicAuthHelper(),
        api = mockk<SubsonicApi>(relaxed = true),
        playlistDao = mockk(relaxed = true),
        scope = CoroutineScope(Dispatchers.Unconfined),
        mainDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `onGetLibraryRoot returns list content-style extras`() {
        val result = callback().onGetLibraryRoot(
            mockk(relaxed = true),
            mockk(relaxed = true),
            null,
        ).get()
        assertEquals(AutoBrowseIds.ROOT, result.value?.mediaId)
        val extras = result.params?.extras
        assertNotNull(extras)
        assertEquals(
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            extras!!.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE),
        )
        assertEquals(
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE),
        )
    }

    @Test
    fun `onGetLibraryRoot preserves client offline flag and extras`() {
        val clientExtras = Bundle().apply { putString("client_key", "keep") }
        val client = MediaLibraryService.LibraryParams.Builder()
            .setOffline(true)
            .setExtras(clientExtras)
            .build()
        val result = callback().onGetLibraryRoot(
            mockk(relaxed = true),
            mockk(relaxed = true),
            client,
        ).get()
        val params = result.params
        assertNotNull(params)
        assertTrue(params!!.isOffline)
        assertEquals("keep", params.extras.getString("client_key"))
        assertEquals(
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            params.extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE),
        )
    }
}
