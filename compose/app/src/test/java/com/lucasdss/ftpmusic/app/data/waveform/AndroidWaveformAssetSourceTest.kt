package com.lucasdss.ftpmusic.app.data.waveform

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Deterministic unit tests for the AssetManager-backed source (mockk). */
class AndroidWaveformAssetSourceTest {

    private val context: Context = mockk()
    private val assetManager: AssetManager = mockk()
    private val source = AndroidWaveformAssetSource(context)

    // ── list ────────────────────────────────────────────────────────────────

    @Test fun `list returns entries when dir exists`() {
        every { context.assets } returns assetManager
        every { assetManager.list("waveform") } returns arrayOf("Rock", "Blues")
        assertEquals(listOf("Rock", "Blues"), source.list("waveform"))
    }

    @Test fun `list returns null when dir empty`() {
        every { context.assets } returns assetManager
        every { assetManager.list("waveform") } returns arrayOf()
        assertNull(source.list("waveform"))
    }

    @Test fun `list returns null when dir missing`() {
        every { context.assets } returns assetManager
        every { assetManager.list("waveform") } returns null
        assertNull(source.list("waveform"))
    }

    @Test fun `list returns null on io error`() {
        every { context.assets } returns assetManager
        every { assetManager.list("waveform") } throws IOException("no")
        assertNull(source.list("waveform"))
    }

    // ── open ────────────────────────────────────────────────────────────────

    @Test fun `open returns stream when file exists`() {
        every { context.assets } returns assetManager
        every { assetManager.open("waveform/Rock/11922.json") } returns
            ByteArrayInputStream("[]".toByteArray())
        assertNotNull(source.open("waveform/Rock/11922.json"))
    }

    @Test fun `open returns null when file missing`() {
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } throws IOException("no")
        assertNull(source.open("waveform/Rock/nope.json"))
    }
}
