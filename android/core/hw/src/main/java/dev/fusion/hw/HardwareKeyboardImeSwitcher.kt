// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Clean-room External Physical Keyboard Layout & IME Switcher (`IN-08`) preserving per-`VID:PID`
 * layout rings (`en-US`, `en-Dvorak`, `de-QWERTZ`, `fr-AZERTY`, `ja-Romaji`) with `Ctrl+Space` /
 * `Globe` cycling while keeping the onscreen soft keyboard suppressed.
 */
object HardwareKeyboardImeSwitcher {
    const val SCOPE =
        "Pure-JVM per-VID:PID external hardware keyboard layout ring & soft-IME suppression state machine (IN-08)."

    data class DeviceProfile(
        val vidPid: String,
        val layouts: List<String>,
        val activeIndex: Int,
        val suppressSoftKeyboard: Boolean = true,
    ) {
        val activeLayout: String get() = layouts[activeIndex]
    }

    class SwitcherRegistry {
        private val byDevice = LinkedHashMap<String, DeviceProfile>()

        fun registerDevice(vidPid: String, layouts: List<String>, suppressSoftIme: Boolean = true): DeviceProfile {
            val clean = vidPid.trim().uppercase()
            require(Regex("^[0-9A-F]{4}:[0-9A-F]{4}$").matches(clean)) { "expected VID:PID hex, got '$vidPid'" }
            val dedup = layouts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            require(dedup.isNotEmpty()) { "at least one layout required" }
            val prof = DeviceProfile(clean, dedup, 0, suppressSoftIme)
            byDevice[clean] = prof
            return prof
        }

        fun cycleLayout(vidPid: String, reverse: Boolean = false): DeviceProfile {
            val clean = vidPid.trim().uppercase()
            val cur = byDevice[clean] ?: throw IllegalArgumentException("unregistered keyboard '$clean'")
            val n = cur.layouts.size
            val nextIdx = if (reverse) (cur.activeIndex - 1 + n) % n else (cur.activeIndex + 1) % n
            val next = cur.copy(activeIndex = nextIdx)
            byDevice[clean] = next
            return next
        }
    }
}
