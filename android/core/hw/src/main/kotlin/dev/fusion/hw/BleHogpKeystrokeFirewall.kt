// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Hardware Shield `#27` — **`BlueDucky` (`CVE-2023-45866`) & `BLESA` (`CVE-2020-9770`) Bluetooth /
 * BLE HID-over-GATT (`0x1812`) Keystroke Injection Firewall**.
 *
 * Blocks unauthenticated Just-Works HID pairing, weak `< 16-byte` LTK encryption keys, unencrypted
 * GATT reconnect notifications (`BLESA`), lock-screen HID registration, and superhuman `> 28 keys / 100 ms`
 * Rubber-Ducky / `BlueDucky` macro keystroke injection bursts!
 */
object BleHogpKeystrokeFirewall {
    const val SCOPE =
        "Hardware Shield #27: CVE-2023-45866 BlueDucky & CVE-2020-9770 BLESA BLE HID-over-GATT (0x1812) firewall and burst-rate cutoff."

    const val UUID_HOGP_SERVICE = 0x1812

    data class BleHidSessionContext(
        val gattServiceUuid16: Int,
        val screenUnlocked: Boolean,
        val bondedWithMitmNumericComparison: Boolean,
        val leSecureConnectionsActive: Boolean,
        val encryptionKeySizeBytes: Int,
        val reconnectLinkEncrypted: Boolean,
        val keystrokesInLast100Ms: Int,
    )

    data class FirewallDecision(val allowed: Boolean, val quarantineDevice: Boolean, val reason: String)

    fun evaluateHidEvent(ctx: BleHidSessionContext): FirewallDecision {
        if (ctx.gattServiceUuid16 != UUID_HOGP_SERVICE) {
            return FirewallDecision(true, false, "Non-HID GATT profile")
        }
        if (!ctx.screenUnlocked) {
            return FirewallDecision(false, true, "Blocked BLE HID profile while screen is locked (CVE-2023-45866)")
        }
        if (!ctx.reconnectLinkEncrypted) {
            return FirewallDecision(false, true, "Blocked unencrypted GATT reconnect notification (BLESA CVE-2020-9770)")
        }
        if (!ctx.leSecureConnectionsActive || !ctx.bondedWithMitmNumericComparison) {
            return FirewallDecision(false, true, "Rejected Just-Works unauthenticated BLE HID pairing (CVE-2023-45866)")
        }
        if (ctx.encryptionKeySizeBytes < 16) {
            return FirewallDecision(false, true, "Rejected downgraded BLE LTK key size ${ctx.encryptionKeySizeBytes}B (< 16B KNOB/BIAS)")
        }
        if (ctx.keystrokesInLast100Ms > 28) {
            return FirewallDecision(false, true, "Quarantined superhuman HID macro injection burst (${ctx.keystrokesInLast100Ms} keys/100ms)")
        }
        return FirewallDecision(true, false, "Authenticated LE-SC MITM 128-bit BLE HID session permitted")
    }
}
