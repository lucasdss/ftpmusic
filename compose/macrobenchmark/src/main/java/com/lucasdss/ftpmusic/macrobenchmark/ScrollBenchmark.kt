package com.lucasdss.ftpmusic.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release scroll frame timing for Home / Library (ADR-0107 Pass 4b / hard-fix).
 * Run on a connected device: `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeFling() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.res(PACKAGE, "home_scroll")), 15_000)
        val list = requireScrollNode(By.res(PACKAGE, "home_scroll"), "home_scroll")
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
    }

    @Test
    fun libraryAlbumsFling() = benchmarkRule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Library")), 10_000)
        val libraryTab = device.findObject(By.text("Library"))
        assertNotNull("Library tab not found", libraryTab)
        libraryTab.click()
        device.wait(Until.hasObject(By.res(PACKAGE, "library_albums_grid")), 15_000)
        val grid = requireScrollNode(By.res(PACKAGE, "library_albums_grid"), "library_albums_grid")
        grid.setGestureMargin(device.displayWidth / 5)
        grid.fling(Direction.DOWN)
        device.waitForIdle()
        grid.fling(Direction.UP)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.requireScrollNode(
        selector: androidx.test.uiautomator.BySelector,
        tag: String,
    ): UiObject2 {
        val node = device.findObject(selector)
        assertNotNull("Scroll node missing: $tag (login / seeded library required)", node)
        return node
    }

    companion object {
        private const val PACKAGE = "com.lucasdss.ftpmusic.app"
    }
}
