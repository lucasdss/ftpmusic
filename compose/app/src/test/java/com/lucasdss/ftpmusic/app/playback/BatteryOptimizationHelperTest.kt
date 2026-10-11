package com.lucasdss.ftpmusic.app.playback

import android.content.Context
import android.os.PowerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationHelperTest {

    @Test
    fun `isUnrestricted true when PowerManager says ignoring`() {
        val pm = mockk<PowerManager>()
        every { pm.isIgnoringBatteryOptimizations("com.lucasdss.ftpmusic.app") } returns true
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.packageName } returns "com.lucasdss.ftpmusic.app"
        every { ctx.getSystemService(PowerManager::class.java) } returns pm
        assertTrue(BatteryOptimizationHelper.isUnrestricted(ctx))
    }

    @Test
    fun `isUnrestricted false when optimized`() {
        val pm = mockk<PowerManager>()
        every { pm.isIgnoringBatteryOptimizations("com.lucasdss.ftpmusic.app") } returns false
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.packageName } returns "com.lucasdss.ftpmusic.app"
        every { ctx.getSystemService(PowerManager::class.java) } returns pm
        assertFalse(BatteryOptimizationHelper.isUnrestricted(ctx))
    }

    @Test
    fun `isUnrestricted true when PowerManager null`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.getSystemService(PowerManager::class.java) } returns null
        assertTrue(BatteryOptimizationHelper.isUnrestricted(ctx))
    }

    @Test
    fun `openBatterySettings starts intent`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.packageName } returns "com.lucasdss.ftpmusic.app"
        assertTrue(BatteryOptimizationHelper.openBatterySettings(ctx))
        verify(atLeast = 1) { ctx.startActivity(any()) }
    }

    @Test
    fun `openIgnoreBatteryOptimizationSettings false when intent fails`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.startActivity(any()) } throws RuntimeException("no settings")
        assertFalse(BatteryOptimizationHelper.openIgnoreBatteryOptimizationSettings(ctx))
    }

    @Test
    fun `openAppDetails starts package settings`() {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.packageName } returns "com.lucasdss.ftpmusic.app"
        assertTrue(BatteryOptimizationHelper.openAppDetails(ctx))
        verify(atLeast = 1) { ctx.startActivity(any()) }
    }
}
