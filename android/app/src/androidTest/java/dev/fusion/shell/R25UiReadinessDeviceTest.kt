// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real power transition + native touch/actual application dialog. No Compose input interception or model setter. */
class R25UiReadinessDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()

    @Test fun actualAsleepAvdIsPreparedBeforeNativeFindTouch() {
        UiLabReadiness.eligible()
        assertEquals("Actual OS runtime, not compileSdk", 34, android.os.Build.VERSION.SDK_INT)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.sleep()
            assertFalse("Negative control must actually be asleep", device.isScreenOn)
            UiLabReadiness.prepare("R25 deliberate asleep-before-Activity positive control")
            assertTrue(device.isScreenOn)
            ActivityScenario.launch(MainActivity::class.java).use {
                val find = device.wait(Until.findObject(By.text("Find")), 10000)
                assertNotNull("Actual visible Find control unavailable", find)
                assertFalse(find.visibleBounds.isEmpty)
                find.click() // One native pointer action; not a direct launcher/Compose callback.
                assertTrue("One real Find touch must open the real dialog", device.wait(Until.hasObject(By.text("Find local actions")), 10000))
                assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000))
                device.pressBack()
            }
        } finally { if (!device.isScreenOn) device.wakeUp() }
    }
}
