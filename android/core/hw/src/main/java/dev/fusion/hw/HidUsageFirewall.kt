// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Clean-room USB-IF **HID 1.11 Binary Report Descriptor Parser & BadUSB Usage-Page Firewall**.
 *
 * Complements USB device-class allowlisting (`USBGuard` / `BlueDucky`) by inspecting the actual
 * binary **HID Report Descriptor** short items (`bTag`, `bType`, `bSize`) of an attached keyboard
 * or mouse BEFORE admitting input events:
 * - Blocks **Vendor-Defined Usage Pages (`0xFF00..0xFFFF`)** used by covert BadUSB / Teensy /
 *   Flipper-Zero raw HID command-and-control backchannels masquerading inside a keyboard composite;
 * - Blocks **Consumer Page (`0x0C`) Application Launch (`AL`)** usages (`0x018A` Mail, `0x0192`
 *   Calculator, `0x0194` Local Browser, `0x0196` Internet Browser, `0x019E` Terminal Lock/Command)
 *   and **Generic Desktop (`0x01`) System Control** (`0x81` Power Down, `0x82` Sleep);
 * - Detects Rubber-Ducky / OMG-Cable **GUI+R / ALT+F2 / CTRL+ALT+T** rapid launcher chords.
 */
object HidUsageFirewall {
    const val SCOPE =
        "Clean-room USB-IF HID 1.11 binary report-descriptor parser & BadUSB usage-page/chord firewall (blocks 0xFF00..0xFFFF vendor C2 pages, System Power/Consumer AL launch usages, and rapid GUI+R / ALT+F2 injection chords)."

    const val PAGE_GENERIC_DESKTOP = 0x0001
    const val PAGE_KEYBOARD_KEYPAD = 0x0007
    const val PAGE_LEDS = 0x0008
    const val PAGE_BUTTON = 0x0009
    const val PAGE_CONSUMER = 0x000C

    private val DANGEROUS_CONSUMER_AL_USAGES = setOf(0x018A, 0x0192, 0x0194, 0x0196, 0x019E, 0x0221)
    private val DANGEROUS_SYSTEM_CONTROL_USAGES = setOf(0x0081, 0x0082, 0x0083)

    data class DescriptorVerdict(
        val allowed: Boolean,
        val usagePages: Set<Int>,
        val topLevelUsages: List<Pair<Int, Int>>, // (usagePage, usage)
        val blockedReasons: List<String>,
    )

    fun inspectReportDescriptor(descriptor: ByteArray): DescriptorVerdict {
        require(descriptor.isNotEmpty() && descriptor.size <= 4_096) {
            "HID report descriptor size ${descriptor.size} outside 1..4096"
        }
        val pages = LinkedHashSet<Int>()
        val topUsages = ArrayList<Pair<Int, Int>>()
        val reasons = ArrayList<String>()

        var currentPage = 0
        val pendingUsages = ArrayList<Int>()
        var collectionDepth = 0
        var pos = 0

        while (pos < descriptor.size) {
            val prefix = descriptor[pos++].toInt() and 0xFF
            require(prefix != 0xFE) { "HID long item (0xFE) refused in strict keyboard/mouse profile" }
            val bSizeCode = prefix and 0x03
            val dataLen = if (bSizeCode == 3) 4 else bSizeCode
            require(pos + dataLen <= descriptor.size) { "truncated HID short item at byte $pos" }
            val bType = (prefix ushr 2) and 0x03 // 0=Main, 1=Global, 2=Local
            val bTag = (prefix ushr 4) and 0x0F
            var value = 0
            for (i in 0 until dataLen) {
                value = value or ((descriptor[pos + i].toInt() and 0xFF) shl (i * 8))
            }
            pos += dataLen

            when (bType) {
                1 -> { // Global
                    if (bTag == 0x00) { // Usage Page
                        currentPage = value
                        pages.add(currentPage)
                        if (currentPage in 0xFF00..0xFFFF) {
                            reasons.add("VENDOR_DEFINED_HID_C2_PAGE_0x${currentPage.toString(16).uppercase()}")
                        }
                    }
                }
                2 -> { // Local
                    if (bTag == 0x00) { // Usage
                        pendingUsages.add(value)
                        if (currentPage == PAGE_CONSUMER && value in DANGEROUS_CONSUMER_AL_USAGES) {
                            reasons.add("CONSUMER_APP_LAUNCH_USAGE_0x${value.toString(16).uppercase()}")
                        }
                        if (currentPage == PAGE_GENERIC_DESKTOP && value in DANGEROUS_SYSTEM_CONTROL_USAGES) {
                            reasons.add("SYSTEM_POWER_CONTROL_USAGE_0x${value.toString(16).uppercase()}")
                        }
                    }
                }
                0 -> { // Main
                    when (bTag) {
                        0x0A -> { // Collection
                            if (collectionDepth == 0 && pendingUsages.isNotEmpty()) {
                                topUsages.add(currentPage to pendingUsages.last())
                            }
                            collectionDepth++
                            pendingUsages.clear()
                        }
                        0x0C -> { // End Collection
                            require(collectionDepth > 0) { "unmatched HID End Collection (0xC0)" }
                            collectionDepth--
                            pendingUsages.clear()
                        }
                        else -> pendingUsages.clear()
                    }
                }
            }
        }
        require(collectionDepth == 0) { "unclosed HID Collection at end of descriptor" }
        val allowedStandardPages = setOf(PAGE_GENERIC_DESKTOP, PAGE_KEYBOARD_KEYPAD, PAGE_LEDS, PAGE_BUTTON)
        for (p in pages) {
            if (p !in allowedStandardPages && p !in 0xFF00..0xFFFF) {
                if (p != PAGE_CONSUMER) reasons.add("NON_STANDARD_HID_PAGE_0x${p.toString(16).uppercase()}")
            }
        }
        return DescriptorVerdict(
            allowed = reasons.isEmpty(),
            usagePages = pages,
            topLevelUsages = topUsages,
            blockedReasons = reasons.distinct(),
        )
    }

    /** Detects USB-HID Boot-Keyboard (`8`-byte report: `[modifier, reserved, k0..k5]`) launcher-injection chords. */
    fun isDangerousBootKeyboardReport(report8: ByteArray): Boolean {
        require(report8.size == 8) { "USB Boot Keyboard report must be 8 bytes" }
        val mods = report8[0].toInt() and 0xFF
        val hasGui = (mods and 0x88) != 0 // Left GUI (0x08) or Right GUI (0x80)
        val hasAlt = (mods and 0x44) != 0 // Left Alt (0x04) or Right Alt (0x40)
        val hasCtrl = (mods and 0x11) != 0 // Left Ctrl (0x01) or Right Ctrl (0x10)
        for (i in 2 until 8) {
            val kc = report8[i].toInt() and 0xFF
            if (kc == 0) continue
            if (hasGui && kc == 0x15) return true // GUI + R (Run dialog)
            if (hasAlt && kc == 0x3B) return true // ALT + F2 (Desktop command runner)
            if (hasCtrl && hasAlt && kc == 0x17) return true // CTRL + ALT + T (Terminal spawn)
        }
        return false
    }
}
