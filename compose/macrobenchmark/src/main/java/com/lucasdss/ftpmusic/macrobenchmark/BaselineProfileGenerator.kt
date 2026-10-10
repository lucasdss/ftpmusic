package com.lucasdss.ftpmusic.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Baseline Profile covering Home + Library album flings.
 * `./gradlew :app:generateBaselineProfile` (connected device required).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun homeAndLibraryScroll() {
        baselineProfileRule.collect(
            packageName = "com.lucasdss.ftpmusic.app",
            includeInStartupProfile = true,
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(By.res(packageName, "home_scroll")), 15_000)
            device.findObject(By.res(packageName, "home_scroll"))?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                list.fling(Direction.DOWN)
                device.waitForIdle()
                list.fling(Direction.UP)
            }
            device.findObject(By.text("Library"))?.click()
            device.wait(Until.hasObject(By.res(packageName, "library_albums_grid")), 15_000)
            device.findObject(By.res(packageName, "library_albums_grid"))?.let { grid ->
                grid.setGestureMargin(device.displayWidth / 5)
                grid.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }
    }
}
