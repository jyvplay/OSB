package dev.fusion.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual measurements only. Emulator samples do not satisfy physical performance budgets. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Before fun consent() = LabJourney.requireDisposableEmulator()

    @Test fun coldStartup() = startup(CompilationMode.None())
    @Test fun profileCompiledStartup() = startup(CompilationMode.Partial(
        baselineProfileMode = BaselineProfileMode.Require, warmupIterations = 0))

    private fun startup(compilation: CompilationMode) = rule.measureRepeated(
        packageName = LabJourney.TARGET, metrics = listOf(StartupTimingMetric()),
        iterations = 3, startupMode = StartupMode.COLD, compilationMode = compilation,
        setupBlock = { pressHome() }
    ) { startActivityAndWait() }

    @Test fun filesOpenClose() = rule.measureRepeated(
        packageName = LabJourney.TARGET, metrics = listOf(FrameTimingMetric()),
        iterations = 3, startupMode = StartupMode.WARM, compilationMode = CompilationMode.None(),
        setupBlock = { startActivityAndWait(); LabJourney.acknowledgePilot(device) }
    ) { LabJourney.filesOpenClose(device) }
}
