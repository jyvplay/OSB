// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.fusion.shell.probes.WebCapabilityProbeActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real renderer observation; a negative SharedArrayBuffer result is a boundary, not a failed Office claim or mock. */
class R25WebCapabilityDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    @Test fun actualControlledWebViewReportsThreadedWasmPrerequisitesWithoutClaimingOffice() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=ApplicationProvider.getApplicationContext<Context>()
        val device=UiDevice.getInstance(instrumentation)
        context.startActivity(Intent(context,WebCapabilityProbeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val node=device.wait(Until.findObject(By.res(context.packageName,"r25_web_capability_result").textStartsWith("{")),15000)
            assertNotNull("Actual WebView report unavailable; no capability inference",node)
            val actual=JSONObject(node.text)
            assertEquals("Actual renderer report: $actual","R25 native JS 42",actual.optString("canary"));assertEquals(34,actual.getInt("api"))
            assertNotEquals(Process.myPid(),actual.getInt("pid"));assertEquals(context.applicationInfo.uid,actual.getInt("uid"))
            assertTrue(actual.getBoolean("wasm"))
            val eligible=actual.optBoolean("secure",false) && actual.optBoolean("isolated",false) && actual.optBoolean("sharedArrayBuffer",false)
            actual.put("origin","android-instrumentation").put("threaded_wasm_prerequisites_observed",eligible)
                .put("office_loaded",false).put("complete_browser_accepted",false)
            val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: File(context.getExternalFilesDir(null),"r25-browser-observation")
            check(folder.exists() || folder.mkdirs());File(folder,"r25-web-capability.json").writeText(actual.toString(2))
        } finally {device.pressBack()}
    }
}
