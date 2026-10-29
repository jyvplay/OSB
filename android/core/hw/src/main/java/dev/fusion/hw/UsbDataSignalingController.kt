// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

import java.security.MessageDigest

/**
 * Clean-room Hardware USB Type-C Power Delivery & Data-Signaling Port Controller.
 *
 * Implements Evil-Maid / Cold-Boot / Public-Charger physical USB port state machine:
 * - `STRICT_NEVER_DATA`: Permanently disables USB data lanes (`D+`/`D-`/`SSRX`/`SSTX`), permitting PD power only.
 * - `DISABLE_WHILE_LOCKED` (default): Disables USB data signaling before lockscreen authentication;
 *   already-authenticated docks stay connected only if their `(VID, PID, serialSha256)` fingerprint
 *   is pinned AND no cable disconnect occurred while locked (`Tails udev-watchdog` parity!).
 */
class UsbDataSignalingController(
    val mode: PortPolicyMode = PortPolicyMode.DISABLE_WHILE_LOCKED,
    pinnedDockFingerprints: Set<String> = emptySet(),
) {
    enum class PortPolicyMode { STRICT_NEVER_DATA, DISABLE_WHILE_LOCKED, ALLOW_WHEN_UNLOCKED }

    enum class PortSignalState {
        DATA_LANES_ELECTRICALLY_DISABLED,
        DATA_LANES_ENABLED_TRUSTED_DOCK,
        DATA_LANES_ENABLED_USER_SESSION,
        EMERGENCY_PANIC_LOCK_TRIGGERED,
    }

    private val trustedPins = pinnedDockFingerprints.map { it.lowercase() }.toSet()

    fun fingerprintDevice(vendorId: Int, productId: Int, serialNumber: String): String {
        val raw = "%04x:%04x:%s".format(vendorId and 0xFFFF, productId and 0xFFFF, serialNumber.trim())
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun evaluatePortTransition(
        deviceLocked: Boolean,
        wasConnectedBeforeLock: Boolean,
        cableUnpluggedWhileLocked: Boolean,
        deviceFingerprintSha256: String?,
        amnesicWatchdogArmed: Boolean = false,
    ): PortSignalState {
        if (amnesicWatchdogArmed && cableUnpluggedWhileLocked) {
            return PortSignalState.EMERGENCY_PANIC_LOCK_TRIGGERED
        }
        if (mode == PortPolicyMode.STRICT_NEVER_DATA) {
            return PortSignalState.DATA_LANES_ELECTRICALLY_DISABLED
        }
        if (!deviceLocked) {
            return if (deviceFingerprintSha256 != null && deviceFingerprintSha256.lowercase() in trustedPins) {
                PortSignalState.DATA_LANES_ENABLED_TRUSTED_DOCK
            } else {
                PortSignalState.DATA_LANES_ENABLED_USER_SESSION
            }
        }
        // Device IS locked:
        if (wasConnectedBeforeLock &&
            !cableUnpluggedWhileLocked &&
            deviceFingerprintSha256 != null &&
            deviceFingerprintSha256.lowercase() in trustedPins
        ) {
            return PortSignalState.DATA_LANES_ENABLED_TRUSTED_DOCK
        }
        return PortSignalState.DATA_LANES_ELECTRICALLY_DISABLED
    }
}
