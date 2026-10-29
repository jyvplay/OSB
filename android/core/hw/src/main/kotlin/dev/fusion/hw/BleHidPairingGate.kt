// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Clean-room Bluetooth Classic & BLE HOGP (`0x1812`) Anti-`BlueDucky` (`CVE-2023-45866`)
 * & Wireless Keystroke-Injection Gate.
 *
 * Threat vectors closed:
 * 1. **`CVE-2023-45866` (`BlueDucky`) Unauthenticated HID Injection**: rejects any Bluetooth
 *    Classic L2CAP `0x0011` (HID Control) / `0x0013` (HID Interrupt) or BLE GATT `0x1812`
 *    (HID-over-GATT) connection using `NONE` or `JUST_WORKS` (`NoInputNoOutput`) pairing.
 * 2. **Weak Link-Key / Legacy Pairing Downgrade**: requires `LE Secure Connections` (or BR/EDR
 *    Secure Simple Pairing with MITM protection) and encryption key size `>= 16` octets (`128-bit`).
 * 3. **Post-Bonding GATT Service-Changed Elevation**: blocks a previously bonded non-HID
 *    peripheral (e.g., BLE headphones/heart-rate sensor paired via `JustWorks`) from dynamically
 *    adding GATT `0x1812` HID report characteristics later without re-bonding via MITM passkey!
 */
object BleHidPairingGate {
    const val SCOPE =
        "Bluetooth Classic L2CAP (0x0011/0x0013) & BLE HOGP (0x1812) anti-CVE-2023-45866 pairing, key-size, and GATT service-elevation policy gate; Android BluetoothAdapter binder calls out of scope."

    const val UUID_HOGP_16 = 0x1812
    const val PSM_HID_CONTROL = 0x0011
    const val PSM_HID_INTERRUPT = 0x0013
    const val MAJOR_CLASS_PERIPHERAL = 0x0500
    const val MIN_ENC_KEY_BYTES = 16

    enum class IoCapability {
        NO_INPUT_NO_OUTPUT,
        DISPLAY_ONLY,
        DISPLAY_YES_NO,
        KEYBOARD_ONLY,
        KEYBOARD_DISPLAY,
    }

    enum class AuthMethod {
        UNAUTHENTICATED_NONE,
        JUST_WORKS,
        NUMERIC_COMPARISON_MITM,
        PASSKEY_ENTRY_MITM,
        OUT_OF_BAND_MITM,
    }

    data class WirelessHidAttempt(
        val macAddress: String,
        val exposesHogp1812: Boolean,
        val classicMajorClass: Int = 0,
        val requestedL2capPsm: Int? = null,
        val ioCapability: IoCapability,
        val authMethod: AuthMethod,
        val secureConnections: Boolean,
        val encryptionKeyBytes: Int,
        val screenLocked: Boolean,
        val bondedOriginallyWithHid: Boolean? = null, // null = new pairing; false = bonded earlier as non-HID!
    ) {
        val isHidCapable: Boolean
            get() = exposesHogp1812 ||
                classicMajorClass == MAJOR_CLASS_PERIPHERAL ||
                requestedL2capPsm == PSM_HID_CONTROL ||
                requestedL2capPsm == PSM_HID_INTERRUPT
    }

    data class GateDecision(val allowed: Boolean, val code: String, val detail: String)

    fun evaluate(attempt: WirelessHidAttempt): GateDecision {
        if (attempt.screenLocked) {
            return GateDecision(false, "DENY_LOCKSCREEN_BT", "wireless peripheral connection/pairing refused while screen locked")
        }
        if (!attempt.isHidCapable) {
            // Non-HID peripherals (e.g., audio/battery) still require encryption >= 16 bytes if bonded
            if (attempt.encryptionKeyBytes < MIN_ENC_KEY_BYTES) {
                return GateDecision(false, "DENY_KNOB_WEAK_KEY", "Bluetooth encryption key ${attempt.encryptionKeyBytes}B < ${MIN_ENC_KEY_BYTES}B (KNOB defense)")
            }
            return GateDecision(true, "ALLOW_NON_HID", "non-HID wireless peripheral permitted")
        }

        // Post-bonding GATT Service-Changed HID privilege escalation check!
        if (attempt.bondedOriginallyWithHid == false) {
            return GateDecision(
                false,
                "DENY_GATT_HID_ELEVATION",
                "bonded non-HID device (${attempt.macAddress}) attempted post-bond GATT 0x1812 HID elevation without MITM re-pairing",
            )
        }

        // CVE-2023-45866 / BlueDucky defense: NoInputNoOutput & JustWorks categorically forbidden for HID
        if (attempt.ioCapability == IoCapability.NO_INPUT_NO_OUTPUT ||
            attempt.authMethod == AuthMethod.UNAUTHENTICATED_NONE ||
            attempt.authMethod == AuthMethod.JUST_WORKS
        ) {
            return GateDecision(
                false,
                "DENY_CVE_2023_45866_BLUEDUCKY",
                "unauthenticated/JustWorks HID pairing or L2CAP connection refused (${attempt.macAddress})",
            )
        }

        if (!attempt.secureConnections) {
            return GateDecision(
                false,
                "DENY_LEGACY_PAIRING",
                "legacy Bluetooth pairing without Secure Connections refused for HID (${attempt.macAddress})",
            )
        }

        if (attempt.encryptionKeyBytes < MIN_ENC_KEY_BYTES) {
            return GateDecision(
                false,
                "DENY_KNOB_WEAK_KEY",
                "HID link encryption key ${attempt.encryptionKeyBytes}B < ${MIN_ENC_KEY_BYTES}B",
            )
        }

        return GateDecision(
            true,
            "ALLOW_MITM_AUTHENTICATED_HID",
            "MITM-authenticated Secure Connections HID peripheral permitted (${attempt.authMethod})",
        )
    }
}
