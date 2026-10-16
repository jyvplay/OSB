package dev.fusion.privacy

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.InputDevice
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import java.lang.Character

/**
 * A transient, read-only view of values that ordinary Android APIs report to this app.
 * This is not a malware scan, hardware attestation, security score, or proof of anonymity.
 * No values are persisted or sent by this class. Individual failed/filtered observations stay unknown.
 */
internal data class DevicePrivacySnapshot(
    val apiLevel: Int,
    val securityPatch: String?,
    val secureScreenLockConfigured: Boolean?,
    val adbDebuggingEnabled: Boolean?,
    val currentInputMethodPackage: String?,
    val enabledAccessibilityServiceIds: List<String>?,
    val nonVirtualKeyboardNames: List<String>?
) {
    /** Human-readable disclosure deliberately keeps the non-observable risks visible on every result. */
    fun toDisplayText(): String = buildString {
        appendLine("LOCAL OBSERVATIONS — not a malware or keylogger scan.")
        appendLine("Android API level reported by the OS: $apiLevel.")
        appendLine("Security patch reported by the OS (not checked against latest): ${securityPatch ?: "unavailable"}.")
        appendLine("Secure screen lock configured: ${secureScreenLockConfigured?.let { if (it) "yes" else "no" } ?: "unknown"}.")
        appendLine("USB debugging setting: ${adbDebuggingEnabled?.let { if (it) "enabled" else "disabled" } ?: "unknown"}. This does not report wireless debugging or USB Protection state.")
        appendLine("Current input method package reported by Android: ${currentInputMethodPackage ?: "unavailable"}. It is not trust-checked; review all enabled keyboards in system settings.")
        appendLine("Enabled accessibility services reported by Android: ${enabledAccessibilityServiceIds?.let { values -> if (values.isEmpty()) "none listed" else values.joinToString(", ") } ?: "unavailable"}. Accessibility tools may be important; review rather than blindly disabling them.")
        appendLine("Non-virtual keyboard devices reported right now: ${nonVirtualKeyboardNames?.let { if (it.isEmpty()) "none listed" else it.joinToString(", ") } ?: "unavailable"}. Device names do not prove authenticity and may omit inline or firmware-level devices.")
        appendLine("This app cannot inspect a physical/inline keylogger, malicious cable or keyboard firmware, compromised boot firmware, other apps, the operating system, or the laptop/hypervisor.")
        append("A reassuring or empty snapshot is not proof that the device is safe or anonymous. Use trusted hardware and review the operating-system controls described below.")
    }
}

/** Uses public read-only platform APIs only; no special permission, device-owner authority, or network. */
internal object DevicePrivacyProbe {
    private const val MAX_COMPONENT_UNITS = 160
    private const val MAX_PACKAGE_UNITS = 128
    private const val MAX_DEVICE_NAME_UNITS = 96
    private const val MAX_LIST_ITEMS = 12

    fun capture(context: Context): DevicePrivacySnapshot {
        val appContext = context.applicationContext
        val apiLevel = Build.VERSION.SDK_INT
        val patch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() }?.displaySafe(MAX_COMPONENT_UNITS)

        val lockConfigured = runCatching {
            appContext.getSystemService(KeyguardManager::class.java)?.isDeviceSecure
        }.getOrNull()
        val adbEnabled = runCatching {
            when (Settings.Global.getInt(appContext.contentResolver, Settings.Global.ADB_ENABLED, -1)) {
                1 -> true
                0 -> false
                else -> null
            }
        }.getOrNull()
        val currentImePackage = runCatching {
            appContext.getSystemService(InputMethodManager::class.java)
                ?.currentInputMethodInfo
                ?.packageName
                ?.displaySafe(MAX_PACKAGE_UNITS)
        }.getOrNull()
        val accessibilityServices = runCatching {
            val manager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return@runCatching null
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .asSequence()
                .mapNotNull { service ->
                    val info = service.resolveInfo?.serviceInfo ?: return@mapNotNull null
                    "${info.packageName}/${info.name}".displaySafe(MAX_COMPONENT_UNITS)
                }
                .distinct()
                .take(MAX_LIST_ITEMS)
                .toList()
        }.getOrNull()
        val keyboardNames = runCatching {
            InputDevice.getDeviceIds()
                .asSequence()
                .mapNotNull(InputDevice::getDevice)
                .filter {
                    !it.isVirtual &&
                        (it.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                }
                .map { it.name.displaySafe(MAX_DEVICE_NAME_UNITS) }
                .distinct()
                .take(MAX_LIST_ITEMS)
                .toList()
        }.getOrNull()

        return DevicePrivacySnapshot(
            apiLevel = apiLevel,
            securityPatch = patch,
            secureScreenLockConfigured = lockConfigured,
            adbDebuggingEnabled = adbEnabled,
            currentInputMethodPackage = currentImePackage,
            enabledAccessibilityServiceIds = accessibilityServices,
            nonVirtualKeyboardNames = keyboardNames
        )
    }
}

internal fun sanitizePostureValue(value: String, maxUtf16Units: Int): String = buildString {
    for (character in value.take(maxUtf16Units)) {
        // Replace control/format characters so a peripheral-controlled name cannot spoof UI order or labels.
        val category = Character.getType(character)
        val unsafeDisplayCharacter = Character.isISOControl(character) ||
            category == Character.FORMAT.toInt() ||
            category == Character.LINE_SEPARATOR.toInt() ||
            category == Character.PARAGRAPH_SEPARATOR.toInt()
        append(if (unsafeDisplayCharacter) ' ' else character)
    }
}

private fun String.displaySafe(maxUtf16Units: Int): String = sanitizePostureValue(this, maxUtf16Units)
