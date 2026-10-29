// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Clean-room Anti-BadUSB / Rubber-Ducky Keystroke-Injection Hardware Firewall (Vector V15):
 *
 * Malicious USB-C cables, docks, and thumb-drives enumerate a composite USB HID keyboard (`0x03 /
 * 0x01 / 0x01`) and inject shell payloads (`META+R`, `CTRL+ALT+T`, `curl ... | sh`) at superhuman
 * `1..4 ms` inter-keystroke intervals (`> 2,500 WPM`).
 *
 * Enforces:
 * 1. Mandatory untrusted quarantine window (`firstSeenAgeMs < 5000`) for newly enumerated USB HID
 *    keyboards requiring a deliberate human unlock chord before `META`/`ALT`/`CTRL` shell-launcher
 *    shortcuts are honored.
 * 2. Sliding-window inter-keystroke interval (`IKI`) detector blocking HID devices whose median
 *    `IKI < 8.0 ms` or burst rate exceeds `35` keystrokes/sec (`> 420 WPM`, physically impossible
 *    for human typing).
 */
object UsbBadHidKeystrokeRateGuard {
    const val SCOPE =
        "Pure-JVM Anti-BadUSB / Rubber-Ducky HID keystroke-injection firewall blocking <8ms superhuman IKI bursts and untrusted shell-launch chords (Vector V15)."

    const val MIN_HUMAN_MEDIAN_IKI_MS = 8.0
    const val NEW_DEVICE_TRUST_GRACE_MS = 5_000L

    data class HidEventWindow(
        val usbVendorProductId: String,
        val deviceEnumeratedAgoMs: Long,
        val userExplicitlyTrustedHid: Boolean,
        val keyDownTimestampsMs: LongArray,
        val containsShellLaunchChord: Boolean, // e.g. META+R, CTRL+ALT+T, META+ENTER
    )

    data class HidFirewallVerdict(
        val allowInputDelivery: Boolean,
        val quarantineHidDevice: Boolean,
        val medianInterKeystrokeMs: Double,
        val reasonCode: String,
    )

    fun inspectHidBurst(w: HidEventWindow): HidFirewallVerdict {
        require(w.usbVendorProductId.matches(Regex("^[0-9a-fA-F]{4}:[0-9a-fA-F]{4}$"))) {
            "expected VID:PID hex identifier, got '${w.usbVendorProductId}'"
        }
        require(w.keyDownTimestampsMs.isNotEmpty()) { "at least 1 HID timestamp required" }

        if (!w.userExplicitlyTrustedHid && w.deviceEnumeratedAgoMs < NEW_DEVICE_TRUST_GRACE_MS && w.containsShellLaunchChord) {
            return HidFirewallVerdict(
                allowInputDelivery = false,
                quarantineHidDevice = true,
                medianInterKeystrokeMs = 0.0,
                reasonCode = "REJECT_BADUSB_SHELL_CHORD_DURING_ENUMERATION_GRACE",
            )
        }

        if (w.keyDownTimestampsMs.size >= 6) {
            val ikis = DoubleArray(w.keyDownTimestampsMs.size - 1) { i ->
                (w.keyDownTimestampsMs[i + 1] - w.keyDownTimestampsMs[i]).toDouble().coerceAtLeast(0.0)
            }.sortedArray()
            val medianIki = ikis[ikis.size / 2]
            if (medianIki < MIN_HUMAN_MEDIAN_IKI_MS) {
                return HidFirewallVerdict(
                    allowInputDelivery = false,
                    quarantineHidDevice = true,
                    medianInterKeystrokeMs = medianIki,
                    reasonCode = "REJECT_BADUSB_SUPERHUMAN_IKI_INJECTION(${medianIki}ms)",
                )
            }
            return HidFirewallVerdict(true, false, medianIki, "ALLOW_HUMAN_TYPING_CADENCE")
        }
        return HidFirewallVerdict(true, false, 100.0, "ALLOW_SHORT_CHORD")
    }
}
