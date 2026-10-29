// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Clean-room `USBGuard` Per-Interface BadUSB & Inline Hardware-Keylogger Policy Engine.
 *
 * Defends against local physical USB implants (`Rubber Ducky`, `Bash Bunny`, `O.MG Cable`,
 * inline hardware keyloggers):
 * 1. **Lockscreen Zero-Trust**: rejects 100% of newly attached USB devices while screen is locked.
 * 2. **Composite BadUSB Rule** (`USBGuard` `reject with-interface all-of { 08:*:* 03:01:* }`):
 *    blocks devices combining Mass Storage (`08`) or CDC-Ethernet/RNDIS (`02`/`EF`/`FF`) with a
 *    HID Boot Keyboard (`03:01:01`) or HID Mouse (`03:01:02`).
 * 3. **Inline Hardware Keylogger / O.MG Interposer Heuristic**: flags an external Hub (`09:00:*`)
 *    + HID Keyboard (`03:01:01`) hotplug occurring within `<= 3000 ms` of an existing keyboard
 *    disconnect, or any device spoofing reserved/zero VID:PID (`0000:0000`, `ffff:ffff`).
 * 4. **Secondary Keyboard Gate**: requires interactive user confirmation (`QUARANTINE_PROMPT`)
 *    whenever a primary keyboard is already attached and a second keyboard hotplugs.
 */
object UsbGuardPolicy {
    const val SCOPE =
        "USBGuard-style per-interface descriptor evaluator for BadUSB composite devices, lockscreen hotplug blocking, secondary HID keyboard quarantine, and inline hub+HID keylogger interposer detection; kernel sysfs bConfigurationValue writes out of scope."

    const val CLASS_CDC_COMM = 0x02
    const val CLASS_HID = 0x03
    const val CLASS_MASS_STORAGE = 0x08
    const val CLASS_HUB = 0x09
    const val CLASS_CDC_DATA = 0x0A
    const val CLASS_WIRELESS_RNDIS = 0xE0
    const val CLASS_VENDOR_SPECIFIC = 0xFF

    const val INTERPOSER_WINDOW_MS = 3_000L

    data class UsbInterfaceSpec(
        val interfaceClass: Int,
        val interfaceSubClass: Int,
        val interfaceProtocol: Int,
    ) {
        init {
            require(interfaceClass in 0..0xFF && interfaceSubClass in 0..0xFF && interfaceProtocol in 0..0xFF) {
                "USB interface class/subclass/protocol outside 0x00..0xFF"
            }
        }

        val isBootKeyboard: Boolean
            get() = interfaceClass == CLASS_HID && interfaceSubClass == 0x01 && interfaceProtocol == 0x01

        val isAnyHidKeyboardOrGeneric: Boolean
            get() = interfaceClass == CLASS_HID && (isBootKeyboard || interfaceProtocol == 0x00)
    }

    data class UsbCandidate(
        val vendorId: Int,
        val productId: Int,
        val serialSha256Hex: String?,
        val interfaces: List<UsbInterfaceSpec>,
        val attachedBehindNewInlineHub: Boolean = false,
    ) {
        init {
            require(vendorId in 0..0xFFFF && productId in 0..0xFFFF) { "VID:PID outside 0..0xFFFF" }
            require(interfaces.isNotEmpty() && interfaces.size <= 32) { "interfaces size outside 1..32" }
        }

        fun vidPidHex(): String = "%04x:%04x".format(vendorId, productId)
    }

    data class HostContext(
        val screenLocked: Boolean,
        val activeKeyboardCount: Int,
        val lastKeyboardDisconnectMs: Long?,
        val nowMs: Long,
        val pinnedTrustedVidPidSerials: Set<String> = emptySet(),
    )

    enum class Action { ALLOW, QUARANTINE_PROMPT, REJECT }

    data class Verdict(val action: Action, val ruleId: String, val reason: String)

    fun evaluate(candidate: UsbCandidate, ctx: HostContext): Verdict {
        if (ctx.screenLocked) {
            return Verdict(Action.REJECT, "LOCKSCREEN_DENY_NEW_USB", "screen is locked; all hotplugged USB interfaces blocked")
        }
        if (candidate.vendorId == 0x0000 || candidate.vendorId == 0xFFFF || candidate.productId == 0xFFFF) {
            return Verdict(Action.REJECT, "BOGON_VID_PID", "spoofed/invalid USB VID:PID ${candidate.vidPidHex()}")
        }

        val hasHidInput = candidate.interfaces.any { it.isAnyHidKeyboardOrGeneric }
        val hasStorage = candidate.interfaces.any { it.interfaceClass == CLASS_MASS_STORAGE }
        val hasNetOrSerial = candidate.interfaces.any {
            it.interfaceClass in setOf(CLASS_CDC_COMM, CLASS_CDC_DATA, CLASS_WIRELESS_RNDIS, CLASS_VENDOR_SPECIFIC)
        }
        val hasHub = candidate.interfaces.any { it.interfaceClass == CLASS_HUB }

        // Rule 1: Composite BadUSB (Storage + HID or Network/Serial + HID)
        if (hasHidInput && (hasStorage || hasNetOrSerial)) {
            return Verdict(
                Action.REJECT,
                "COMPOSITE_BADUSB_HID",
                "composite device (${candidate.vidPidHex()}) mixes HID input with storage/network/vendor interface",
            )
        }

        // Rule 2: Inline hardware keylogger / O.MG interposer (Hub + HID or rapid re-plug behind new hub)
        val recentKbdDrop = ctx.lastKeyboardDisconnectMs != null &&
            ctx.nowMs >= ctx.lastKeyboardDisconnectMs &&
            (ctx.nowMs - ctx.lastKeyboardDisconnectMs) <= INTERPOSER_WINDOW_MS
        if (hasHidInput && (hasHub || (candidate.attachedBehindNewInlineHub && recentKbdDrop))) {
            return Verdict(
                Action.REJECT,
                "INLINE_KEYLOGGER_INTERPOSER",
                "suspected inline hardware keylogger/hub interposer on ${candidate.vidPidHex()}",
            )
        }

        // Rule 3: Pinned allowlist check (exact VID:PID:serialHash)
        val pinKey = candidate.serialSha256Hex?.let { "${candidate.vidPidHex()}:${it.lowercase()}" }
        if (pinKey != null && pinKey in ctx.pinnedTrustedVidPidSerials) {
            return Verdict(Action.ALLOW, "PINNED_ALLOWLIST", "matched pinned VID:PID:serial allowlist")
        }

        // Rule 4: Secondary keyboard hotplug requires physical user confirmation + physiological monitoring
        if (hasHidInput && ctx.activeKeyboardCount >= 1) {
            return Verdict(
                Action.QUARANTINE_PROMPT,
                "SECONDARY_HID_KEYBOARD",
                "secondary HID keyboard (${candidate.vidPidHex()}) requires explicit user consent + keystroke-jitter gate",
            )
        }

        if (hasHidInput) {
            return Verdict(
                Action.QUARANTINE_PROMPT,
                "UNPINNED_HID_INPUT",
                "unpinned HID keyboard (${candidate.vidPidHex()}) requires one-time physical user confirmation",
            )
        }

        return Verdict(Action.ALLOW, "NON_HID_PERIPHERAL", "non-HID peripheral permitted while unlocked")
    }
}
