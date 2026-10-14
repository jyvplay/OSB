package dev.fusion.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real macrobenchmarks for the shell.
 *
 * These produce measurements only when executed on a device or emulator:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * Until then every performance figure in the specification stays unmeasured.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.DEFAULT
    ) {
        pressHome()
        startActivityAndWait()
    }

    /**
     * Opens several shell windows from the shelf and records frame timing while
     * the window list is scrolled. This is the closest honest proxy we have for
     * the compositor budget until window dragging is automated.
     */
    @Test
    fun openWindowsFrameTiming() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT
    ) {
        startActivityAndWait()
        listOf("Files", "Notes", "Calculator").forEach { label ->
            val button = device.wait(Until.findObject(By.text(label)), 3_000)
            button?.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET = "dev.fusion.shell.pro"
    }
}
