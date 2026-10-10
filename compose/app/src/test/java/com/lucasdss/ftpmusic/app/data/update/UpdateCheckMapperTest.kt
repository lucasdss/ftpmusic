package com.lucasdss.ftpmusic.app.data.update

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckMapperTest {

    @Test
    fun `UPDATE_AVAILABLE maps to UpdateAvailable with flexible flag`() {
        val info = mockk<AppUpdateInfo>()
        every { info.updateAvailability() } returns UpdateAvailability.UPDATE_AVAILABLE
        every { info.availableVersionCode() } returns 42
        every { info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) } returns true

        val result = UpdateCheckMapper.fromPlayInfo(info)

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        result as UpdateCheckResult.UpdateAvailable
        assertEquals(42, result.availableVersionCode)
        assertTrue(result.flexibleAllowed)
    }

    @Test
    fun `UPDATE_AVAILABLE without flexible sets flexibleAllowed false`() {
        val info = mockk<AppUpdateInfo>()
        every { info.updateAvailability() } returns UpdateAvailability.UPDATE_AVAILABLE
        every { info.availableVersionCode() } returns 99
        every { info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) } returns false

        val result = UpdateCheckMapper.fromPlayInfo(info) as UpdateCheckResult.UpdateAvailable

        assertEquals(99, result.availableVersionCode)
        assertEquals(false, result.flexibleAllowed)
    }

    @Test
    fun `UPDATE_NOT_AVAILABLE maps to UpToDate`() {
        val info = mockk<AppUpdateInfo>()
        every { info.updateAvailability() } returns UpdateAvailability.UPDATE_NOT_AVAILABLE

        assertEquals(UpdateCheckResult.UpToDate, UpdateCheckMapper.fromPlayInfo(info))
    }

    @Test
    fun `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS maps to UpdateAvailable`() {
        val info = mockk<AppUpdateInfo>()
        every { info.updateAvailability() } returns
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        every { info.availableVersionCode() } returns 15
        every { info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) } returns true

        val result = UpdateCheckMapper.fromPlayInfo(info) as UpdateCheckResult.UpdateAvailable
        assertEquals(15, result.availableVersionCode)
        assertTrue(result.flexibleAllowed)
    }

    @Test
    fun `unknown availability maps to Unavailable`() {
        val info = mockk<AppUpdateInfo>()
        every { info.updateAvailability() } returns UpdateAvailability.UNKNOWN

        val result = UpdateCheckMapper.fromPlayInfo(info)
        assertTrue(result is UpdateCheckResult.Unavailable)
    }
}
