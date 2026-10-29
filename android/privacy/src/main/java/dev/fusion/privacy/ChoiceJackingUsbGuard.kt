// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room defense against **ChoiceJacking** (*Draschbacher et al., TU Graz, 34th USENIX Security
 * Symposium, Aug 2025*) malicious public USB chargers & cables.
 *
 * Defends all three ChoiceJacking exploit primitives:
 * 1. **HID -> BLE Pairing Bridge**: Blocks USB HID keyboard enumeration + Bluetooth pairing initiation
 *    within `STABILIZATION_WINDOW_MS` of VBUS charger attachment.
 * 2. **Input-Buffer Keystroke Flood**: Drops external HID `ENTER` / `SPACE` / `TAB` / `DPAD_CENTER`
 *    keystrokes while any system USB / MTP / PTP / ADB consent dialog is active or within `5,000 ms`
 *    of a USB role/mode transition.
 * 3. **Android Open Accessory Protocol (`AOAP`, VID `0x18D1`, PIDs `0x2D00..0x2D05`) Abuse &
 *    Power-Line Distraction Side Channel**: Rejects `AOAP` simultaneous host + HID interface claims
 *    and forces `CHARGE_ONLY` whenever the screen is locked or an active voice/VoIP call is ongoing!
 */
object ChoiceJackingUsbGuard {
    const val GOOGLE_AOAP_VENDOR_ID = 0x18D1
    val AOAP_PRODUCT_IDS: Set<Int> = setOf(0x2D00, 0x2D01, 0x2D02, 0x2D03, 0x2D04, 0x2D05)
    const val USB_CLASS_HID = 0x03
    const val STABILIZATION_WINDOW_MS = 2_500L
    const val HID_TO_DATA_CORRELATION_MS = 5_000L

    enum class UsbDataPolicy {
        CHARGE_ONLY_LOCKED,
        ALLOW_DATA_PROMPT_TOUCH_ONLY,
        QUARANTINE_CHOICEJACKING_ATTACK,
    }

    data class UsbAttachContext(
        val vendorId: Int,
        val productId: Int,
        val interfaceClasses: List<Int>,
        val screenLocked: Boolean,
        val activeVoiceOrVoipCall: Boolean,
        val msSinceVbusPowerAttach: Long,
        val msSinceLastExternalHidKeystroke: Long?, // null if none observed
        val blePairingRequestedSinceAttach: Boolean = false,
        val trustedHardwareFingerprintAllowlisted: Boolean = false,
    )

    data class GuardVerdict(
        val policy: UsbDataPolicy,
        val disableUsbDataSignaling: Boolean,
        val blockExternalHidOnConsentDialog: Boolean,
        val reason: String,
    )

    fun evaluateAttach(ctx: UsbAttachContext): GuardVerdict {
        val isAoap = ctx.vendorId == GOOGLE_AOAP_VENDOR_ID && ctx.productId in AOAP_PRODUCT_IDS
        val hasHid = USB_CLASS_HID in ctx.interfaceClasses

        // Technique 3: AOAP simultaneous accessory + HID input injection
        if (isAoap && (hasHid || !ctx.trustedHardwareFingerprintAllowlisted)) {
            return GuardVerdict(
                UsbDataPolicy.QUARANTINE_CHOICEJACKING_ATTACK,
                disableUsbDataSignaling = true,
                blockExternalHidOnConsentDialog = true,
                reason = "CHOICEJACKING_AOAP_BLOCKED: unallowlisted AOAP (0x18D1:0x${ctx.productId.toString(16)}) accessory/HID claim refused",
            )
        }

        // Technique 1: Charger presents as HID keyboard and triggers BLE keyboard pairing
        if (hasHid && ctx.blePairingRequestedSinceAttach) {
            return GuardVerdict(
                UsbDataPolicy.QUARANTINE_CHOICEJACKING_ATTACK,
                disableUsbDataSignaling = true,
                blockExternalHidOnConsentDialog = true,
                reason = "CHOICEJACKING_HID_BLE_BRIDGE_BLOCKED: USB HID enumeration attempted concurrent Bluetooth pairing",
            )
        }

        // Technique 2: Input buffer keystroke flood immediately preceding USB data role negotiation
        if (ctx.msSinceLastExternalHidKeystroke != null &&
            ctx.msSinceLastExternalHidKeystroke in 0..HID_TO_DATA_CORRELATION_MS
        ) {
            return GuardVerdict(
                UsbDataPolicy.QUARANTINE_CHOICEJACKING_ATTACK,
                disableUsbDataSignaling = true,
                blockExternalHidOnConsentDialog = true,
                reason = "CHOICEJACKING_INPUT_FLOOD_BLOCKED: USB data negotiation within ${ctx.msSinceLastExternalHidKeystroke}ms of external HID keystrokes",
            )
        }

        // Screen-locked or power-line phone-call distraction window -> strict CHARGE_ONLY
        if (ctx.screenLocked || ctx.activeVoiceOrVoipCall || ctx.msSinceVbusPowerAttach < STABILIZATION_WINDOW_MS) {
            return GuardVerdict(
                UsbDataPolicy.CHARGE_ONLY_LOCKED,
                disableUsbDataSignaling = true,
                blockExternalHidOnConsentDialog = true,
                reason = "CHARGE_ONLY_ENFORCED: USB data lanes disabled while locked, in-call, or inside ${STABILIZATION_WINDOW_MS}ms VBUS stabilization window",
            )
        }

        return GuardVerdict(
            UsbDataPolicy.ALLOW_DATA_PROMPT_TOUCH_ONLY,
            disableUsbDataSignaling = false,
            blockExternalHidOnConsentDialog = true, // Consent dialog ALWAYS requires direct on-screen touch, never HID!
            reason = "TOUCH_ONLY_CONSENT_REQUIRED: external HID/BLE keystrokes blocked on USB consent dialog",
        )
    }

    fun shouldAcceptConsentInputEvent(
        isPhysicalInternalTouchscreen: Boolean,
        inputFlagsObscured: Boolean,
        keyCodeOrNull: Int?,
    ): Boolean {
        if (!isPhysicalInternalTouchscreen) return false
        if (inputFlagsObscured) return false
        if (keyCodeOrNull != null) return false
        return true
    }
}
