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
            val observation = if (node != null) "not-needed" else runCatching {
                // One read-only post-failure observation; never print page/user-agent text or reload.
                val late = device.findObject(By.res(context.packageName, "r25_web_capability_result"))
                val marker = late?.text
                val state = when {
                    late == null -> "node-missing"
                    marker == null -> "text-unavailable"
                    marker.startsWith("Waiting for actual controlled WebView page") -> "still-waiting"
                    marker.startsWith("Observed WebView report failure:") -> "report-error"
                    marker.startsWith("{") -> "json-present-only-after-wait"
                    else -> "other-text"
                }
                val foreground = when (device.currentPackageName) {
                    context.packageName -> "test-app"
                    "android" -> "android-system"
                    "com.android.systemui" -> "system-ui"
                    null -> "unknown"
                    else -> "other"
                }
                "status=$state foreground=$foreground"
            }.getOrElse { "unavailable:${it.javaClass.simpleName}" }
            assertNotNull("Actual WebView report unavailable; no capability inference; $observation",node)
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
