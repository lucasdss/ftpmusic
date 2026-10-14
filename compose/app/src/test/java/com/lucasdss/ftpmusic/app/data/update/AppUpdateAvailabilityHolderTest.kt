package com.lucasdss.ftpmusic.app.data.update

import android.content.Context
import android.content.SharedPreferences
import com.lucasdss.ftpmusic.app.data.db.MetadataSyncWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AppUpdateAvailabilityHolderTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var holder: AppUpdateAvailabilityHolder

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { prefs.getInt(AppUpdateAvailabilityHolder.PREF_DISMISSED_UPDATE_VERSION_CODE, 0) } returns 0
        val context = mockk<Context>(relaxed = true)
        every {
            context.getSharedPreferences(MetadataSyncWorker.PREFS_NAME, Context.MODE_PRIVATE)
        } returns prefs
        holder = AppUpdateAvailabilityHolder(context)
    }

    @Test
    fun `publish UpdateAvailable emits when not dismissed`() {
        holder.publish(UpdateCheckResult.UpdateAvailable(22, flexibleAllowed = true))
        assertEquals(AvailableUpdate(22, true), holder.available.value)
    }

    @Test
    fun `publish InProgress leaves tray unchanged`() {
        holder.publish(UpdateCheckResult.UpdateAvailable(22, true))
        holder.publish(UpdateCheckResult.InProgress(23))
        assertEquals(AvailableUpdate(22, true), holder.available.value)
    }

    @Test
    fun `publish UpToDate clears`() {
        holder.publish(UpdateCheckResult.UpdateAvailable(22, true))
        holder.publish(UpdateCheckResult.UpToDate)
        assertNull(holder.available.value)
    }

    @Test
    fun `publish Unavailable keeps last confirmed`() {
        holder.publish(UpdateCheckResult.UpdateAvailable(22, true))
        holder.publish(UpdateCheckResult.Unavailable("no play"))
        assertEquals(AvailableUpdate(22, true), holder.available.value)
    }

    @Test
    fun `dismissed version hides same code`() {
        every { prefs.getInt(AppUpdateAvailabilityHolder.PREF_DISMISSED_UPDATE_VERSION_CODE, 0) } returns 22
        holder.publish(UpdateCheckResult.UpdateAvailable(22, true))
        assertNull(holder.available.value)
    }

    @Test
    fun `higher version after dismiss shows again`() {
        every { prefs.getInt(AppUpdateAvailabilityHolder.PREF_DISMISSED_UPDATE_VERSION_CODE, 0) } returns 22
        holder.publish(UpdateCheckResult.UpdateAvailable(23, true))
        assertEquals(AvailableUpdate(23, true), holder.available.value)
    }

    @Test
    fun `dismiss writes prefs and clears matching`() {
        holder.publish(UpdateCheckResult.UpdateAvailable(22, true))
        holder.dismiss(22)
        verify { editor.putInt(AppUpdateAvailabilityHolder.PREF_DISMISSED_UPDATE_VERSION_CODE, 22) }
        assertNull(holder.available.value)
    }
}
