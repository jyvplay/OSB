package dev.fusion.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline profile generator.
 *
 * Deviation from the original plan: this lives inside :benchmark instead of a
 * separate :baselineprofile module, because BaselineProfileRule ships in
 * benchmark-macro-junit4 and a second module would add the androidx
 * baselineprofile plugin without adding capability.
 *
 * Run on a rooted emulator / userdebug image:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -P android.testInstrumentationRunnerArguments.class=\
 *dev.fusion.benchmark.BaselineProfileGenerator
 * The generated profile must then be copied into app/src/main/baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "dev.fusion.shell") {
        pressHome()
        startActivityAndWait()
        // Exercise the startup-critical surfaces so they are pre-compiled.
        listOf("Files", "Settings", "Notes").forEach { label ->
            device.wait(Until.findObject(By.text(label)), 3_000)?.click()
            device.waitForIdle()
        }
    }
}
