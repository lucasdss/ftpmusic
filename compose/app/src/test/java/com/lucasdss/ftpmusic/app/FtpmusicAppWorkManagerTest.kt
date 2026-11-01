package com.lucasdss.ftpmusic.app

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the 2.0.0 P0 (see
 * docs/FTPMUSIC_DIAGNOSTICS_20261031_SYNC_PLAYBACK_BEHAVIOR_REPORT.md F1):
 * `@HiltWorker` classes were scheduled but the app never installed a
 * `HiltWorkerFactory`, so `DefaultWorkerFactory` failed to instantiate them and
 * `doWork()` never ran. Construction-based worker tests cannot catch this — they
 * bypass the factory entirely.
 */
class FtpmusicAppWorkManagerTest {

    @Test
    fun `app supplies a hilt worker factory through Configuration Provider`() {
        assertTrue(
            "FtpmusicApp must implement androidx.work.Configuration.Provider",
            Configuration.Provider::class.java.isAssignableFrom(FtpmusicApp::class.java),
        )
        assertTrue(
            "FtpmusicApp must expose an injected HiltWorkerFactory",
            FtpmusicApp::class.java.declaredFields.any {
                HiltWorkerFactory::class.java.isAssignableFrom(it.type)
            },
        )
    }
}
