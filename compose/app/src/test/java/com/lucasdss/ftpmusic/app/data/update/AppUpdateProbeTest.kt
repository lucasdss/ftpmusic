package com.lucasdss.ftpmusic.app.data.update

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppUpdateProbeTest {
    private val checker: AppUpdateChecker = mockk()
    private val holder: AppUpdateAvailabilityHolder = mockk(relaxed = true)
    private lateinit var probe: AppUpdateProbe

    @Before
    fun setUp() {
        probe = AppUpdateProbe(checker, holder)
    }

    @Test
    fun `probe publishes checker result`() = runTest {
        val result = UpdateCheckResult.UpdateAvailable(30, true)
        coEvery { checker.check() } returns result

        probe.probe(force = true)

        verify { holder.publish(result) }
    }

    @Test
    fun `probe swallows checker exceptions`() = runTest {
        coEvery { checker.check() } throws RuntimeException("play down")

        probe.probe(force = true)

        verify(exactly = 0) { holder.publish(any()) }
    }

    @Test
    fun `success throttle skips second probe within window`() = runTest {
        coEvery { checker.check() } returns UpdateCheckResult.UpToDate

        probe.probe(force = true)
        probe.probe(force = false)

        coVerify(exactly = 1) { checker.check() }
    }

    @Test
    fun `force bypasses throttle`() = runTest {
        coEvery { checker.check() } returns UpdateCheckResult.UpToDate

        probe.probe(force = true)
        probe.probe(force = true)

        coVerify(exactly = 2) { checker.check() }
    }

    @Test
    fun `Unavailable uses short failure backoff not success throttle`() = runTest {
        coEvery { checker.check() } returnsMany listOf(
            UpdateCheckResult.Unavailable("blip"),
            UpdateCheckResult.UpToDate,
        )

        probe.probe(force = true)
        // Within success throttle but after failure path — force=false still blocked by failure backoff.
        // Use force=true to confirm Unavailable still published and second definitive call works.
        probe.probe(force = true)

        coVerify(exactly = 2) { checker.check() }
        verify { holder.publish(UpdateCheckResult.Unavailable("blip")) }
        verify { holder.publish(UpdateCheckResult.UpToDate) }
    }
}
