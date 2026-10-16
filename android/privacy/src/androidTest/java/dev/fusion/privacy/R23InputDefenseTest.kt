package dev.fusion.privacy

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test

/** Executes real Activities/views/window events. Authored obscuration flags test admission, not a hardware implant.
 * API34 emulator evidence is not a hostile-host/physical-device/AX review.
 */
class R23InputDefenseTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T : Activity> focused(scenario: ActivityScenario<T>) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        do {
            var ready = false; scenario.onActivity { ready = it.hasWindowFocus() }
            if (ready) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Real Activity did not acquire focus")
    }
    private fun click(id: String) {
        val device = UiDevice.getInstance(instrumentation)
        val selector = By.res("dev.fusion.privacy", id)
        val initial = device.findObject(selector)
        if (initial == null || initial.visibleBounds.isEmpty) UiScrollable(UiSelector().scrollable(true))
            .scrollIntoView(UiSelector().resourceId("dev.fusion.privacy:id/$id"))
        val node = device.wait(Until.findObject(selector), 10_000)
        assertNotNull("Missing real UI control $id", node); node.click(); instrumentation.waitForIdleSync()
    }
    private fun event(action: Int, flags: Int): MotionEvent {
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { x = 20f; y = 20f; pressure = 1f; size = 1f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
    }
    private fun allViews(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { allViews(view.getChildAt(it)) } else emptyList()

    @Test fun realTouchEntryHasNoInputConnectionAndHardwareKeysCannotMutateCode() {
        ActivityScenario.launch(TouchCodeActivity::class.java).use { s ->
            focused(s); click("touch_key_7")
            s.onActivity { a ->
                val output = a.findViewById<TextView>(R.id.touch_code_output)
                assertEquals("• (1/64)", output.text.toString())
                assertNull(output.onCreateInputConnection(EditorInfo()))
                assertFalse(output.onCheckIsTextEditor())
                assertTrue(allViews(a.window.decorView).none { it is EditText })
                assertFalse(a.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()))
                for (key in listOf(KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_DEL)) {
                    assertTrue(a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
                    assertTrue(a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)))
                }
                assertTrue(a.dispatchKeyShortcutEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, 0, KeyEvent.META_CTRL_ON)))
                assertEquals("• (1/64)", output.text.toString())
                // Concrete boundary: trusted accessibility activation is intentionally NOT genuine-touch authentication.
                assertTrue(a.findViewById<Button>(R.id.touch_key_2).performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null))
                assertEquals("•• (2/64)", output.text.toString())
            }
            click("touch_reveal"); s.onActivity { assertEquals("72 (2/64)", it.findViewById<TextView>(R.id.touch_code_output).text.toString()) }
        }
    }

    @Test fun fullAndPartialObscurationDiscardRealEditorsAndDoNotResumeOnCleanUp() {
        for (flags in listOf(MotionEvent.FLAG_WINDOW_IS_OBSCURED, MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) {
            ActivityScenario.launch(PrivacyActivity::class.java).use { s ->
                focused(s)
                s.onActivity { a ->
                    val old = a.findViewById<EditText>(R.id.scratch_text)
                    old.setText("private scratch before obscured input")
                    a.findViewById<EditText>(R.id.link_text).setText("https://example.com/private?q=secret")
                    val down = event(MotionEvent.ACTION_DOWN, 0)
                    try { a.dispatchTouchEvent(down) } finally { down.recycle() }
                    val rejected = event(MotionEvent.ACTION_UP, flags)
                    try { assertTrue(a.dispatchTouchEvent(rejected)) } finally { rejected.recycle() }
                    assertNull(a.findViewById<View>(R.id.scratch_text)); assertEquals("", old.text.toString())
                    val cleanUp = event(MotionEvent.ACTION_UP, 0)
                    try { assertTrue(a.dispatchTouchEvent(cleanUp)) } finally { cleanUp.recycle() }
                    assertNull(a.findViewById<View>(R.id.scratch_text))
                }
                click("restart_workspace")
                s.onActivity { a ->
                    val fresh = a.findViewById<EditText>(R.id.scratch_text)
                    fresh.onTextContextMenuItem(android.R.id.undo)
                    assertEquals("", fresh.text.toString()); assertEquals("", a.findViewById<EditText>(R.id.link_text).text.toString())
                    assertTrue(fresh.filterTouchesWhenObscured); assertTrue(fresh.isAccessibilityDataSensitive)
                }
            }
        }
    }

    @Test fun detachedKeyCannotMutateNewPadAndNewPadEnforcesAtomicCapacity() {
        ActivityScenario.launch(TouchCodeActivity::class.java).use { s ->
            focused(s)
            var stale: Button? = null
            s.onActivity { a ->
                stale = a.findViewById(R.id.touch_key_2); stale!!.performClick()
                a.findViewById<Button>(R.id.touch_clear).performClick()
                stale!!.performClick()
                assertEquals(" (0/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                val key = a.findViewById<Button>(R.id.touch_key_1)
                repeat(64) { key.performClick() }
                assertEquals("•".repeat(64) + " (64/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                key.performClick()
                assertEquals("Input refused: maximum 64 digits.", a.findViewById<TextView>(R.id.touch_code_status).text.toString())
                a.findViewById<Button>(R.id.touch_reveal).performClick()
                assertEquals("1".repeat(64) + " (64/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
            }
            s.moveToState(Lifecycle.State.CREATED); s.moveToState(Lifecycle.State.RESUMED); focused(s)
            s.onActivity { a ->
                stale!!.performClick(); assertEquals(" (0/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                a.findViewById<Button>(R.id.touch_key_3).performClick()
            }
            s.recreate(); focused(s)
            s.onActivity { assertEquals(" (0/64)", it.findViewById<TextView>(R.id.touch_code_output).text.toString()) }
        }
    }

    @Test fun realFocusableDialogDiscardsPadBeforePauseAndFocusReturnStartsEmpty() {
        ActivityScenario.launch(TouchCodeActivity::class.java).use { s ->
            focused(s); var dialog: Dialog? = null; var oldOutput: TextView? = null
            s.onActivity { a ->
                a.findViewById<Button>(R.id.touch_key_4).performClick(); oldOutput = a.findViewById(R.id.touch_code_output)
                assertEquals("• (1/64)", oldOutput!!.text.toString())
                dialog = Dialog(a).apply { setContentView(TextView(a).apply { text = "Actual focus-loss test dialog" }); show() }
            }
            instrumentation.waitForIdleSync()
            s.onActivity { a ->
                assertFalse(a.hasWindowFocus()); assertEquals("", oldOutput!!.text.toString())
                assertNull(a.findViewById<View>(R.id.touch_code_output)); dialog!!.dismiss()
            }
            focused(s)
            s.onActivity { assertEquals(" (0/64)", it.findViewById<TextView>(R.id.touch_code_output).text.toString()) }
        }
    }

    @Test fun actualMonotonicIdleDeadlineDiscardsWithoutFurtherInteraction() {
        ActivityScenario.launch(TouchCodeActivity::class.java).use { s ->
            focused(s)
            s.onActivity { a ->
                a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                a.findViewById<Button>(R.id.touch_key_8).performClick()
                assertEquals("• (1/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
            }
            // Actual elapsed wait, not a fake clock or a shortened production timeout.
            SystemClock.sleep(61_000)
            instrumentation.waitForIdleSync()
            s.onActivity { a ->
                assertTrue(a.hasWindowFocus())
                assertEquals(" (0/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                assertEquals("Code discarded after 60 seconds without a code change.", a.findViewById<TextView>(R.id.touch_code_status).text.toString())
            }
        }
    }

    @Test fun actualRevealDeadlineRehidesAndNextInputMasksImmediately() {
        ActivityScenario.launch(TouchCodeActivity::class.java).use { s ->
            focused(s)
            s.onActivity { a ->
                a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                a.findViewById<Button>(R.id.touch_key_6).performClick()
                a.findViewById<Button>(R.id.touch_reveal).performClick()
                assertEquals("6 (1/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
            }
            SystemClock.sleep(6_000); instrumentation.waitForIdleSync()
            s.onActivity { a ->
                assertEquals("• (1/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                a.findViewById<Button>(R.id.touch_reveal).performClick()
                a.findViewById<Button>(R.id.touch_key_5).performClick()
                assertEquals("•• (2/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                a.findViewById<Button>(R.id.touch_reveal).performClick()
                a.findViewById<Button>(R.id.touch_clear).performClick()
            }
            // Attack the stale reveal Runnable: it must not publish into the replacement pad.
            SystemClock.sleep(6_000); instrumentation.waitForIdleSync()
            s.onActivity { assertEquals(" (0/64)", it.findViewById<TextView>(R.id.touch_code_output).text.toString()) }
        }
    }

    @Test fun realExposureSnapshotAndMergedManifestKeepScopeAndNewScreensPrivate() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // SDK37 compile anchor and actual installed manifest, NOT proof this API34 runtime became API37.
        assertEquals(37, android.os.Build.VERSION_CODES.CINNAMON_BUN)
        assertEquals(37, context.applicationInfo.targetSdkVersion)
        val exposure = ExposureProbe.read(context)
        assertTrue(exposure.describe().contains("NOT a malware/keylogger scan"))
        assertTrue(exposure.describe().contains("wireless debugging is NOT comprehensively inspected"))
        for (count in listOf(exposure.accessibilityServices, exposure.externalKeyboards, exposure.additionalDisplays)) assertTrue(count == null || count >= 0)
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.HIDE_OVERLAY_WINDOWS))
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        for (type in listOf(TouchCodeActivity::class.java, ExposureActivity::class.java)) {
            assertFalse(context.packageManager.getActivityInfo(ComponentName(context, type), 0).exported)
        }
        ActivityScenario.launch(ExposureActivity::class.java).use { s ->
            focused(s)
            s.onActivity { a ->
                assertTrue(a.findViewById<TextView>(R.id.exposure_report).text.contains("Local exposure snapshot"))
                assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                a.findViewById<Button>(R.id.refresh_exposure).performClick()
                assertTrue(a.findViewById<TextView>(R.id.exposure_report).text.contains("UNKNOWN") || exposure.adbEnabled != null)
            }
        }
    }
}
