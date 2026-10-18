// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real Settings provider notification on a disposable AVD. NOT a physical USB/keylogger test. */
class R24InputChangeDeviceTest {
    @Test fun actualImeConfigurationChangeDiscardsAndRequiresExplicitEmptyRestart() {
        require(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Never change a personal device's IME configuration" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val original = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        require(!original.isNullOrBlank()) { "A real IME setting is required for this change experiment" }
        ActivityScenario.launch(TouchCodeActivity::class.java).use { scenario ->
            PrivacyDeviceReady.focus(scenario)
            lateinit var old: Button
            scenario.onActivity { a ->
                old = a.findViewById(R.id.touch_key_1); old.performClick()
                assertTrue(a.findViewById<TextView>(R.id.touch_code_output).text.contains("1/64"))
            }
            instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
            try {
                fun awaitDiscard() {
                    val deadline = SystemClock.elapsedRealtime() + 10000
                    var discarded = false
                    while (SystemClock.elapsedRealtime() < deadline && !discarded) {
                        scenario.onActivity { a -> discarded = a.findViewById<TextView>(R.id.touch_code_output) == null && a.findViewById<Button>(R.id.restart_workspace) != null }
                        if (!discarded) SystemClock.sleep(50)
                    }
                    assertTrue("Real IME settings notification did not discard", discarded)
                }
                assertTrue(Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, ""))
                awaitDiscard()
                // Verify restart before restoring the setting; idle alone does not await a provider notification.
                lateinit var secondWorkspaceButton: Button
                PrivacyDeviceReady.focus(scenario)
                scenario.onActivity { a ->
                    assertNull(a.findViewById<TextView>(R.id.touch_code_output)) // Focus must not auto-rearm.
                    old.performClick(); assertNull(a.findViewById<TextView>(R.id.touch_code_output))
                    a.findViewById<Button>(R.id.restart_workspace).performClick()
                    assertEquals(" (0/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                    old.performClick(); assertEquals(" (0/64)", a.findViewById<TextView>(R.id.touch_code_output).text.toString())
                    secondWorkspaceButton = a.findViewById(R.id.touch_key_2)
                    secondWorkspaceButton.performClick()
                    assertTrue(a.findViewById<TextView>(R.id.touch_code_output).text.contains("1/64"))
                }
                assertTrue(Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, original))
                awaitDiscard() // A second genuine change invalidates the newly armed workspace too.
                scenario.onActivity { a ->
                    secondWorkspaceButton.performClick(); old.performClick()
                    assertNull(a.findViewById<TextView>(R.id.touch_code_output))
                    assertNotNull(a.findViewById<Button>(R.id.restart_workspace))
                }
            } finally {
                Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, original)
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }
        }
    }
}
