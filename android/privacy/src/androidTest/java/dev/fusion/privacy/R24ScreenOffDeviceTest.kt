package dev.fusion.privacy

import android.content.Intent
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test

/** Actual Android sleep/wake and protected-broadcast permission, NOT a manually invoked lifecycle. */
class R24ScreenOffDeviceTest {
    @Test fun realScreenOffDiscardsRevealedPadAndOldButtonCannotEnterOnWake() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(TouchCodeActivity::class.java).use {scenario->
            PrivacyDeviceReady.focus(scenario)
            lateinit var old:Button
            scenario.onActivity {a->
                old=a.findViewById<Button>(R.id.touch_key_1);old.performClick();a.findViewById<Button>(R.id.touch_reveal).performClick()
                assertTrue(a.findViewById<TextView>(R.id.touch_code_output).text.contains("(1/64)"))
            }
            try {
                device.sleep();instrumentation.waitForIdleSync()
                assertFalse(device.isScreenOn)
                device.wakeUp();device.pressMenu();instrumentation.waitForIdleSync()
                PrivacyDeviceReady.focus(scenario)
                scenario.onActivity {a->
                    assertEquals(" (0/64)",a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                    old.performClick()
                    assertEquals(" (0/64)",a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                }
            } finally {if(!device.isScreenOn) device.wakeUp()}
        }
    }
    @Test fun ordinaryUidCannotSpoofProtectedScreenOffBroadcast() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val error=runCatching {context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF).setPackage(context.packageName))}.exceptionOrNull()
        assertTrue("Protected OS screen-state event accepted from ordinary app",error is SecurityException)
    }
}
