package dev.fusion.privacy

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import android.view.accessibility.AccessibilityManager
import dev.fusion.security.LocalExposure

/** No identifiers retained, no special privileges, no root/implant detector. Each unavailable signal stays unknown. */
object ExposureProbe {
    fun read(context: Context): LocalExposure {
        fun count(block: () -> Int): Int? = try { block() } catch (_: RuntimeException) { null }
        fun setting(name: String): Boolean? = try {
            when (Settings.Global.getInt(context.contentResolver, name)) { 0 -> false; 1 -> true; else -> null }
        } catch (_: Settings.SettingNotFoundException) { null } catch (_: RuntimeException) { null }
        return LocalExposure(
            accessibilityServices = count {
                requireNotNull(context.getSystemService(AccessibilityManager::class.java))
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).size
            },
            externalKeyboards = count {
                val manager = requireNotNull(context.getSystemService(InputManager::class.java))
                // A removal during enumeration is unknown, not a false clean count.
                manager.inputDeviceIds.map { requireNotNull(manager.getInputDevice(it)) }.count {
                    it.isExternal && it.supportsSource(InputDevice.SOURCE_KEYBOARD)
                }
            },
            additionalDisplays = count {
                requireNotNull(context.getSystemService(DisplayManager::class.java)).displays.count { it.displayId != Display.DEFAULT_DISPLAY }
            },
            adbEnabled = setting(Settings.Global.ADB_ENABLED),
            developerOptions = setting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        )
    }
}
