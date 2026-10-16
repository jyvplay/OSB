package dev.fusion.privacy

import android.os.Build
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class PrivacyPostureTest {
    @Test fun untrustedDeviceTextCannotInjectControlOrBidirectionalFormatting() {
        val sanitized = sanitizePostureValue("Trusted\u202EKeyboard\n\u2028", 160)
        assertEquals("Trusted Keyboard  ", sanitized)
        assertFalse(sanitized.contains('\u202E'))
        assertFalse(sanitized.contains('\n'))
        assertFalse(sanitized.contains('\u2028'))
    }

    @Test fun evenAnAllReassuringSnapshotDoesNotClaimTheDeviceIsSafeOrAnonymous() {
        val optimistic = DevicePrivacySnapshot(
            apiLevel = 36,
            securityPatch = "2099-12-31",
            secureScreenLockConfigured = true,
            adbDebuggingEnabled = false,
            currentInputMethodPackage = "com.android.inputmethod.latin",
            enabledAccessibilityServiceIds = emptyList(),
            nonVirtualKeyboardNames = emptyList()
        )

        val report = optimistic.toDisplayText()
        assertTrue(report.contains("not a malware or keylogger scan"))
        assertTrue(report.contains("cannot inspect a physical/inline keylogger"))
        assertTrue(report.contains("not proof that the device is safe or anonymous"))
        assertFalse(report.contains("Device is safe"))
        assertFalse(report.contains("All checks passed"))
    }

    @Test fun failedOrFilteredObservationsRemainUnknownRatherThanBecomingFalse() {
        val unknown = DevicePrivacySnapshot(
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = null,
            secureScreenLockConfigured = null,
            adbDebuggingEnabled = null,
            currentInputMethodPackage = null,
            enabledAccessibilityServiceIds = null,
            nonVirtualKeyboardNames = null
        )

        val report = unknown.toDisplayText()
        assertTrue(report.contains("Security patch reported by the OS (not checked against latest): unavailable"))
        assertTrue(report.contains("Secure screen lock configured: unknown"))
        assertTrue(report.contains("USB debugging setting: unknown"))
        assertTrue(report.contains("Current input method package reported by Android: unavailable"))
        assertTrue(report.contains("Enabled accessibility services reported by Android: unavailable"))
        assertTrue(report.contains("Non-virtual keyboard devices reported right now: unavailable"))
        assertTrue(report.contains("not proof that the device is safe or anonymous"))
    }

    @Test fun livePanelRendersBoundedObservationsAndSettingsButtons() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = DevicePrivacyProbe.capture(context)
        assertEquals(Build.VERSION.SDK_INT, snapshot.apiLevel)
        assertTrue(snapshot.securityPatch == null || snapshot.securityPatch.length <= 160)
        assertTrue(snapshot.currentInputMethodPackage == null || snapshot.currentInputMethodPackage.length <= 128)
        assertTrue(snapshot.enabledAccessibilityServiceIds == null || snapshot.enabledAccessibilityServiceIds.size <= 12)
        assertTrue(snapshot.enabledAccessibilityServiceIds.orEmpty().all { it.length <= 160 })
        assertTrue(snapshot.nonVirtualKeyboardNames == null || snapshot.nonVirtualKeyboardNames.size <= 12)
        assertTrue(snapshot.nonVirtualKeyboardNames.orEmpty().all { it.length <= 96 })

        ActivityScenario.launch(PrivacyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val report = activity.findViewById<TextView>(R.id.device_posture).text.toString()
                assertTrue(report.contains("LOCAL OBSERVATIONS"))
                assertTrue(report.contains("not a malware or keylogger scan"))
                assertTrue(report.contains("not proof that the device is safe or anonymous"))
                assertNotNull(activity.findViewById<Button>(R.id.refresh_device_posture))
                assertNotNull(activity.findViewById<Button>(R.id.review_keyboard_settings))
                assertNotNull(activity.findViewById<Button>(R.id.review_accessibility_settings))
                assertNotNull(activity.findViewById<Button>(R.id.review_developer_settings))
                assertNotNull(activity.findViewById<Button>(R.id.review_security_settings))
            }
        }
    }
}
