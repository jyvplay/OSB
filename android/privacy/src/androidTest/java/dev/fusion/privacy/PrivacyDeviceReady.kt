package dev.fusion.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real platform readiness. No fabricated focus/lifecycle callbacks, test retries or production rearm. */
internal object PrivacyDeviceReady {
    private fun posture(): JSONObject {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val root = runCatching { instrumentation.uiAutomation.rootInActiveWindow }.getOrNull()
        return JSONObject().put("api", Build.VERSION.SDK_INT).put("interactive", power.isInteractive)
            .put("keyguard_locked", keyguard.isKeyguardLocked).put("device_locked", keyguard.isDeviceLocked)
            .put("credential_secure", keyguard.isKeyguardSecure)
            .put("foreground_package", root?.packageName?.toString() ?: "none")
            .put("foreground_class", root?.className?.toString() ?: "none")
    }
    private fun isReady(state: JSONObject) = state.getBoolean("interactive") && !state.getBoolean("keyguard_locked") && !state.getBoolean("device_locked")

    /** Failure-only, bounded observation; never emits raw titles or acts on a system window. */
    private fun windowSummary(): JSONObject = runCatching {
        val windows = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
        val rows = org.json.JSONArray()
        for (window in windows.take(8)) {
            val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
            val system = pkg == "android" || pkg == "com.android.systemui"
            val title = if (system) runCatching { window.title }.getOrNull() else null
            val kind = PrivacyWindowClassifier.classify(pkg, title)
            rows.put(JSONObject().put("root_kind", kind.root).put("title_kind", kind.title)
                .put("type", window.type).put("active", window.isActive).put("focused", window.isFocused))
        }
        JSONObject().put("outcome", if (windows.isEmpty()) "empty-or-unavailable" else "observed")
            .put("reported_count", windows.size).put("truncated", windows.size > 8).put("windows", rows)
    }.getOrElse { JSONObject().put("outcome", "unavailable").put("error_kind", it.javaClass.simpleName) }

    /** Before launching any tested UI: one real wake/dismiss of an UNSECURED emulator keyguard if needed.
     * Never dismiss arbitrary dialogs, change permissions, unlock credentials, retry a case or restart a workspace.
     */
    fun prepareEmulator(case: String) {
        require(Build.HARDWARE in setOf("ranchu", "goldfish")) { "UI laboratory preflight is restricted to disposable emulators" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val before = posture()
        check(!before.getBoolean("credential_secure")) { "Refuse a credential-secured device; $before" }
        val actions = mutableListOf<String>()
        if (!before.getBoolean("interactive")) {
            UiDevice.getInstance(instrumentation).wakeUp(); actions.add("one-real-wake")
        }
        if (before.getBoolean("keyguard_locked") || posture().getBoolean("keyguard_locked")) {
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
            actions.add("one-unsecured-keyguard-dismiss")
        }
        val deadline = SystemClock.elapsedRealtime() + 5000
        var after = posture()
        while (!isReady(after) && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50); after = posture() // Observe only; do not repeat the actions.
        }
        val record = JSONObject().put("origin", "android-instrumentation").put("case", case)
            .put("before", before).put("after", after).put("actions", org.json.JSONArray(actions))
            .put("scope", "Disposable emulator display precondition only; not privacy capability or root-cause proof")
        val folder = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "r24c-privacy-readiness")
        check(folder.exists() || folder.mkdirs())
        val file = File(folder, "r24c-privacy-preflight.jsonl")
        check(file.length() < 64 * 1024)
        file.appendText(record.toString() + "\n")
        assertTrue("Real emulator UI precondition not ready: $record", isReady(after))
        instrumentation.waitForIdleSync()
    }
    fun emulatorRule(): TestRule = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                prepareEmulator(description.className + "." + description.methodName)
                base.evaluate() // Exactly once; exceptions and original assertions propagate.
            }
        }
    }
    fun <T:Activity> focus(scenario:ActivityScenario<T>,expected:Boolean=true) {
        val ready=CountDownLatch(1)
        var observer:ViewTreeObserver?=null
        val listener=ViewTreeObserver.OnWindowFocusChangeListener {focused ->if(focused==expected) ready.countDown()}
        scenario.onActivity {activity ->
            observer=activity.window.decorView.viewTreeObserver
            observer!!.addOnWindowFocusChangeListener(listener)
            if(activity.hasWindowFocus()==expected) ready.countDown()
        }
        try {
            // Preserve the original10s focus deadline; the new precondition is staged BEFORE Activity launch.
            val gained = ready.await(10,TimeUnit.SECONDS)
            if (!gained) fail("Actual window focus did not become $expected; lifecycle=${scenario.state}; posture=${posture()}; windows=${windowSummary()}")
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var observed = !expected
            scenario.onActivity { observed = it.hasWindowFocus() }
            if (observed != expected) fail("Focus changed before action staging; posture=${posture()}; windows=${windowSummary()}")
        } finally {scenario.onActivity {observer?.takeIf {it.isAlive}?.removeOnWindowFocusChangeListener(listener)}}
    }
}
