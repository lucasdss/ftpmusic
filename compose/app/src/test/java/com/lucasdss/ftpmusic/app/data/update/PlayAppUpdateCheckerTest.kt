package com.lucasdss.ftpmusic.app.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.lucasdss.ftpmusic.app.BuildConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayAppUpdateCheckerTest {
    private val appUpdateManager: AppUpdateManager = mockk(relaxed = true)
    private lateinit var checker: PlayAppUpdateChecker

    @Before
    fun setUp() {
        checker = PlayAppUpdateChecker(appUpdateManager)
    }

    @Test
    fun `applicationId matches BuildConfig`() {
        assertEquals(BuildConfig.APPLICATION_ID, checker.applicationId())
    }

    @Test
    fun `check success maps UPDATE_AVAILABLE`() = runTest {
        val info = mockInfo(
            availability = UpdateAvailability.UPDATE_AVAILABLE,
            versionCode = 21,
            flexible = true,
        )
        stubAppUpdateInfoSuccess(info)

        val result = checker.check()

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        result as UpdateCheckResult.UpdateAvailable
        assertEquals(21, result.availableVersionCode)
        assertTrue(result.flexibleAllowed)
    }

    @Test
    fun `check maps IN_PROGRESS to InProgress`() = runTest {
        val info = mockInfo(
            availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            versionCode = 22,
            flexible = true,
            installStatus = InstallStatus.DOWNLOADING,
        )
        stubAppUpdateInfoSuccess(info)

        val result = checker.check() as UpdateCheckResult.InProgress
        assertEquals(22, result.availableVersionCode)
    }

    @Test
    fun `check failure maps to Unavailable`() = runTest {
        stubAppUpdateInfoFailure(RuntimeException("offline"))

        val result = checker.check()

        assertEquals(UpdateCheckResult.Unavailable("offline"), result)
    }

    @Test
    fun `check failure blank message uses default`() = runTest {
        stubAppUpdateInfoFailure(RuntimeException("   "))

        val result = checker.check() as UpdateCheckResult.Unavailable

        assertEquals("Unable to check Play Store for updates", result.message)
    }

    @Test
    fun `startFlexibleUpdate false without prior check`() {
        assertFalse(
            checker.startFlexibleUpdate(mockk(relaxed = true), mockk(relaxed = true)),
        )
    }

    @Test
    fun `startFlexibleUpdate false when flexible not allowed`() = runTest {
        val info = mockInfo(
            availability = UpdateAvailability.UPDATE_AVAILABLE,
            versionCode = 21,
            flexible = false,
        )
        stubAppUpdateInfoSuccess(info)
        checker.check()

        assertFalse(
            checker.startFlexibleUpdate(mockk(relaxed = true), mockk(relaxed = true)),
        )
    }

    @Test
    fun `startFlexibleUpdate true when flexible allowed`() = runTest {
        val info = mockInfo(
            availability = UpdateAvailability.UPDATE_AVAILABLE,
            versionCode = 21,
            flexible = true,
        )
        stubAppUpdateInfoSuccess(info)
        checker.check()

        every {
            appUpdateManager.startUpdateFlowForResult(any(), any<ActivityResultLauncher<IntentSenderRequest>>(), any())
        } returns true

        val activity = mockk<Activity>(relaxed = true)
        val launcher = mockk<ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
        assertTrue(checker.startFlexibleUpdate(activity, launcher))
        verify {
            appUpdateManager.startUpdateFlowForResult(info, launcher, any())
        }
    }

    @Test
    fun `startFlexibleUpdate false when Play throws`() = runTest {
        val info = mockInfo(
            availability = UpdateAvailability.UPDATE_AVAILABLE,
            versionCode = 21,
            flexible = true,
        )
        stubAppUpdateInfoSuccess(info)
        checker.check()

        every {
            appUpdateManager.startUpdateFlowForResult(any(), any<ActivityResultLauncher<IntentSenderRequest>>(), any())
        } throws IllegalStateException("no play")

        assertFalse(
            checker.startFlexibleUpdate(mockk(relaxed = true), mockk(relaxed = true)),
        )
    }

    @Test
    fun `completeUpdate returns true when Play accepts`() {
        every { appUpdateManager.completeUpdate() } returns mockk(relaxed = true)
        assertTrue(checker.completeUpdate())
        verify { appUpdateManager.completeUpdate() }
    }

    @Test
    fun `completeUpdate returns false when Play throws`() {
        every { appUpdateManager.completeUpdate() } throws IllegalStateException("no")
        assertFalse(checker.completeUpdate())
    }

    @Test
    fun `observeFlexibleInstall emits Downloaded from listener`() = runTest(UnconfinedTestDispatcher()) {
        val listenerSlot = slot<InstallStateUpdatedListener>()
        every { appUpdateManager.registerListener(capture(listenerSlot)) } returns Unit
        every { appUpdateManager.unregisterListener(any()) } returns Unit

        val events = mutableListOf<FlexibleInstallEvent>()
        val job = launch {
            checker.observeFlexibleInstall().collect { events.add(it) }
        }

        val state = mockk<InstallState>()
        every { state.installStatus() } returns InstallStatus.DOWNLOADED
        listenerSlot.captured.onStateUpdate(state)

        assertEquals(listOf(FlexibleInstallEvent.Downloaded), events)
        job.cancel()
        verify { appUpdateManager.unregisterListener(listenerSlot.captured) }
    }

    @Test
    fun `observeFlexibleInstall emits Downloaded from cached DOWNLOADED info`() = runTest {
        val info = mockInfo(
            availability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            versionCode = 30,
            flexible = true,
            installStatus = InstallStatus.DOWNLOADED,
        )
        stubAppUpdateInfoSuccess(info)
        checker.check()

        every { appUpdateManager.registerListener(any()) } returns Unit
        every { appUpdateManager.unregisterListener(any()) } returns Unit

        val first = checker.observeFlexibleInstall().first()
        assertEquals(FlexibleInstallEvent.Downloaded, first)
    }

    private fun mockInfo(
        availability: Int,
        versionCode: Int,
        flexible: Boolean,
        installStatus: Int = InstallStatus.UNKNOWN,
    ): AppUpdateInfo {
        val info = mockk<AppUpdateInfo>()
        every { info.updateAvailability() } returns availability
        every { info.availableVersionCode() } returns versionCode
        every { info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) } returns flexible
        every { info.installStatus() } returns installStatus
        return info
    }

    private fun stubAppUpdateInfoSuccess(info: AppUpdateInfo) {
        val task = mockk<Task<AppUpdateInfo>>()
        val success = slot<OnSuccessListener<AppUpdateInfo>>()
        every { appUpdateManager.appUpdateInfo } returns task
        every { task.addOnSuccessListener(capture(success)) } answers {
            success.captured.onSuccess(info)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
    }

    private fun stubAppUpdateInfoFailure(error: Exception) {
        val task = mockk<Task<AppUpdateInfo>>()
        val failure = slot<OnFailureListener>()
        every { appUpdateManager.appUpdateInfo } returns task
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(capture(failure)) } answers {
            failure.captured.onFailure(error)
            task
        }
    }
}
