package dev.fusion.benchmark

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** No personal-device reset, nullable clicks or claim of physical performance acceptance. */
object LabJourney {
    const val TARGET = "dev.fusion.shell.pro"
    fun requireDisposableEmulator() {
        check(InstrumentationRegistry.getArguments().getString("fusionDisposable") == "true")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    fun acknowledgePilot(device: UiDevice) {
        // Either the explicit notice or the shelf must appear. An unrelated screen is not a pass.
        check(device.wait(Until.hasObject(By.text("Files")), 10_000) ||
            device.hasObject(By.text("I understand — evaluation only"))) { "No pilot UI" }
        device.findObject(By.text("I understand — evaluation only"))?.click()
        check(device.wait(Until.gone(By.text("I understand — evaluation only")), 5_000))
        check(device.wait(Until.hasObject(By.text("Files")), 5_000))
    }
    fun filesOpenClose(device: UiDevice) {
        acknowledgePilot(device)
        // Fresh dedicated AVD: no folder grants, no document enumeration or user data.
        requireNotNull(device.wait(Until.findObject(By.text("Files")), 5_000)).click()
        val empty = By.text("No folder access. Only folders you choose are visible.")
        check(device.wait(Until.hasObject(empty), 5_000)) { "Files did not open" }
        requireNotNull(device.wait(Until.findObject(By.desc("Close window")), 5_000)).click()
        check(device.wait(Until.gone(empty), 5_000)) { "Files did not close" }
        device.waitForIdle()
    }
}
