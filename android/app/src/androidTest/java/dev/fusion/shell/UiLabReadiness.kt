// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import java.io.File

/** Laboratory precondition, not an app feature or a retry rule. Never unlock credentials/dismiss arbitrary dialogs. */
internal object UiLabReadiness {
    fun eligible() {
        check(InstrumentationRegistry.getArguments().getString("fusionDisposable") == "true") { "Explicit disposable AVD consent required" }
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "No personal/physical device UI preparation" }
    }
    private fun state(context: Context): JSONObject {
        val power = requireNotNull(context.getSystemService(PowerManager::class.java))
        val keyguard = requireNotNull(context.getSystemService(KeyguardManager::class.java))
        return JSONObject().put("api", Build.VERSION.SDK_INT)
            .put("interactive", power.isInteractive).put("keyguard_locked", keyguard.isKeyguardLocked)
            .put("device_locked", keyguard.isDeviceLocked).put("credential_secure", keyguard.isKeyguardSecure)
    }
    private fun ready(state: JSONObject) = state.getBoolean("interactive") && !state.getBoolean("keyguard_locked") && !state.getBoolean("device_locked")

    fun prepare(case: String) {
        eligible()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val before = state(context)
        check(!before.getBoolean("credential_secure")) { "Refuse a credential-secured emulator: $before" }
        val actions = mutableListOf<String>()
        if (!before.getBoolean("interactive")) {
            UiDevice.getInstance(instrumentation).wakeUp(); actions.add("one-real-wake")
        }
        if (state(context).getBoolean("keyguard_locked")) {
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
            actions.add("one-unsecured-keyguard-dismiss")
        }
        // Observation budget, NOT a hard bound on a stalled Binder or a longer behavioral-test deadline.
        val deadline = SystemClock.elapsedRealtime() + 5000
        var after = state(context)
        while (!ready(after) && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50); after = state(context)
        }
        // Connect the genuine accessibility observation channel BEFORE the tested Activity is launched.
        val root = runCatching { instrumentation.uiAutomation.rootInActiveWindow }.getOrNull()
        after.put("foreground_package", root?.packageName?.toString() ?: "none")
        val record = JSONObject().put("origin", "android-instrumentation").put("case", case)
            .put("phase", InstrumentationRegistry.getArguments().getString("fusionUiPhase", "candidate"))
            .put("elapsed_ms", SystemClock.elapsedRealtime()).put("before", before).put("after", after)
            .put("actions", JSONArray(actions)).put("scope", "Disposable UI precondition only; no test retry, production state setter or privacy acceptance")
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(context.getExternalFilesDir(null), "r25-ui-observations")
        check(directory.exists() || directory.mkdirs())
        val file = File(directory, "r25-ui-preflight.jsonl")
        check(file.length() + record.toString().toByteArray(Charsets.UTF_8).size + 1 <= 256 * 1024)
        file.appendText(record.toString() + "\n")
        assertTrue("Actual UI laboratory condition is not ready: $record", ready(after))
        instrumentation.waitForIdleSync()
    }
}
