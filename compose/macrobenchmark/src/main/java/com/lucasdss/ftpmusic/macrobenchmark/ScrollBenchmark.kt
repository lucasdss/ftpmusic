package com.lucasdss.ftpmusic.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release scroll frame timing for Home / Library (ADR-0107 Pass 4b).
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
        val list = device.findObject(By.res(PACKAGE, "home_scroll")) ?: return@measureRepeated
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
        // Bottom nav "Library" — label text may vary; try content-desc / text.
        device.wait(Until.hasObject(By.text("Library")), 10_000)
        device.findObject(By.text("Library"))?.click()
        device.wait(Until.hasObject(By.res(PACKAGE, "library_albums_grid")), 15_000)
        val grid = device.findObject(By.res(PACKAGE, "library_albums_grid")) ?: return@measureRepeated
        grid.setGestureMargin(device.displayWidth / 5)
        grid.fling(Direction.DOWN)
        device.waitForIdle()
        grid.fling(Direction.UP)
    }

    companion object {
        private const val PACKAGE = "com.lucasdss.ftpmusic.app"
    }
}
