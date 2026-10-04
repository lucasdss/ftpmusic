package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMusicRoleHelperTest {

    @Test
    fun `isRoleAvailable always true isRoleHeld always false`() {
        val ctx = mockk<Context>(relaxed = true)
        assertTrue(DefaultMusicRoleHelper.isRoleAvailable(ctx))
        assertFalse(DefaultMusicRoleHelper.isRoleHeld(ctx))
    }

    @Test
    fun `openDefaultAppsSettings starts intent`() {
        val ctx = mockk<Context>(relaxed = true)
        assertTrue(DefaultMusicRoleHelper.openDefaultAppsSettings(ctx))
        verify(atLeast = 1) { ctx.startActivity(any()) }
    }

    @Test
    fun `openDefaultAppsSettings false when all intents fail`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.startActivity(any()) } throws RuntimeException("no settings")
        every { ctx.packageName } returns "com.lucasdss.ftpmusic.app"
        assertFalse(DefaultMusicRoleHelper.openDefaultAppsSettings(ctx))
    }

    @Test
    fun `openAppDetails starts package settings`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.packageName } returns "com.lucasdss.ftpmusic.app"
        assertTrue(DefaultMusicRoleHelper.openAppDetails(ctx))
        verify(atLeast = 1) { ctx.startActivity(any()) }
    }
}
